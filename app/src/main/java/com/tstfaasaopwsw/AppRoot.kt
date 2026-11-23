package com.tstfaasaopwsw

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Root: home springboard, then Browser (activity) or Blocking (sub-screen). */
@Composable
internal fun AppRoot() {
    val context = LocalContext.current
    var atHome by rememberSaveable { mutableStateOf(true) }

    if (atHome) {
        HomeScreen(
            onBrowser = { context.startActivity(Intent(context, BrowserActivity::class.java)) },
            onBlocking = { atHome = false },
        )
    } else {
        BackHandler { atHome = true }
        BlockHost(onHome = { atHome = true })
    }
}
