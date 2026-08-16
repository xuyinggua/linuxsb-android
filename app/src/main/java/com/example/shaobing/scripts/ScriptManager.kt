package com.example.shaobing.scripts

import android.content.Context
import com.example.shaobing.ShaoBingApp
import com.example.shaobing.db.Userscript
import com.example.shaobing.net.OkHttpHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.UUID

object ScriptManager {

    const val SCRIPTS_DIR = "userscripts"
    const val REQUIRES_DIR = "requires"

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
