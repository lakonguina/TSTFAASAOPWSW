package com.tstfaasaopwsw

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Choix du délai : puces rapides + champ libre en minutes. [onPick] applique la valeur. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DelayPicker(current: Long, onPick: (Long) -> Unit) {
    var custom by rememberSaveable { mutableStateOf("") }
    var customError by rememberSaveable { mutableStateOf(false) }

    Column {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp, 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (choice in Config.delayChoicesMinutes) {
                FilterChip(
                    selected = choice == current,
                    onClick = { onPick(choice) },
                    label = { Text(Config.formatDelay(choice)) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.filter(Char::isDigit); customError = false },
                modifier = Modifier.width(160.dp),
                label = { Text("Perso (min)") },
                isError = customError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedButton(onClick = {
                val m = custom.toLongOrNull()
                if (m == null) customError = true else { onPick(m); custom = "" }
            }) { Text("Appliquer") }
        }
    }
}
