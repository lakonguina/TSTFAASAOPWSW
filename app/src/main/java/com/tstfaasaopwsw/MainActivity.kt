package com.tstfaasaopwsw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Écran principal : springboard d'accueil ([AppRoot]) menant au Navigateur ou au
 * Blocage (apps, web, réglages). En Jetpack Compose Material 3.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Updater.checkInBackground(this) // mise à jour auto depuis GitHub (throttlée)
        setContent { ForcelistTheme { AppRoot() } }
    }
}
