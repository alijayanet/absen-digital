package com.alijaya.smartschool.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView

/**
 * Custom WebView untuk Android TV & Kiosk:
 * Menolak koneksi InputMethod soft keyboard agar On-Screen Keyboard tidak pernah muncul di layar TV
 * saat RFID reader atau barcode scanner USB membaca kartu.
 */
class KioskWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        isSoundEffectsEnabled = false
    }

    // Mencegah Android mendeteksi WebView sebagai editor teks biasa
    override fun onCheckIsTextEditor(): Boolean = false

    // KUNCI UTAMA: Mengembalikan null melarang Android OS memunculkan Soft Keyboard virtual sama sekali!
    override fun onCreateInputConnection(outAttrs: EditorInfo?): InputConnection? = null

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        hideKeyboard()
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }
}
