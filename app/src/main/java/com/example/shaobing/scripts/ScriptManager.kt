package com.example.shaobing.scripts

import android.content.Context
import com.example.shaobing.ShaoBingApp
import com.example.shaobing.db.Userscript
import com.example.shaobing.net.OkHttpHolder
import com.example.shaobing.ui.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ScriptManager {

    const val SCRIPTS_DIR = "userscripts"
    const val REQUIRES_DIR = "requires"

    private const val BUILTIN_NAMESPACE = "com.shaobing.builtin"

    private val BUILTIN_ASSETS = listOf("builtin_top_sticky.user.js")

    enum class ScriptSource(val baseUrl: String, val label: String) {
        GREASYFORK("https://api.greasyfork.org", "GreasyFork"),
        SLEAZYFORK("https://api.sleazyfork.org", "SleazyFork")
    }

    data class ScriptSearchResult(
        val name: String,
        val description: String,
        val version: String,
        val pageUrl: String,
        val codeUrl: String,
        val totalInstalls: Long
    )

    /**
     * Search scripts on GreasyFork / SleazyFork (same JSON API). Returns an
     * empty list when the query is blank or the request fails.
     */
    suspend fun searchScripts(source: ScriptSource, query: String): List<ScriptSearchResult> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val url = source.baseUrl + "/en/scripts.json?q=" +
                java.net.URLEncoder.encode(query.trim(), "UTF-8") + "&page=1"
            val body = runCatching {
                OkHttpHolder.client.newCall(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0 Mobile Safari/537.36")
                        .header("Accept", "application/json")
                        .build()
                ).execute().use { if (it.isSuccessful) it.body?.string() else null }
            }.getOrNull() ?: return@withContext emptyList()

            return@withContext runCatching {
                val root = JSONObject(body)
                val arr = root.optJSONArray("query") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    if (o.optBoolean("deleted", false)) return@mapNotNull null
                    val codeUrl = o.optString("code_url", "")
                    if (codeUrl.isBlank()) return@mapNotNull null
                    val pageUrl = o.optString("url", codeUrl)
                    ScriptSearchResult(
                        name = o.optString("name", "未命名"),
                        description = stripHtml(o.optString("description", "")).trim(),
                        version = o.optString("version", ""),
                        pageUrl = pageUrl,
                        codeUrl = codeUrl,
                        totalInstalls = o.optLong("total_installs", 0L)
                    )
                }
            }.getOrDefault(emptyList())
        }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

    data class DownloadResult(val script: Userscript)

    sealed class DownloadError : Exception() {
        class Network(override val message: String) : DownloadError()
        class Parse(override val message: String) : DownloadError()
        class NotFound(override val message: String) : DownloadError()
    }

    fun scriptsDir(context: Context): File =
        File(context.filesDir, SCRIPTS_DIR).apply { mkdirs() }

    fun requiresDir(context: Context): File =
        File(context.filesDir, "$SCRIPTS_DIR/$REQUIRES_DIR").apply { mkdirs() }

    suspend fun downloadFromUrl(inputUrl: String): Userscript = withContext(Dispatchers.IO) {
        val context = ShaoBingApp.appContext

        val url = resolveScriptUrl(inputUrl) ?: throw DownloadError.NotFound("无法从该地址解析出油猴脚本")
        val content = fetch(url) ?: throw DownloadError.Network("下载失败: $url")

        if (!content.contains("==UserScript==")) {
            throw DownloadError.Parse("不是有效的油猴脚本（缺少 ==UserScript== 元数据块）")
        }

        val meta = MetadataParser.parse(content)
        if (meta.name.isEmpty()) {
            throw DownloadError.Parse("脚本缺少 @name")
        }

        val requireFiles = mutableListOf<String>()
        val requireNames = mutableListOf<String>()
        for (requireUrl in meta.requires) {
            val depContent = fetch(requireUrl)
            if (depContent != null) {
                val hash = depContent.hashCode().toUInt().toString(16)
                val fileName = "$hash.js"
                File(requiresDir(context), fileName).writeText(depContent)
                requireFiles.add(fileName)
                requireNames.add(requireUrl)
            }
        }

        val fileName = "${UUID.randomUUID()}.user.js"
        File(scriptsDir(context), fileName).writeText(content)

        val script = Userscript(
            name = meta.name,
            namespace = meta.namespace,
            description = meta.description,
            version = meta.version,
            sourceUrl = inputUrl,
            file = fileName,
            matches = JSONArray(meta.matches).toString(),
            includes = JSONArray(meta.includes).toString(),
            requires = JSONArray(requireNames).toString(),
            requireFiles = JSONArray(requireFiles).toString(),
            runAt = meta.runAt,
            grants = JSONArray(meta.grants).toString(),
            enabled = true
        )
        ShaoBingApp.db.userscriptDao().insert(script)
        script
    }

    private suspend fun resolveScriptUrl(inputUrl: String): String? = withContext(Dispatchers.IO) {
        val trimmed = inputUrl.trim()
        if (trimmed.endsWith(".user.js")) return@withContext trimmed
        if (!trimmed.startsWith("http")) return@withContext null

        val content = fetch(trimmed) ?: return@withContext null
        if (content.contains("==UserScript==")) return@withContext trimmed

        // greasyfork style HTML page: look for a .user.js href
        val linkRe = Regex("href=[\"']([^\"']+\\.user\\.js[^\"']*)[\"']")
        val m = linkRe.find(content) ?: return@withContext null
        var href = m.groupValues[1]
        if (!href.startsWith("http")) {
            val base = trimmed.substringBeforeLast('/')
            href = if (href.startsWith("/")) {
                val scheme = trimmed.substringBefore("://")
                val host = trimmed.substringAfter("://").substringBefore('/')
                "$scheme://$host$href"
            } else {
                "$base/$href"
            }
        }
        href
    }

    private fun fetch(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/javascript,*/*;q=0.8")
            .build()
        OkHttpHolder.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            resp.body?.string()
        }
    }.getOrNull()

    fun readScriptContent(context: Context, script: Userscript): String {
        val file = File(scriptsDir(context), script.file)
        if (!file.exists()) return ""
        val main = file.readText()
        val deps = try {
            JSONArray(script.requireFiles).let { arr ->
                (0 until arr.length()).joinToString("\n;\n") { i ->
                    val f = File(requiresDir(context), arr.getString(i))
                    if (f.exists()) f.readText() else ""
                }
            }
        } catch (e: Exception) {
            ""
        }
        return if (deps.isBlank()) main else "$deps\n;\n$main"
    }

    fun all(context: Context): List<Userscript> = ShaoBingApp.db.userscriptDao().all()

    fun disableAll() {
        val dao = ShaoBingApp.db.userscriptDao()
        ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
            for (s in dao.enabled()) {
                dao.setEnabled(s.id, false)
            }
        }
    }

    data class BuiltinInfo(
        val asset: String,
        val name: String,
        val description: String?,
        val version: String?,
        val installed: Boolean
    )

    fun isBuiltin(script: Userscript): Boolean = script.sourceUrl.startsWith("builtin://")

    fun builtinScripts(context: Context): List<BuiltinInfo> {
        val dao = ShaoBingApp.db.userscriptDao()
        return BUILTIN_ASSETS.mapNotNull { asset ->
            val content = runCatching {
                context.assets.open(asset).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null
            val meta = MetadataParser.parse(content)
            if (meta.name.isEmpty()) return@mapNotNull null
            val installed = dao.all().any { it.namespace == meta.namespace && it.name == meta.name }
            BuiltinInfo(
                asset = asset,
                name = meta.name,
                description = meta.description,
                version = meta.version,
                installed = installed
            )
        }
    }

    /**
     * Install built-in scripts on the very first launch only. Afterwards the
     * user manages built-ins manually from the built-in list in the scripts UI.
     */
    suspend fun installBuiltinIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        if (Prefs.builtinsInitialized) return@withContext
        for (asset in BUILTIN_ASSETS) {
            installBuiltin(context, asset)
        }
        Prefs.builtinsInitialized = true
    }

    /**
     * Install a single built-in script (shipped as an asset). Returns true when
     * a new script was inserted, false when it is already installed.
     */
    suspend fun installBuiltin(context: Context, asset: String): Boolean = withContext(Dispatchers.IO) {
        val dao = ShaoBingApp.db.userscriptDao()
        val content = runCatching {
            context.assets.open(asset).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@withContext false
        val meta = MetadataParser.parse(content)
        if (meta.name.isEmpty()) return@withContext false
        if (dao.all().any { it.namespace == meta.namespace && it.name == meta.name }) return@withContext false

        File(scriptsDir(context), asset).writeText(content)
        dao.insert(
            Userscript(
                name = meta.name,
                namespace = meta.namespace,
                description = meta.description,
                version = meta.version,
                sourceUrl = "builtin://$asset",
                file = asset,
                matches = JSONArray(meta.matches).toString(),
                includes = JSONArray(meta.includes).toString(),
                requires = "[]",
                requireFiles = "[]",
                runAt = meta.runAt,
                grants = JSONArray(meta.grants).toString(),
                enabled = true
            )
        )
        true
    }

    fun enabledScriptsForUrl(context: Context, url: String): List<Userscript> {
        val dao = ShaoBingApp.db.userscriptDao()
        return dao.enabled().filter { s ->
            val matches = try {
                JSONArray(s.matches).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            } catch (e: Exception) { emptyList<String>() }
            val includes = try {
                JSONArray(s.includes).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            } catch (e: Exception) { emptyList<String>() }
            MatchPattern.matches(url, matches) || MatchPattern.matches(url, includes)
        }
    }
}
