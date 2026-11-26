package com.tstfaasaopwsw

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Blocking section: Apps and General screens with their own large titles, plus a small,
 * centered glass segmented bar (Web · Apps · General) as the only chrome. "Web" returns to
 * the browser, so the browser is just another destination in the same menu.
 */
@Composable
internal fun BlockHost(onHome: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    var appsEpoch by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { tick++; appsEpoch++ }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snapshot = remember(tick) { buildSnapshot(context) }
    val refresh: () -> Unit = { tick++ }
    val store = remember { Store.get(context) }
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 = Lists, 1 = General
    var openedListId by rememberSaveable { mutableStateOf<String?>(null) }
    val openedList = remember(openedListId, tick, appsEpoch) { store.ruleLists().firstOrNull { it.id == openedListId } }
    if (openedList != null) BackHandler { openedListId = null }

    GlassScene(
        content = {
            Box(Modifier.fillMaxSize()) {
                when {
                    openedList != null -> ListDetailScreen(openedList, snapshot, refresh, onBack = { openedListId = null })
                    tab == 0 -> ListsScreen(snapshot, refresh, onOpen = { openedListId = it.id })
                    else -> SettingsScreen(snapshot, refresh)
                }
            }
        },
        chrome = {
            if (openedList == null) {
                GlassSegmentedBar(
                    tabs = listOf(
                        GlassTab(Icons.Filled.Home, "Web", false, onHome),
                        GlassTab(Icons.Filled.PlaylistPlay, "Lists", tab == 0) { tab = 0 },
                        GlassTab(Icons.Filled.Settings, "General", tab == 1) { tab = 1 },
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        },
    )
}
