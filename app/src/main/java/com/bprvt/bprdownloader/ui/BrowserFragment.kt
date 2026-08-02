package com.bprvt.bprdownloader.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bprvt.bprdownloader.MainActivity
import com.bprvt.bprdownloader.R
import com.bprvt.bprdownloader.data.Prefs
import com.bprvt.bprdownloader.databinding.FragmentBrowserBinding
import com.bprvt.bprdownloader.util.Urls

class BrowserFragment : Fragment(), BackHandler {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!

    private var cursorMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = true
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                // Catch direct file links before the WebView tries to render them.
                if (Urls.looksLikeFile(url)) {
                    (activity as? MainActivity)?.startDownload(url)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                binding.urlBar.setText(url)
                binding.pageProgress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                binding.urlBar.setText(url)
                binding.pageProgress.visibility = View.GONE
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.pageProgress.progress = newProgress
            }
        }

        binding.webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val suggested = runCatching {
                URLUtil.guessFileName(url, contentDisposition, mimeType)
            }.getOrNull()
            (activity as? MainActivity)?.startDownload(url, suggested)
        }

        binding.backButton.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
        }
        binding.forwardButton.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }
        binding.reloadButton.setOnClickListener { binding.webView.reload() }
        binding.cursorButton.setOnClickListener { setCursorMode(!cursorMode) }
        binding.downloadPageButton.setOnClickListener {
            val url = binding.webView.url ?: return@setOnClickListener
            (activity as? MainActivity)?.startDownload(url)
        }

        binding.urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val url = Urls.normalizeOrSearch(binding.urlBar.text.toString())
                if (url != null) load(url) else {
                    Toast.makeText(requireContext(), R.string.url_hint, Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }

        binding.cursorOverlay.onTap = { x, y -> tapWebView(x, y) }
        binding.cursorOverlay.onScroll = { dy -> binding.webView.scrollBy(0, dy) }
        binding.cursorOverlay.onExit = { setCursorMode(false) }

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        }
        if (!consumePendingUrl() && binding.webView.url == null) {
            load(Prefs.homepage)
        }
    }

    /** Picks up a URL handed over by MainActivity. Returns true if one was loaded. */
    fun consumePendingUrl(): Boolean {
        val activity = activity as? MainActivity ?: return false
        val pending = activity.pendingBrowserUrl ?: return false
        activity.pendingBrowserUrl = null
        if (_binding == null) return false
        load(pending)
        return true
    }

    override fun onResume() {
        super.onResume()
        _binding?.webView?.onResume()
        consumePendingUrl()
    }

    fun load(url: String) {
        _binding?.webView?.loadUrl(url)
        _binding?.urlBar?.setText(url)
    }

    private fun setCursorMode(enabled: Boolean) {
        cursorMode = enabled
        val overlay = binding.cursorOverlay
        overlay.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.cursorButton.isSelected = enabled
        if (enabled) {
            overlay.centerCursor()
            overlay.requestFocus()
            Toast.makeText(requireContext(), R.string.cursor_on, Toast.LENGTH_SHORT).show()
        } else {
            binding.webView.requestFocus()
        }
    }

    /** Synthesises a tap so the page reacts as it would to a real touch. */
    private fun tapWebView(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, x, y, 0)
        binding.webView.dispatchTouchEvent(down)
        binding.webView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    override fun onBackPressedInFragment(): Boolean {
        if (cursorMode) {
            setCursorMode(false)
            return true
        }
        val webView = _binding?.webView ?: return false
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.webView?.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        _binding?.webView?.onPause()
    }

    override fun onDestroyView() {
        _binding?.webView?.destroy()
        _binding = null
        super.onDestroyView()
    }
}
