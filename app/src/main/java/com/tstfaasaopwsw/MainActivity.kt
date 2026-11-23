package com.tstfaasaopwsw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Main screen: home springboard ([AppRoot]) leading to the Browser or Blocking
 * (apps, web, settings). Built with Jetpack Compose Material 3.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Updater.checkInBackground(this) // auto-update from GitHub (throttled)
        setContent { ForcelistTheme { AppRoot() } }
    }
}
