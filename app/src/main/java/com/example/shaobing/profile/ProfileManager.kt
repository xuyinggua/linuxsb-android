package com.example.shaobing.profile

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.example.shaobing.ShaoBingApp
import com.example.shaobing.data.AppState
import com.example.shaobing.db.ProfileSnapshot
import com.example.shaobing.db.UserProfile
import com.example.shaobing.net.OkHttpHolder
import com.example.shaobing.web.BrowserClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

object ProfileManager {

    private val COOKIE_DOMAINS = listOf("https://linux.sb")
    const val LOGIN_URL = "https://linux.sb/login"

    @Volatile
    var awaitingLoginProfileId: Long? = null
        private set

    @Volatile
    private var awaitingMainLogin = false

    private const val LOGIN_PROFILE_NAME = "login"

    fun isMultiProfileSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    fun ensureDefaultProfile() {
        val dao = ShaoBingApp.db.profileDao()
        ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
            if (dao.count() == 0) {
                dao.insert(UserProfile(name = "默认账号", isCurrent = true))
            }
        }
    }

    fun currentProfile(): UserProfile? = ShaoBingApp.db.profileDao().current()

    fun currentProfileName(): String = currentProfile()?.name ?: ""

    fun notifyAccountChanged() {
        AppState.onAccountChanged?.invoke()
    }

    fun isLoggedIn(profile: UserProfile): Boolean = !profile.username.isNullOrBlank()

    /**
     * Get (and clean) the isolated profile used for the login WebView.
     * Returns null when multi-profile is unsupported.
     */
    fun getLoginProfile(): Profile? {
        if (!isMultiProfileSupported()) return null
        return runCatching {
            val p = ProfileStore.getInstance().getOrCreateProfile(LOGIN_PROFILE_NAME)
            runCatching { p.cookieManager.removeAllCookies(null) }
            runCatching { p.cookieManager.flush() }
            runCatching { p.webStorage.deleteAllData() }
            p
        }.getOrNull()
    }

    /**
     * Begin an isolated login for the given profile. Returns true when the
     * isolated login WebView should be shown (multi-profile supported).
     */
    fun beginLogin(target: UserProfile): Boolean {
        if (!isMultiProfileSupported()) return false
        awaitingLoginProfileId = target.id
        return true
    }

    fun cancelLogin() {
        awaitingLoginProfileId = null
    }

    /**
     * Manual login: called when the user taps the confirm button in the login
     * WebView. Detects login INSIDE the login WebView, writes cookies/storage +
     * user info into the target profile's snapshot, then shows a dialog.
     */
    fun confirmLogin(loginWebView: WebView, loginProfile: Profile, onDone: () -> Unit) {
        finishLogin(loginWebView, loginProfile, isAuto = false, onDone)
    }

    /**
     * Automatic login: called when the login WebView navigates from the login
     * page to the homepage. Saves cookies/storage + user info, shows a toast and
     * closes the login window automatically.
     */
    fun autoSaveLogin(loginWebView: WebView, loginProfile: Profile, onDone: () -> Unit) {
        finishLogin(loginWebView, loginProfile, isAuto = true, onDone)
    }

    private fun finishLogin(
        loginWebView: WebView,
        loginProfile: Profile,
        isAuto: Boolean,
        onDone: () -> Unit
    ) {
        val targetId = awaitingLoginProfileId
        if (targetId == null) {
            onDone()
            return
        }
        loginWebView.evaluateJavascript(DETECT_LOGIN_START_JS, null)
        pollLoginCheck(loginWebView, 20) { result ->
            val info = parseLoginResult(result)
            if (info == null) {
                loginWebView.post {
                    android.widget.Toast.makeText(
                        loginWebView.context,
                        "尚未登录，请先在页面中完成登录",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                return@pollLoginCheck
            }
            awaitingLoginProfileId = null
            saveLogin(loginWebView, loginProfile, targetId, info) {
                loginWebView.post {
                    if (isAuto) {
                        android.widget.Toast.makeText(
                            loginWebView.context,
                            "已自动获取到cookie，现在可以返回到之前的页面了",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        onDone()
                    } else {
                        android.app.AlertDialog.Builder(loginWebView.context)
                            .setTitle("登录成功")
                            .setMessage("已获取到cookie")
                            .setCancelable(false)
                            .setPositiveButton("确定") { _, _ -> onDone() }
                            .show()
                    }
                }
            }
        }
    }

    private fun saveLogin(
        loginWebView: WebView,
        loginProfile: Profile,
        targetId: Long,
        info: Pair<String, String>,
        onDone: () -> Unit
    ) {
        val cookies = captureCookies(loginProfile.cookieManager)
        loginWebView.evaluateJavascript(CAPTURE_STORAGE_JS) { storageResult ->
            val storageJson = parseJsonString(storageResult) ?: "{}"
            ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
                ShaoBingApp.db.profileSnapshotDao().upsert(
                    ProfileSnapshot(profileId = targetId, cookiesJson = cookies.toString(), storageJson = storageJson)
                )
                ShaoBingApp.db.profileDao().updateUserInfo(targetId, info.first, info.second)

                val target = ShaoBingApp.db.profileDao().byId(targetId)
                val main = AppState.webView
                val cur = ShaoBingApp.db.profileDao().current()
                if (target != null && main != null && cur?.id == targetId) {
                    withContext(Dispatchers.Main) {
                        restore(target, main)
                    }
                }
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    private fun parseLoginResult(result: String?): Pair<String, String>? {
        if (result.isNullOrBlank() || result == "null") return null
        return try {
            val obj = JSONObject(result)
            if (!obj.optBoolean("loggedIn", false)) return null
            val username = obj.optString("username", "")
            val uid = obj.optString("uid", "")
            if (username.isBlank() || uid.isBlank()) null else username to uid
        } catch (e: Exception) {
            null
        }
    }

    private fun pollLoginCheck(webView: WebView, attemptsLeft: Int, onResult: (String?) -> Unit) {
        webView.evaluateJavascript("window.__sbLoginCheck") { result ->
            val value = runCatching { org.json.JSONTokener(result).nextValue()?.toString() }.getOrNull()
            if (value != null && value != "pending" && value != "undefined" && value != "null") {
                onResult(value)
            } else if (attemptsLeft > 0) {
                webView.postDelayed({ pollLoginCheck(webView, attemptsLeft - 1, onResult) }, 250)
            } else {
                onResult(null)
            }
        }
    }

    /**
     * Fallback login path (multi-profile unsupported): log in inside the main
     * WebView after ensuring the target profile is active.
     */
    fun openLoginInMain(webView: WebView) {
        awaitingMainLogin = true
        webView.post { webView.loadUrl(LOGIN_URL) }
    }

    fun onMainLoginCheck(webView: WebView) {
        if (!awaitingMainLogin) return
        val info = fetchCurrentUserInfo(CookieManager.getInstance()) ?: return
        awaitingMainLogin = false
        snapshotCurrent(webView)
        val profile = ShaoBingApp.db.profileDao().current() ?: return
        ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
            ShaoBingApp.db.profileDao().updateUserInfo(profile.id, info.first, info.second)
        }
        webView.post {
            android.widget.Toast.makeText(
                webView.context,
                "登录成功：${info.first}（UID ${info.second}）",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun captureCookies(cm: CookieManager): JSONArray {
        val cookies = JSONArray()
        for (domain in COOKIE_DOMAINS) {
            val header = cm.getCookie(domain) ?: continue
            if (header.isBlank()) continue
            for (pair in header.split(";")) {
                val p = pair.trim()
                if (p.contains("=")) {
                    cookies.put(JSONObject().put("domain", domain).put("value", p))
                }
            }
        }
        return cookies
    }

    /**
     * Fetch a session's user info (username, uid) from /profile using the given
     * cookie manager. Returns null if not logged in.
     */
    fun fetchCurrentUserInfo(cm: CookieManager): Pair<String, String>? {
        val cookie = COOKIE_DOMAINS.mapNotNull { cm.getCookie(it) }.filter { it.isNotBlank() }
            .firstOrNull() ?: return null

        val html = runCatching {
            OkHttpHolder.client.newCall(
                Request.Builder()
                    .url("https://linux.sb/profile")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) Mobile")
                    .header("Cookie", cookie)
                    .header("Accept", "text/html")
                    .build()
            ).execute().use { it.body?.string() }
        }.getOrNull() ?: return null

        val nameRe = Regex("class=\"user-header\"[\\s\\S]*?class=\"user-name\"[^>]*href=\"/user/\\d+\"[^>]*>([^<]+)<")
        val uidRe = Regex("class=\"user-header\"[\\s\\S]*?class=\"user-name\"[^>]*href=\"/user/(\\d+)\"")
        val name = nameRe.find(html)?.groupValues?.get(1)?.trim()
        val uid = uidRe.find(html)?.groupValues?.get(1)
        if (!name.isNullOrBlank() && !uid.isNullOrBlank()) return name to uid

        val isLoginPage = html.contains("<title>") &&
            Regex("<title>([^<]*)</title>").find(html)?.groupValues?.get(1)?.contains("登录") == true
        if (isLoginPage) return null

        val titleRe = Regex("<title>([^<]+?)\\s*-\\s*")
        val titleName = titleRe.find(html)?.groupValues?.get(1)?.trim()
        if (!titleName.isNullOrBlank() && !uid.isNullOrBlank()) return titleName to uid
        return null
    }

    /**
     * Capture the current profile's cookies + web storage, then restore the target profile.
     */
    fun switchTo(target: UserProfile, webView: WebView, onDone: (() -> Unit)? = null) {
        snapshotCurrent(webView) {
            restore(target, webView, onDone)
        }
    }

    fun snapshotCurrent(webView: WebView, onDone: (() -> Unit)? = null) {
        val profile = ShaoBingApp.db.profileDao().current()
        if (profile == null) {
            onDone?.invoke()
            return
        }
        val cookies = captureCookies(CookieManager.getInstance())

        webView.evaluateJavascript(CAPTURE_STORAGE_JS) { result ->
            val storageJson = parseJsonString(result) ?: "{}"
            ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
                ShaoBingApp.db.profileSnapshotDao().upsert(
                    ProfileSnapshot(profileId = profile.id, cookiesJson = cookies.toString(), storageJson = storageJson)
                )
            }
            onDone?.invoke()
        }
    }

    private fun restore(target: UserProfile, webView: WebView, onDone: (() -> Unit)? = null) {
        val snapshot = ShaoBingApp.db.profileSnapshotDao().byProfile(target.id)
        val cm = CookieManager.getInstance()

        cm.removeAllCookies(null)
        cm.flush()
        WebStorage.getInstance().deleteAllData()

        if (snapshot != null) {
            val cookies = runCatching { JSONArray(snapshot.cookiesJson) }.getOrDefault(JSONArray())
            for (i in 0 until cookies.length()) {
                val o = cookies.getJSONObject(i)
                val domain = o.getString("domain")
                val value = o.getString("value")
                runCatching { cm.setCookie(domain, value, null) }
            }
            runCatching { cm.flush() }

            val storage = runCatching { JSONObject(snapshot.storageJson) }.getOrDefault(JSONObject())
            val restoreJs = buildStorageRestoreJs(storage)
            (webView.webViewClient as? BrowserClient)?.queueJavascript(restoreJs)
        }

        ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
            val dao = ShaoBingApp.db.profileDao()
            for (p in dao.all()) {
                dao.setCurrent(p.id, p.id == target.id)
            }
            withContext(Dispatchers.Main) {
                notifyAccountChanged()
                onDone?.invoke()
            }
        }

        webView.reload()
    }

    private fun buildStorageRestoreJs(storage: JSONObject): String {
        val sb = StringBuilder(
            "(function(){try{localStorage.clear();}catch(e){}try{sessionStorage.clear();}catch(e){}"
        )
        val local = storage.optJSONObject("local") ?: JSONObject()
        val it = local.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = local.getString(k)
            sb.append("try{localStorage.setItem(")
                .append(JSONObject.quote(k)).append(",").append(JSONObject.quote(v))
                .append(");}catch(e){};")
        }
        sb.append("})()")
        return sb.toString()
    }

    private fun parseJsonString(result: String?): String? {
        if (result.isNullOrBlank() || result == "null") return null
        return try {
            org.json.JSONTokener(result).nextValue()?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private val CAPTURE_STORAGE_JS = """
        JSON.stringify({
          local: (function(){var o={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);o[k]=localStorage.getItem(k);}return o;})(),
          session: (function(){var o={};for(var i=0;i<sessionStorage.length;i++){var k=sessionStorage.key(i);o[k]=sessionStorage.getItem(k);}return o;})()
        })
    """.trimIndent()

    private val DETECT_LOGIN_START_JS = """
        (function(){
          window.__sbLoginCheck = 'pending';
          try {
            fetch('/profile', {credentials:'include'}).then(function(r){ return r.text(); }).then(function(html){
              var m = html.match(/class="user-header"[\s\S]*?class="user-name"[^>]*href="\/user\/(\d+)"[^>]*>([^<]+)</);
              window.__sbLoginCheck = (m && m[1] && m[2])
                ? JSON.stringify({loggedIn:true, uid:m[1], username:m[2]})
                : JSON.stringify({loggedIn:false, uid:'', username:''});
            }).catch(function(){
              window.__sbLoginCheck = JSON.stringify({loggedIn:false, uid:'', username:''});
            });
          } catch(e) {
            window.__sbLoginCheck = JSON.stringify({loggedIn:false, uid:'', username:''});
          }
          return 'started';
        })()
    """.trimIndent()
}
