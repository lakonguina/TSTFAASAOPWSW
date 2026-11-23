package com.tstfaasaopwsw

import android.content.SharedPreferences

/** Browsing history of the embedded browser: most recent first, capped at 200. */
class BrowsingHistory(private val sp: SharedPreferences) {

    fun entries(): List<String> =
        sp.getString(K_HISTORY, "")!!.split('\n').filter { it.isNotEmpty() }

    fun add(url: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val list = entries().toMutableList()
        list.remove(url); list.add(0, url)
        while (list.size > MAX) list.removeAt(list.size - 1)
        sp.edit().putString(K_HISTORY, list.joinToString("\n")).apply()
    }

    fun clear() = sp.edit().remove(K_HISTORY).apply()

    private companion object {
        const val K_HISTORY = "history"
        const val MAX = 200
    }
}
