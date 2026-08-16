package com.example.shaobing.scripts

object MatchPattern {

    fun matches(url: String, patterns: List<String>): Boolean {
        if (patterns.isEmpty()) return true
        return patterns.any { compile(it)?.matches(url) ?: false }
    }

    fun compile(pattern: String): Regex? {
        if (pattern.length > 2 && pattern.startsWith("/")) {
            val last = pattern.lastIndexOf('/')
            if (last > 0) {
                val body = pattern.substring(1, last)
                val flags = pattern.substring(last + 1)
                return runCatching { Regex(body, flagSet(flags)) }.getOrNull()
            }
        }
        val sb = StringBuilder("^")
        for (c in pattern) {
            when (c) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                else -> sb.append(Regex.escape(c.toString()))
            }
        }
        sb.append("$")
        return runCatching { Regex(sb.toString()) }.getOrNull()
    }

    private fun flagSet(flags: String): Set<RegexOption> = buildSet {
        if ('i' in flags) add(RegexOption.IGNORE_CASE)
        if ('m' in flags) add(RegexOption.MULTILINE)
        if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
    }
}
