package com.example.shaobing.ui

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import com.example.shaobing.profile.ProfileManager

@Composable
fun LoginScreen(onClose: () -> Unit, initialUrl: String = "") {
    val context = LocalContext.current
    val activity = context as? Activity
    val loginProfile = remember { ProfileManager.getLoginProfile() }

    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(100) }

    val nav = remember {
        object {
            var previousUrl = ""
            var pending = false
            var done = false
        }
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            if (loginProfile != null) {
                runCatching { WebViewCompat.setProfile(this, loginProfile.name) }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val u = url ?: return
                    val uri = runCatching { Uri.parse(u) }.getOrNull()
                    val prevWasLogin = nav.previousUrl.contains("/login")
                    val isHome = uri?.host?.endsWith("linux.sb") == true &&
                        (uri.path.isNullOrEmpty() || uri.path == "/")
                    if (prevWasLogin && isHome && !nav.done) {
                        nav.pending = true
                    }
                    nav.previousUrl = u
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    progress = 100
                    if (nav.pending && !nav.done && loginProfile != null) {
                        nav.pending = false
                        nav.done = true
                        ProfileManager.autoSaveLogin(view, loginProfile) { onClose() }
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, t: String?) {
                    if (!t.isNullOrBlank()) title = t
                }

                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }

                override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                    if (activity != null) {
                        android.app.AlertDialog.Builder(activity)
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                            .setOnCancelListener { result.cancel() }
                            .show()
                    } else result.confirm()
                    return true
                }

                override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                    if (activity != null) {
                        android.app.AlertDialog.Builder(activity)
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                            .setOnCancelListener { result.cancel() }
                            .show()
                    } else result.cancel()
                    return true
                }

                override fun onJsPrompt(
                    view: WebView,
                    url: String,
                    message: String,
                    defaultValue: String,
                    result: JsPromptResult
                ): Boolean {
                    if (activity != null) {
                        val input = android.widget.EditText(activity).apply { setText(defaultValue) }
                        android.app.AlertDialog.Builder(activity)
                            .setTitle(message)
                            .setView(input)
                            .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(input.text.toString()) }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                            .setOnCancelListener { result.cancel() }
                            .show()
                    } else result.cancel()
                    return true
                }

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean = false
            }
            loadUrl(initialUrl.ifBlank { ProfileManager.LOGIN_URL })
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        ProfileManager.cancelLogin()
                        onClose()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭登录")
                    }
                    Text(
                        title.ifBlank { "账号登录" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (loginProfile != null) {
                                ProfileManager.confirmLogin(webView, loginProfile, onClose)
                            }
                        }
                    ) {
                        Text("确认")
                    }
                    IconButton(onClick = { webView.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
                if (progress < 100) {
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        HorizontalDivider()
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.destroy() }
        )
    }
}
