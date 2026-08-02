package com.bprvt.bprdownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bprvt.bprdownloader.data.HistoryRepo
import com.bprvt.bprdownloader.databinding.ActivityMainBinding
import com.bprvt.bprdownloader.download.Downloads
import com.bprvt.bprdownloader.download.Installer
import com.bprvt.bprdownloader.send.SendServer
import com.bprvt.bprdownloader.ui.FilesAdapter
import com.bprvt.bprdownloader.util.Format
import com.bprvt.bprdownloader.util.Urls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/**
 * The whole app: show where the phone should connect, take whatever it sends,
 * and let the remote install the results.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var filesAdapter: FilesAdapter

    private var server: SendServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionLabel.text = "v${BuildConfig.VERSION_NAME}"

        filesAdapter = FilesAdapter { file -> showFileMenu(file) }
        binding.fileList.layoutManager = LinearLayoutManager(this)
        binding.fileList.adapter = filesAdapter

        observeDownloads()
        requestNotificationPermissionIfNeeded()
    }

    // The server's lifetime is the app's: opening the app is what turns the
    // sender on, and leaving it closes the port.
    override fun onStart() {
        super.onStart()
        startServer()
        refreshFiles()
    }

    override fun onStop() {
        super.onStop()
        server?.stop()
        server = null
    }

    private fun startServer() {
        val instance = SendServer(
            onSubmit = { url -> handleSubmit(url) },
            historyProvider = { HistoryRepo.all() }
        )
        server = instance
        if (!instance.start()) {
            binding.addressLabel.text = getString(R.string.send_failed)
            binding.pinLabel.text = ""
            return
        }
        binding.addressLabel.text = instance.address() ?: getString(R.string.send_no_network)
        binding.pinLabel.text = instance.pin
    }

    /** Called on a server thread. */
    private fun handleSubmit(rawUrl: String) {
        val url = Urls.normalize(rawUrl) ?: return
        runOnUiThread {
            lifecycleScope.launch(Dispatchers.IO) {
                HistoryRepo.record(url)
                HistoryRepo.trimTo(HISTORY_LIMIT)
            }
            Downloads.enqueue(this, url)
        }
    }

    // --- downloads ---------------------------------------------------------

    private fun observeDownloads() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    Downloads.active.collectLatest { task ->
                        if (task == null || task.state != Downloads.State.RUNNING) {
                            binding.statusProgress.visibility = View.GONE
                            binding.statusText.text = ""
                            return@collectLatest
                        }
                        binding.statusProgress.visibility = View.VISIBLE
                        val done = Format.bytes(task.bytesDone)
                        val total = if (task.bytesTotal > 0) Format.bytes(task.bytesTotal) else "?"
                        binding.statusText.text = "${task.fileName}  ·  $done / $total"
                        if (task.percent >= 0) {
                            binding.statusProgress.isIndeterminate = false
                            binding.statusProgress.progress = task.percent
                        } else {
                            binding.statusProgress.isIndeterminate = true
                        }
                    }
                }
                launch {
                    Downloads.finished.collectLatest { task -> onDownloadFinished(task) }
                }
            }
        }
    }

    private fun onDownloadFinished(task: Downloads.Task) {
        binding.statusProgress.visibility = View.GONE
        binding.statusText.text = ""
        refreshFiles()

        if (task.state == Downloads.State.FAILED) {
            AlertDialog.Builder(this)
                .setTitle(R.string.download_failed)
                .setMessage("${task.fileName}\n\n${task.error}")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val file = task.file ?: return
        if (!Urls.isApk(file.name)) return

        // An APK is almost always meant to be installed, so offer it straight away.
        AlertDialog.Builder(this)
            .setTitle(R.string.download_complete)
            .setMessage("${file.name}\n${Format.bytes(task.bytesDone)}")
            .setPositiveButton(R.string.install) { _, _ -> requestInstall(file) }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    // --- files -------------------------------------------------------------

    private fun refreshFiles() {
        val files = Downloads.downloadDir(this)
            .listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        filesAdapter.submitList(files)
        binding.emptyLabel.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showFileMenu(file: File) {
        val isApk = Urls.isApk(file.name)
        val options = arrayOf(
            getString(if (isApk) R.string.install else R.string.open),
            getString(R.string.delete)
        )
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> if (isApk) requestInstall(file) else runCatching { Installer.open(this, file) }
                    1 -> confirmDelete(file)
                }
            }
            .show()
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(file.name)
            .setPositiveButton(R.string.delete) { _, _ ->
                file.delete()
                refreshFiles()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestInstall(file: File) {
        if (!Installer.canInstall(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.install_blocked_title)
                .setMessage(R.string.install_blocked_message)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    runCatching { startActivity(Installer.permissionIntent(this)) }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        runCatching { Installer.install(this, file) }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
    }

    companion object {
        private const val REQ_NOTIFICATIONS = 42
        private const val HISTORY_LIMIT = 200
    }
}
