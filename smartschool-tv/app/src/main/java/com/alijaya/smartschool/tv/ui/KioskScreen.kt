package com.alijaya.smartschool.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.alijaya.smartschool.tv.util.BeepHelper

class AndroidBridge(
    private val beep: BeepHelper,
    private val onOpenAdmin: (() -> Unit)?
) {
    @JavascriptInterface
    fun openAdmin() {
        Handler(Looper.getMainLooper()).post {
            onOpenAdmin?.invoke()
        }
    }

    @JavascriptInterface
    fun speak(text: String) {
        Handler(Looper.getMainLooper()).post {
            beep.speak(text)
        }
    }

    @JavascriptInterface
    fun beepSuccess() {
        // Nada beep sukses ditangani oleh Web Audio API di display.html
    }

    @JavascriptInterface
    fun beepError() {
        // Nada beep error ditangani oleh Web Audio API di display.html
    }
}

class KioskWebView(context: Context) : WebView(context) {
    private val buffer = StringBuilder()

    init {
        isSoundEffectsEnabled = false
    }

    // Mencegah Android memunculkan keyboard virtual (soft keyboard / keytone) di layar TV
    override fun onCheckIsTextEditor(): Boolean = false

    override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo?): android.view.inputmethod.InputConnection? {
        return null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val unicode = event.unicodeChar
            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                val code = buffer.toString().trim()
                buffer.setLength(0)
                if (code.isNotEmpty()) {
                    evaluateJavascript(
                        "if (typeof processAttendance === 'function') processAttendance('$code');",
                        null
                    )
                    return true
                }
            } else if (unicode in 32..126) {
                buffer.append(unicode.toChar())
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        hideKeyboard()
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KioskScreen(onOpenAdmin: (() -> Unit)? = null) {
    val context = LocalContext.current
    val beep = remember { BeepHelper(context) }

    DisposableEffect(Unit) {
        onDispose { beep.release() }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF020617))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                KioskWebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#020617"))
                    isSoundEffectsEnabled = false
                    keepScreenOn = true

                    setOnTouchListener { _, _ ->
                        hideKeyboard()
                        false
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }

                    addJavascriptInterface(AndroidBridge(beep, onOpenAdmin), "AndroidBridge")

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    view?.loadUrl("http://127.0.0.1:8080/app/display.html")
                                }, 1500)
                            }
                        }
                    }

                    loadUrl("http://127.0.0.1:8080/app/display.html")
                }
            },
            update = {
                // Keep webview running
            }
        )
    }
}
