package com.khazaddum.forcelist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Écran principal : trois onglets (Apps, Web, Réglages) en Jetpack Compose Material 3.
 * Le découpage vit dans [ForcelistApp] (coquille) et les écrans voisins.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Updater.checkInBackground(this) // mise à jour auto depuis GitHub (throttlée)
        setContent { ForcelistTheme { ForcelistApp() } }
    }
}
