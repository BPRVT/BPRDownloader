package com.bprvt.bprdownloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bprvt.bprdownloader.data.HistoryEntry
import com.bprvt.bprdownloader.data.HistoryRepo
import com.bprvt.bprdownloader.data.Prefs
import com.bprvt.bprdownloader.databinding.ActivityMainBinding
import com.bprvt.bprdownloader.download.Downloads
import com.bprvt.bprdownloader.download.Installer
import com.bprvt.bprdownloader.ui.BackHandler
import com.bprvt.bprdownloader.ui.BrowserFragment
import com.bprvt.bprdownloader.ui.FilesFragment
import com.bprvt.bprdownloader.ui.HomeFragment
import com.bprvt.bprdownloader.ui.SettingsFragment
import com.bprvt.bprdownloader.util.Format
import com.bprvt.bprdownloader.util.Urls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentTag: String = TAG_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionLabel.text = "v${BuildConfig.VERSION_NAME}"

        wireNav(binding.navHome, TAG_HOME)
        wireNav(binding.navBrowser, TAG_BROWSER)
        wireNav(binding.navFiles, TAG_FILES)
        wireNav(binding.navSettings, TAG_SETTINGS)

        if (savedInstanceState == null) {
            show(TAG_HOME)
        } else {
            currentTag = savedInstanceState.getString(STATE_TAG) ?: TAG_HOME
            show(currentTag)
        }

        observeDownloads()
        requestNotificationPermissionIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAG, currentTag)
    }

    private fun wireNav(view: TextView, tag: String) {
        view.setOnClickListener { show(tag) }
    }

    // --- navigation --------------------------------------------------------

    fun show(tag: String) {
        currentTag = tag
        val manager = supportFragmentManager
        val transaction = manager.beginTransaction()

        // Fragments are added once and then hidden/shown, so the browser keeps
        // its loaded page and scroll position when you pop over to Home.
        for (existing in manager.fragments) {
            if (existing.tag != tag) transaction.hide(existing)
        }
        val target = manager.findFragmentByTag(tag)
        if (target == null) {
            transaction.add(R.id.container, newFragment(tag), tag)
        } else {
            transaction.show(target)
        }
        transaction.commit()

        binding.navHome.isSelected = tag == TAG_HOME
        binding.navBrowser.isSelected = tag == TAG_BROWSER
        binding.navFiles.isSelected = tag == TAG_FILES
        binding.navSettings.isSelected = tag == TAG_SETTINGS
    }

    private fun newFragment(tag: String): Fragment = when (tag) {
        TAG_BROWSER -> BrowserFragment()
        TAG_FILES -> FilesFragment()
        TAG_SETTINGS -> SettingsFragment()
        else -> HomeFragment()
    }

    private fun currentFragment(): Fragment? = supportFragmentManager.findFragmentByTag(currentTag)

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val fragment = currentFragment()
        if (fragment is BackHandler && fragment.onBackPressedInFragment()) return
        if (currentTag != TAG_HOME) {
            show(TAG_HOME)
            return
        }
        super.onBackPressed()
    }

    // --- actions shared by fragments ---------------------------------------

    /**
     * Set by [openInBrowser] and consumed by [BrowserFragment] once its view
     * exists — the fragment may not be created yet when the call comes in.
     */
    var pendingBrowserUrl: String? = null

    fun openInBrowser(url: String) {
        pendingBrowserUrl = url
        show(TAG_BROWSER)
        supportFragmentManager.executePendingTransactions()
        (supportFragmentManager.findFragmentByTag(TAG_BROWSER) as? BrowserFragment)?.consumePendingUrl()
        lifecycleScope.launch(Dispatchers.IO) {
            HistoryRepo.record(url, Urls.shortLabel(url), HistoryEntry.KIND_PAGE)
        }
    }

    /** Entry point for every download in the app. */
    fun startDownload(url: String, fileNameHint: String? = null, askFirst: Boolean = Prefs.confirmBeforeDownload) {
        val name = fileNameHint ?: Urls.fileNameFrom(url)
        if (!askFirst) {
            enqueue(url, fileNameHint)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.download))
            .setMessage("$name\n\n${Urls.host(url)}")
            .setPositiveButton(R.string.download) { _, _ -> enqueue(url, fileNameHint) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun enqueue(url: String, fileNameHint: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            HistoryRepo.record(url, fileNameHint ?: Urls.shortLabel(url))
            HistoryRepo.trimTo(Prefs.historyLimit)
        }
        Downloads.enqueue(this, url, fileNameHint)
    }

    // --- download status ---------------------------------------------------

    private fun observeDownloads() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    Downloads.active.collectLatest { task ->
                        if (task == null || task.state != Downloads.State.RUNNING) {
                            binding.statusBar.visibility = View.GONE
                            return@collectLatest
                        }
                        binding.statusBar.visibility = View.VISIBLE
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
        binding.statusBar.visibility = View.GONE
        if (task.state == Downloads.State.FAILED) {
            AlertDialog.Builder(this)
                .setTitle(R.string.download_failed)
                .setMessage("${task.fileName}\n\n${task.error}")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val file = task.file ?: return
        if (!Urls.isApk(file.name)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.download_complete)
                .setMessage("${file.name}\n${Format.bytes(task.bytesDone)}")
                .setPositiveButton(R.string.open) { _, _ -> runCatching { Installer.open(this, file) } }
                .setNegativeButton(android.R.string.ok, null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.download_complete)
            .setMessage("${file.name}\n${Format.bytes(task.bytesDone)}")
            .setPositiveButton(R.string.install) { _, _ -> requestInstall(file) }
            .setNegativeButton(R.string.delete) { _, _ -> file.delete() }
            .setNeutralButton(android.R.string.ok, null)
            .show()
    }

    fun requestInstall(file: java.io.File) {
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
        if (Prefs.deleteAfterInstall) {
            // The installer reads the APK asynchronously, so give it a moment
            // before the file disappears from under it.
            binding.root.postDelayed({ file.delete() }, 60_000L)
        }
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
        const val TAG_HOME = "home"
        const val TAG_BROWSER = "browser"
        const val TAG_FILES = "files"
        const val TAG_SETTINGS = "settings"

        private const val STATE_TAG = "current_tag"
        private const val REQ_NOTIFICATIONS = 42
    }
}
