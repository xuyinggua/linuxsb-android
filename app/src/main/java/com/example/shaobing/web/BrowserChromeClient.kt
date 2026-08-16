package com.example.shaobing.web

import android.app.Activity
import android.content.ClipData
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.example.shaobing.data.AppState

class BrowserChromeClient(
    private val activity: Activity,
    private val onFileChooser: (ValueCallback<Array<Uri>>, String) -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        AppState.onProgressChanged?.invoke(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        if (title.isNullOrBlank()) return
        AppState.currentTitle = title
        AppState.onTitleChanged?.invoke(title)
    }

    override fun onJsAlert(
        view: WebView,
        url: String,
        message: String,
        result: android.webkit.JsResult
    ): Boolean {
        android.app.AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }

    override fun onJsConfirm(
        view: WebView,
        url: String,
        message: String,
        result: android.webkit.JsResult
    ): Boolean {
        android.app.AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }

    override fun onJsPrompt(
        view: WebView,
        url: String,
        message: String,
        defaultValue: String,
        result: android.webkit.JsPromptResult
    ): Boolean {
        val input = android.widget.EditText(activity).apply { setText(defaultValue) }
        android.app.AlertDialog.Builder(activity)
            .setTitle(message)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            .setOnCancelListener { result.cancel() }
            .show()
        return true
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        onFileChooser(filePathCallback, fileChooserParams.acceptTypes?.firstOrNull() ?: "*/*")
        return true
    }
}
