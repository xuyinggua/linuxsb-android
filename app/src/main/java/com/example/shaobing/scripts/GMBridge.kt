package com.example.shaobing.scripts

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.shaobing.ShaoBingApp
import com.example.shaobing.net.OkHttpHolder
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GMBridge(private val webView: WebView) {

    @JavascriptInterface
    fun scriptsForUrl(url: String): String {
        val context = ShaoBingApp.appContext
        return runCatching {
            val scripts = ScriptManager.enabledScriptsForUrl(context, url)
            val arr = JSONArray()
            for (s in scripts) {
                val content = ScriptManager.readScriptContent(context, s)
                val meta = s.matches.let {
                    runCatching {
                        JSONArray(it).let { ja -> (0 until ja.length()).map { i -> ja.getString(i) } }
                    }.getOrDefault(emptyList())
                }
                val obj = JSONObject()
                obj.put("name", s.name)
                obj.put("namespace", s.namespace ?: "")
                obj.put("version", s.version ?: "")
                obj.put("description", s.description ?: "")
                obj.put("runAt", s.runAt)
                obj.put("matches", JSONArray(meta))
                obj.put("content", content)
                arr.put(obj)
            }
            arr.toString()
        }.getOrDefault("[]")
    }

    @JavascriptInterface
    fun getValue(scriptKey: String, key: String): String {
        return runCatching { ShaoBingApp.db.gmValueDao().get(scriptKey, key) ?: "" }
            .getOrDefault("")
    }

    @JavascriptInterface
    fun setValue(scriptKey: String, key: String, value: String) {
        runCatching {
            ShaoBingApp.db.gmValueDao().set(
                com.example.shaobing.db.GmValue(scriptKey = scriptKey, key = key, value = value)
            )
        }
    }

    @JavascriptInterface
    fun deleteValue(scriptKey: String, key: String) {
        runCatching { ShaoBingApp.db.gmValueDao().delete(scriptKey, key) }
    }

    @JavascriptInterface
    fun listValues(scriptKey: String): String {
        return runCatching {
            val keys = JSONArray()
            for (v in ShaoBingApp.db.gmValueDao().all(scriptKey)) keys.put(v.key)
            keys.toString()
        }.getOrDefault("[]")
    }

    @JavascriptInterface
    fun xhr(requestId: String, optionsJson: String) {
        runCatching {
            val opts = JSONObject(optionsJson)
            val url = opts.getString("url")
            val method = opts.optString("method", "GET")
            val data = opts.optString("data", "")
            val timeout = opts.optLong("timeout", 0L)

            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", com.example.shaobing.data.AppState.userAgent.ifBlank {
                    "Mozilla/5.0 (Linux; Android 13) Mobile"
                })

            val headers = opts.optJSONObject("headers")
            if (headers != null) {
                val it = headers.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    builder.header(k, headers.getString(k))
                }
            }
            builder.header("Referer", webView.url ?: "https://linux.sb/")

            if (method != "GET" && method != "HEAD") {
                val body = data.toRequestBody(opts.optString("contentType", "text/plain").toMediaTypeOrNull())
                builder.method(method, body)
            } else {
                builder.method(method, null)
            }

            val call = OkHttpHolder.client.newCall(builder.build())
            if (timeout > 0) {
                try { call.timeout().timeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS) }
                catch (_: Exception) {}
            }

            call.execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val headersObj = JSONObject()
                resp.headers.forEach { (k, v) -> headersObj.put(k, v) }
                val status = resp.code
                val statusText = resp.message

                webView.post {
                    val js = "__sb_xhr_cb(${JSONObject.quote(requestId)}, $status, ${JSONObject.quote(statusText)}, ${JSONObject.quote(headersObj.toString())}, ${JSONObject.quote(body)}, null);"
                    webView.evaluateJavascript(js, null)
                }
            }
        }.onFailure { e ->
            webView.post {
                val js = "__sb_xhr_cb(${JSONObject.quote(requestId)}, 0, ${JSONObject.quote(e.message ?: "error")}, \"{}\", \"\", ${JSONObject.quote(e.message ?: "error")});"
                webView.evaluateJavascript(js, null)
            }
        }
    }

    @JavascriptInterface
    fun openTab(url: String) {
        webView.post { webView.loadUrl(url) }
    }

    @JavascriptInterface
    fun notify(title: String, text: String) {
        webView.post {
            android.widget.Toast.makeText(
                webView.context,
                if (text.isNotBlank()) "$title\n$text" else title,
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}
