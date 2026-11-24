package com.tstfaasaopwsw

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Blocking screen: apps, web, settings (bottom tabs) + back to home. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlockHost(onHome: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    var appsEpoch by remember { mutableIntStateOf(0) }

    // Returning to the foreground (e.g. back from the Play Store) reloads the state AND
    // the app list, so recent installs show up without relaunching the app.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { tick++; appsEpoch++ }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snapshot = remember(tick) { buildSnapshot(context) }
    val refresh: () -> Unit = { tick++ }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val services = produceState<List<ServiceInfo>?>(initialValue = null, appsEpoch, tick) { value = loadServices(context) }
    val opened = services.value?.firstOrNull { it.key == openedKey }
    if (opened != null) BackHandler { openedKey = null }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(opened?.label ?: "Blocking") },
                navigationIcon = {
                    val back = opened != null
                    IconButton(onClick = { if (back) openedKey = null else onHome() }) {
                        Icon(Icons.Filled.ArrowBack, if (back) "Back" else "Home")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0 && opened == null, onClick = { tab = 0; openedKey = null },
                    icon = { Icon(Icons.Filled.Lock, null) }, label = { Text("Apps") },
                )
                NavigationBarItem(
                    selected = tab == 1 && opened == null, onClick = { tab = 1; openedKey = null },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("General") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                opened != null -> ServiceDetailScreen(opened, snapshot, refresh)
                tab == 0 -> ServicesScreen(services.value, snapshot, refresh, onOpen = { openedKey = it.key })
                else -> SettingsScreen(snapshot, refresh)
            }
        }
    }
}
