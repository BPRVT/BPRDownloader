package com.bprvt.bprdownloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bprvt.bprdownloader.MainActivity
import com.bprvt.bprdownloader.R
import com.bprvt.bprdownloader.data.HistoryRepo
import com.bprvt.bprdownloader.databinding.FragmentSendBinding
import com.bprvt.bprdownloader.send.SendServer
import com.bprvt.bprdownloader.util.Urls

/**
 * Shows the address and PIN for the phone sender. The server lives exactly as
 * long as this screen is visible.
 */
class SendFragment : Fragment() {

    private var _binding: FragmentSendBinding? = null
    private val binding get() = _binding!!

    private var server: SendServer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        val instance = SendServer(
            onSubmit = { url, browse -> handleSubmit(url, browse) },
            historyProvider = { HistoryRepo.all() }
        )
        server = instance
        if (instance.start()) {
            val address = instance.address()
            binding.addressLabel.text = address ?: getString(R.string.send_no_network)
            binding.pinLabel.text = instance.pin
            binding.statusLabel.text = ""
        } else {
            binding.addressLabel.text = getString(R.string.send_failed)
            binding.pinLabel.text = ""
        }
    }

    override fun onPause() {
        super.onPause()
        server?.stop()
        server = null
    }

    /** Called on a server thread — bounce everything to the UI thread. */
    private fun handleSubmit(rawUrl: String, browse: Boolean) {
        val view = _binding?.root ?: return
        view.post {
            val activity = activity as? MainActivity ?: return@post
            val url = Urls.normalize(rawUrl)
            when {
                url == null -> activity.openInBrowser(Urls.searchUrl(rawUrl))
                browse -> activity.openInBrowser(url)
                // Sent from a phone, so don't make them walk over to confirm.
                else -> activity.startDownload(url, askFirst = false)
            }
            _binding?.statusLabel?.text = getString(R.string.send_received, Urls.shortLabel(rawUrl))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        server?.stop()
        server = null
        _binding = null
    }
}
