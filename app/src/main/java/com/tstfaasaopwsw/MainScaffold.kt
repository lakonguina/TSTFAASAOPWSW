package com.tstfaasaopwsw

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Écran Blocage : apps, web, réglages (onglets du bas) + retour à l'accueil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlocageHost(onHome: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    var appsEpoch by remember { mutableIntStateOf(0) }

    // Revenir au premier plan (ex. retour du Play Store) recharge l'état ET la liste
    // d'apps, pour que les installations récentes apparaissent sans relancer l'app.
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
    val apps = produceState<List<AppInfo>?>(initialValue = null, appsEpoch) { value = loadApps(context) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Blocage") },
                navigationIcon = {
                    IconButton(onClick = onHome) { Icon(Icons.Filled.ArrowBack, "Accueil") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Lock, null) }, label = { Text("Apps") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Web") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Réglages") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> BlocagesScreen(apps.value, snapshot, refresh)
                1 -> WebScreen(snapshot, refresh)
                else -> SettingsScreen(snapshot, refresh)
            }
        }
    }
}
