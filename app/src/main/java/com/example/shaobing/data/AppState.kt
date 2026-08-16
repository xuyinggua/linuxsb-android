package com.example.shaobing.data

import android.webkit.WebView

object AppState {
    const val HOME_URL = "https://linux.sb"

    var webView: WebView? = null
    var userAgent: String = ""
    var currentUrl: String = HOME_URL
    var currentTitle: String = ""

    var onUrlChanged: ((String) -> Unit)? = null
    var onTitleChanged: ((String) -> Unit)? = null
    var onProgressChanged: ((Int) -> Unit)? = null
    var onPageFinished: ((WebView) -> Unit)? = null
    var onAccountChanged: (() -> Unit)? = null
    var onScriptsChanged: (() -> Unit)? = null

    fun open(url: String) {
        val wv = webView ?: return
        wv.post { wv.loadUrl(url) }
    }

    fun openCurrent() {
        open(currentUrl)
    }

    fun reload() {
        val wv = webView ?: return
        wv.post { wv.reload() }
    }

    fun goBack() {
        val wv = webView ?: return
        wv.post {
            if (wv.canGoBack()) wv.goBack()
        }
    }

    fun goForward() {
        val wv = webView ?: return
        wv.post {
            if (wv.canGoForward()) wv.goForward()
        }
    }

    fun evaluate(js: String) {
        val wv = webView ?: return
        wv.post { wv.evaluateJavascript(js, null) }
    }
}
