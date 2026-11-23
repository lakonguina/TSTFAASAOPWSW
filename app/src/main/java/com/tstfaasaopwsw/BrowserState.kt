package com.tstfaasaopwsw

import android.content.SharedPreferences

/** Persisted open tabs: URLs + current index, restored after close/reboot. */
class BrowserState(private val sp: SharedPreferences) {

    fun tabs(): List<String> =
        sp.getString(K_TABS, "")!!.split('\n').filter { it.isNotEmpty() }

    fun currentIndex(): Int = sp.getInt(K_CUR, 0)

    fun save(urls: List<String>, current: Int) =
        sp.edit().putString(K_TABS, urls.joinToString("\n")).putInt(K_CUR, current).apply()

    private companion object {
        const val K_TABS = "tabsUrls"
        const val K_CUR = "tabCurrent"
    }
}
