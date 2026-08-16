package com.example.shaobing.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.shaobing.data.AppState
import com.example.shaobing.net.OkHttpHolder
import com.example.shaobing.ui.Prefs
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class BrowserClient(
    private val context: Context,
    private val onExternalUrl: (String) -> Unit
) : WebViewClient() {

    private val engineScript: String by lazy {
        context.assets.open("runtime.js").bufferedReader().use { it.readText() }
    }

    private val pendingJs = mutableListOf<String>()

    /**
     * Invoked when the main WebView navigates to a login URL. Return true to
     * block the navigation and handle it (e.g. open the isolated login popup).
     */
    var onLoginLink: ((String) -> Boolean)? = null

    private fun isLoginUrl(url: String): Boolean {
        if (!url.startsWith("http")) return false
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            (host == "linux.sb" || host.endsWith(".linux.sb")) &&
                Uri.parse(url).path?.lowercase()?.startsWith("/login") == true
        } catch (e: Exception) {
            false
        }
    }

    private fun isExternal(url: String): Boolean {
        if (!url.startsWith("http")) return false
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            host == "linux.sb" || host.endsWith(".linux.sb")
        } catch (e: Exception) {
            false
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (isLoginUrl(url)) {
            if (onLoginLink?.invoke(url) == true) return true
        }
        if (isExternal(url)) return false
        if (url.startsWith("http")) {
            onExternalUrl(url)
            return true
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        if (isLoginUrl(url)) {
            if (onLoginLink?.invoke(url) == true) return true
        }
        if (isExternal(url)) return false
        if (url.startsWith("http")) {
            onExternalUrl(url)
            return true
        }
        return super.shouldOverrideUrlLoading(view, url)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val u = url ?: return
        AppState.currentUrl = u
        AppState.onUrlChanged?.invoke(u)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        if (url != null) AppState.currentUrl = url
        AppState.onProgressChanged?.invoke(100)
        val queue = synchronized(pendingJs) {
            if (pendingJs.isEmpty()) emptyList()
            else pendingJs.toList().also { pendingJs.clear() }
        }
        for (js in queue) view.evaluateJavascript(js, null)
        view.evaluateJavascript(ViewportFix.injectJs(), null)
        AppState.onPageFinished?.invoke(view)
    }

    fun queueJavascript(js: String) {
        synchronized(pendingJs) { pendingJs.add(js) }
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        if (!request.isForMainFrame || !isInjectionEligible(request) || !Prefs.scriptsEnabled) {
            return super.shouldInterceptRequest(view, request)
        }
        return try {
            injectHtml(view, url, engineScript)
        } catch (e: Exception) {
            null
        }
    }

    private fun isInjectionEligible(request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (url.startsWith("data:") || url.startsWith("about:") || url.startsWith("javascript:")) return false
        val path = Uri.parse(url).path?.lowercase() ?: ""
        val nonHtmlExtensions = listOf(
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico",
            ".pdf", ".zip", ".mp4", ".webm", ".mp3", ".wav", ".woff", ".woff2", ".ttf", ".json"
        )
        if (nonHtmlExtensions.any { path.endsWith(it) }) return false
        val accept = request.requestHeaders?.get("Accept") ?: ""
        if (accept.isNotBlank() && !accept.contains("text/html")) return false
        return true
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun injectHtml(view: WebView, url: String, script: String): WebResourceResponse? {
        val cookie = CookieManager.getInstance().getCookie(url)
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", AppState.userAgent.ifBlank { "Mozilla/5.0 (Linux; Android 13) Mobile" })
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        if (!cookie.isNullOrEmpty()) builder.header("Cookie", cookie)

        OkHttpHolder.client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 200) return null

            val finalUrl = resp.request.url.toString()
            if (finalUrl != url) return null

            val contentType = resp.header("Content-Type") ?: "text/html"
            if (!contentType.contains("text/html", true) && !contentType.contains("application/xhtml+xml", true)) {
                return null
            }
            val disposition = resp.header("Content-Disposition") ?: ""
            if (disposition.contains("attachment", true)) return null

            val setCookies = resp.headers("Set-Cookie")
            if (!setCookies.isEmpty()) {
                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                for (sc in setCookies) {
                    runCatching { cm.setCookie(finalUrl, sc) }
                }
                if (Build.VERSION.SDK_INT >= 21) {
                    runCatching { cm.flush() }
                }
            }

            val body = resp.body?.bytes() ?: return null
            var html = String(body, Charsets.UTF_8)
            val hasCharset = contentType.contains("charset", true)
            if (!hasCharset) {
                val m = Regex("charset\\s*=\\s*[\"']?([\\w-]+)").find(html.take(4096))
                if (m != null) {
                    runCatching { html = String(body, Charset.forName(m.groupValues[1])) }
                }
            }
            val injected = injectIntoHtml(html, script)
            val stream = ByteArrayInputStream(injected.toByteArray(Charsets.UTF_8))
            return WebResourceResponse("text/html", "UTF-8", stream)
        }
    }

    private fun injectIntoHtml(html: String, script: String): String {
        val tag = "<script type=\"text/javascript\">$script</script>"
        val lower = html.lowercase()

        val headStart = lower.indexOf("<head")
        if (headStart >= 0) {
            val headEnd = html.indexOf('>', headStart)
            if (headEnd >= 0) return html.substring(0, headEnd + 1) + tag + html.substring(headEnd + 1)
        }
        val htmlTag = lower.indexOf("<html")
        if (htmlTag >= 0) {
            val htmlEnd = html.indexOf('>', htmlTag)
            if (htmlEnd >= 0) return html.substring(0, htmlEnd + 1) + tag + html.substring(htmlEnd + 1)
        }
        val doctype = lower.indexOf("<!doctype")
        if (doctype >= 0) {
            val dtEnd = html.indexOf('>', doctype)
            if (dtEnd >= 0) return html.substring(0, dtEnd + 1) + tag + html.substring(dtEnd + 1)
        }
        return tag + html
    }
}
