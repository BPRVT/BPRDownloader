package com.bprvt.bprdownloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bprvt.bprdownloader.MainActivity
import com.bprvt.bprdownloader.R
import com.bprvt.bprdownloader.databinding.FragmentFilesBinding
import com.bprvt.bprdownloader.download.Downloads
import com.bprvt.bprdownloader.download.Installer
import com.bprvt.bprdownloader.util.Urls
import kotlinx.coroutines.launch
import java.io.File

class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FilesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = FilesAdapter { file -> showFileMenu(file) }
        binding.fileList.layoutManager = LinearLayoutManager(requireContext())
        binding.fileList.adapter = adapter

        // Refresh when a download lands so a file appears without leaving the tab.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Downloads.finished.collect { refresh() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val binding = _binding ?: return
        val files = Downloads.downloadDir(requireContext())
            .listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        adapter.submitList(files)
        binding.emptyLabel.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showFileMenu(file: File) {
        val isApk = Urls.isApk(file.name)
        val primary = getString(if (isApk) R.string.install else R.string.open)
        val options = arrayOf(primary, getString(R.string.delete))
        AlertDialog.Builder(requireContext())
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> if (isApk) {
                        (activity as? MainActivity)?.requestInstall(file)
                    } else {
                        runCatching { Installer.open(requireContext(), file) }
                    }

                    1 -> confirmDelete(file)
                }
            }
            .show()
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(file.name)
            .setPositiveButton(R.string.delete) { _, _ ->
                file.delete()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.fileList.adapter = null
        _binding = null
    }
}
