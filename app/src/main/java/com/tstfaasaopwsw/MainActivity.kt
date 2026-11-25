package com.tstfaasaopwsw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Blocking screen (apps, web, settings), opened from the browser's Settings action. The
 * app launches straight into [BrowserActivity]; this activity is the controls surface
 * reached from there, and "back/home" returns to the browser.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ForcelistTheme { BlockHost(onHome = { finish() }) } }
    }
}
