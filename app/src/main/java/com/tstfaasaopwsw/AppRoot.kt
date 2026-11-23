package com.tstfaasaopwsw

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Racine : springboard d'accueil, puis Navigateur (activité) ou Blocage (sous-écran). */
@Composable
internal fun AppRoot() {
    val context = LocalContext.current
    var atHome by rememberSaveable { mutableStateOf(true) }

    if (atHome) {
        HomeScreen(
            onBrowser = { context.startActivity(Intent(context, BrowserActivity::class.java)) },
            onBlocage = { atHome = false },
        )
    } else {
        BackHandler { atHome = true }
        BlocageHost(onHome = { atHome = true })
    }
}
