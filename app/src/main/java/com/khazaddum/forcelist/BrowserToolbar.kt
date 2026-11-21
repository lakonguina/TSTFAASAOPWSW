package com.khazaddum.forcelist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Barre d'outils : navigation, rechargement, compteur d'onglets, historique, adresse. */
@Composable
internal fun Toolbar(
    tab: Tab,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onGo: (String) -> Unit,
    onTabs: () -> Unit,
    onHistory: () -> Unit,
) {
    var text by remember(tab, tab.url) { mutableStateOf(tab.url) }
    Column {
        Row(Modifier.fillMaxWidth().padding(4.dp, 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = tab.canGoBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
            IconButton(onClick = onForward, enabled = tab.canGoForward) { Icon(Icons.Filled.ArrowForward, "Avancer") }
            IconButton(onClick = onReload) { Icon(Icons.Filled.Refresh, "Recharger") }
            TextButton(onClick = onTabs) { Text("$tabCount") }
            IconButton(onClick = onHistory) { Icon(Icons.Filled.List, "Historique") }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().padding(8.dp, 0.dp, 8.dp, 6.dp),
            singleLine = true,
            placeholder = { Text("Adresse ou recherche") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo(text) }),
        )
    }
}
