package com.example.shaobing.ui

import com.example.shaobing.db.Bookmark
import org.tomlj.Toml

object BookmarkIO {

    /**
     * Serializes bookmarks to a TOML array of tables:
     *
     * [[bookmark]]
     * title = "..."
     * url = "..."
     * createdAt = <millis>
     */
    fun exportToml(bookmarks: List<Bookmark>): String {
        val sb = StringBuilder()
        for (b in bookmarks) {
            sb.append("[[bookmark]]\n")
            sb.append("title = ").append(tomlString(b.title)).append('\n')
            sb.append("url = ").append(tomlString(b.url)).append('\n')
            sb.append("createdAt = ").append(b.createdAt).append('\n')
            sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * Parses bookmarks from TOML. Expects a [[bookmark]] (or [[bookmarks]])
     * array of tables. Items may use url/URL/link/href and title/name/名称/标题
     * keys; missing titles default to the url and missing timestamps to now.
     */
    fun parseToml(text: String): List<Bookmark> {
        val trimmed = text.trim().removePrefix("\uFEFF")
        if (trimmed.isBlank()) return emptyList()
        val result = runCatching { Toml.parse(trimmed) }.getOrNull() ?: return emptyList()
        if (result.hasErrors()) return emptyList()

        val arr = result.getArray("bookmark") ?: result.getArray("bookmarks") ?: return emptyList()
        val out = mutableListOf<Bookmark>()
        for (i in 0 until arr.size()) {
            val item = arr.getTable(i) ?: continue
            var url = item.getString("url") ?: ""
            if (url.isBlank()) url = item.getString("URL") ?: ""
            if (url.isBlank()) url = item.getString("link") ?: ""
            if (url.isBlank()) url = item.getString("href") ?: ""
            if (url.isBlank()) continue
            var title = item.getString("title") ?: ""
            if (title.isBlank()) title = item.getString("name") ?: ""
            if (title.isBlank()) title = item.getString("名称") ?: ""
            if (title.isBlank()) title = item.getString("标题") ?: ""
            if (title.isBlank()) title = url
            var createdAt = item.getLong("createdAt") ?: 0L
            if (createdAt <= 0) createdAt = item.getLong("time") ?: 0L
            if (createdAt <= 0) createdAt = System.currentTimeMillis()
            out.add(Bookmark(title = title, url = url, createdAt = createdAt))
        }
        return out
    }

    private fun tomlString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}
