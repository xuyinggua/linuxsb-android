package com.example.shaobing.ui

import android.content.Context
import android.webkit.WebView
import com.example.shaobing.ShaoBingApp

object Prefs {
    private val sp by lazy {
        ShaoBingApp.appContext.getSharedPreferences("shaobing_prefs", Context.MODE_PRIVATE)
    }

    const val DEFAULT_FONT_ZOOM = 100

    var fontZoom: Int
        get() = sp.getInt("font_zoom", DEFAULT_FONT_ZOOM)
        set(value) = sp.edit().putInt("font_zoom", value).apply()

    var scriptsEnabled: Boolean
        get() = sp.getBoolean("scripts_enabled", false)
        set(value) = sp.edit().putBoolean("scripts_enabled", value).apply()

    var builtinsInitialized: Boolean
        get() = sp.getBoolean("builtins_initialized", false)
        set(value) = sp.edit().putBoolean("builtins_initialized", value).apply()

    fun applyFontZoom(webView: WebView) {
        runCatching { webView.settings.textZoom = fontZoom }
    }
}
