package com.tstfaasaopwsw

import android.content.SharedPreferences

/** A record of files the browser downloaded, newest first. One row = "url\u001Fname\u001Fmillis". */
class DownloadLog(private val sp: SharedPreferences) {

    data class Entry(val url: String, val name: String, val time: Long)

    fun entries(): List<Entry> =
        sp.getString(K, "")!!.split('\n').filter { it.isNotEmpty() }.mapNotNull { row ->
            val p = row.split(SEP)
            if (p.size == 3) Entry(p[0], p[1], p[2].toLongOrNull() ?: 0L) else null
        }

    fun add(url: String, name: String) {
        val list = listOf(Entry(url, name, System.currentTimeMillis())) + entries()
        sp.edit().putString(K, list.take(MAX).joinToString("\n") { "${it.url}$SEP${it.name}$SEP${it.time}" }).apply()
    }

    fun clear() = sp.edit().remove(K).apply()

    private companion object {
        const val K = "downloadLog"
        const val SEP = "\u001F"
        const val MAX = 200
    }
}
