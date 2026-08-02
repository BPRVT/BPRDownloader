package com.bprvt.bprdownloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bprvt.bprdownloader.MainActivity
import com.bprvt.bprdownloader.R
import com.bprvt.bprdownloader.data.HistoryEntry
import com.bprvt.bprdownloader.data.HistoryRepo
import com.bprvt.bprdownloader.databinding.FragmentHomeBinding
import com.bprvt.bprdownloader.util.Urls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = HistoryAdapter(
            onClick = { entry -> reuse(entry) },
            onLongClick = { entry -> showEntryMenu(entry) }
        )
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.adapter = adapter

        binding.downloadButton.setOnClickListener { submit(download = true) }
        binding.browseButton.setOnClickListener { submit(download = false) }

        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit(download = true)
                true
            } else {
                false
            }
        }

        // The single input doubles as history search: typing filters the list
        // live, so a past download is two keystrokes away instead of a retype.
        binding.urlInput.addTextChangedListener(SimpleTextWatcher { refresh() })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                HistoryRepo.revision.collect { refresh() }
            }
        }

        binding.urlInput.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val binding = _binding ?: return
        val term = binding.urlInput.text.toString()
        viewLifecycleOwner.lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                if (term.isBlank()) HistoryRepo.all() else HistoryRepo.search(term)
            }
            val live = _binding ?: return@launch
            adapter.submitList(entries)
            live.historyCount.text = if (entries.isEmpty()) "" else "${entries.size}"
            live.emptyLabel.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            live.emptyLabel.setText(
                if (term.isBlank()) R.string.history_empty else R.string.history_no_match
            )
        }
    }

    private fun submit(download: Boolean) {
        val raw = binding.urlInput.text.toString()
        val url = Urls.normalize(raw)
        if (url == null) {
            Toast.makeText(requireContext(), R.string.url_hint, Toast.LENGTH_SHORT).show()
            return
        }
        val activity = activity as? MainActivity ?: return
        if (download) activity.startDownload(url) else activity.openInBrowser(url)
    }

    /** Tapping a history row does the same thing it did the first time. */
    private fun reuse(entry: HistoryEntry) {
        val activity = activity as? MainActivity ?: return
        if (entry.kind == HistoryEntry.KIND_PAGE && !Urls.looksLikeFile(entry.url)) {
            activity.openInBrowser(entry.url)
        } else {
            activity.startDownload(entry.url, entry.fileName)
        }
    }

    private fun showEntryMenu(entry: HistoryEntry) {
        val favoriteLabel = getString(if (entry.favorite) R.string.unfavorite else R.string.favorite)
        val options = arrayOf(
            getString(R.string.download),
            getString(R.string.browse),
            favoriteLabel,
            getString(R.string.remove_from_history)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(entry.label)
            .setItems(options) { _, which ->
                val activity = activity as? MainActivity ?: return@setItems
                when (which) {
                    0 -> activity.startDownload(entry.url, entry.fileName)
                    1 -> activity.openInBrowser(entry.url)
                    2 -> ioLaunch { HistoryRepo.setFavorite(entry.id, !entry.favorite) }
                    3 -> ioLaunch { HistoryRepo.delete(entry.id) }
                }
            }
            .show()
    }

    private fun ioLaunch(block: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { block() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.historyList.adapter = null
        _binding = null
    }
}
