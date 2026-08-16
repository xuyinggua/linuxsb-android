package com.example.shaobing.scripts

object MetadataParser {

    data class Meta(
        val name: String,
        val namespace: String?,
        val description: String?,
        val version: String?,
        val matches: List<String>,
        val includes: List<String>,
        val requires: List<String>,
        val runAt: String,
        val grants: List<String>
    )

    fun parse(content: String): Meta {
        val block = Regex("(?s)//\\s*==UserScript==(.*?)//\\s*==/UserScript==")
            .find(content)?.groupValues?.get(1)
            ?: return Meta("", null, null, null, emptyList(), emptyList(), emptyList(), "document-idle", emptyList())

        var name = ""
        var namespace: String? = null
        var description: String? = null
        var version: String? = null
        var runAt = "document-idle"
        val matches = mutableListOf<String>()
        val includes = mutableListOf<String>()
        val requires = mutableListOf<String>()
        val grants = mutableListOf<String>()

        val lineRe = Regex("^@([\\w-]+)\\s*(.*)$")
        for (line in block.lineSequence()) {
            val stripped = line.trim().trimStart('/').trim()
            val m = lineRe.find(stripped) ?: continue
            val key = m.groupValues[1]
            val value = m.groupValues[2].trim()
            when (key) {
                "name" -> if (name.isEmpty()) name = value
                "namespace" -> namespace = value
                "description" -> description = value
                "version" -> version = value
                "match" -> matches.add(value)
                "include" -> includes.add(value)
                "require" -> requires.add(value)
                "grant" -> grants.add(value)
                "run-at" -> runAt = value
            }
        }
        return Meta(name, namespace, description, version, matches, includes, requires, runAt, grants)
    }
}
