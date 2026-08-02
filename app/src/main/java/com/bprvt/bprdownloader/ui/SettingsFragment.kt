package com.bprvt.bprdownloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bprvt.bprdownloader.R
import com.bprvt.bprdownloader.data.HistoryRepo
import com.bprvt.bprdownloader.data.Prefs
import com.bprvt.bprdownloader.databinding.FragmentSettingsBinding
import com.bprvt.bprdownloader.download.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.homepageInput.setText(Prefs.homepage)
        binding.historyLimitInput.setText(Prefs.historyLimit.toString())
        binding.deleteAfterInstall.isChecked = Prefs.deleteAfterInstall
        binding.confirmBeforeDownload.isChecked = Prefs.confirmBeforeDownload
        // The SeekBar range is 0..16, mapped onto a 4..20 pixel step.
        binding.cursorSpeed.progress = Prefs.cursorSpeed - MIN_CURSOR_SPEED

        binding.saveButton.setOnClickListener { save() }

        binding.clearHistoryButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.setting_clear_history)
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { HistoryRepo.clear() }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.clearFilesButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.setting_clear_files)
                .setPositiveButton(R.string.delete) { _, _ ->
                    Downloads.downloadDir(requireContext()).listFiles()?.forEach { it.delete() }
                    Toast.makeText(requireContext(), R.string.setting_clear_files, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun save() {
        Prefs.homepage = binding.homepageInput.text.toString().trim()
            .ifEmpty { Prefs.DEFAULT_HOMEPAGE }
        Prefs.historyLimit = binding.historyLimitInput.text.toString().toIntOrNull()?.coerceIn(10, 5000) ?: 200
        Prefs.deleteAfterInstall = binding.deleteAfterInstall.isChecked
        Prefs.confirmBeforeDownload = binding.confirmBeforeDownload.isChecked
        Prefs.cursorSpeed = binding.cursorSpeed.progress + MIN_CURSOR_SPEED

        val limit = Prefs.historyLimit
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { HistoryRepo.trimTo(limit) }
        Toast.makeText(requireContext(), R.string.save, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MIN_CURSOR_SPEED = 4
    }
}
