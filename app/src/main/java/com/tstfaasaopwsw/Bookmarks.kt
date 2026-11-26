package com.tstfaasaopwsw

import android.content.SharedPreferences

/** Saved bookmarks of the embedded browser: most recent first. One row = "url\u001Ftitle". */
class Bookmarks(private val sp: SharedPreferences) {

    data class Entry(val url: String, val title: String)

    fun entries(): List<Entry> =
        sp.getString(K, "")!!.split('\n').filter { it.isNotEmpty() }.map { row ->
            val p = row.split(SEP); Entry(p[0], p.getOrNull(1).orEmpty())
        }

    fun has(url: String): Boolean = entries().any { it.url == url }

    fun add(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val list = entries().filter { it.url != url }.toMutableList()
        list.add(0, Entry(url, title.ifBlank { UrlRules.host(url) }))
        save(list)
    }

    fun remove(url: String) = save(entries().filter { it.url != url })

    private fun save(list: List<Entry>) =
        sp.edit().putString(K, list.joinToString("\n") { "${it.url}$SEP${it.title}" }).apply()

    private companion object {
        const val K = "bookmarks"
        const val SEP = "\u001F"
    }
}
