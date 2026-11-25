package com.tstfaasaopwsw

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Bottom-sheet form to create or edit a [ServiceDef]: its name, its Android package and
 * its website domain. [initial] null = creation (a fresh id is minted). Either field may
 * be left blank (a web-only or app-only service), but not both.
 */
@Composable
internal fun ServiceForm(initial: ServiceDef?, onSave: (ServiceDef) -> Unit) {
    var label by rememberSaveable { mutableStateOf(initial?.label ?: "") }
    var pkg by rememberSaveable { mutableStateOf(initial?.packages?.joinToString(", ") ?: "") }
    var domain by rememberSaveable { mutableStateOf(initial?.domain ?: "") }

    // Title, padding and the glass frame come from the enclosing GlassSheet.
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(),
            label = { Text("Name") }, singleLine = true, shape = GlassShape.field)
        Spacer(Modifier.height(Space.md))
        OutlinedTextField(pkg, { pkg = it }, Modifier.fillMaxWidth(),
            label = { Text("Android package (e.g. com.twitter.android)") }, singleLine = true, shape = GlassShape.field)
        Spacer(Modifier.height(Space.md))
        OutlinedTextField(domain, { domain = it }, Modifier.fillMaxWidth(),
            label = { Text("Website domain (e.g. x.com)") }, singleLine = true, shape = GlassShape.field)
        Spacer(Modifier.height(Space.lg))
        val packages = pkg.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val dom = domain.trim().ifEmpty { null }
        val valid = label.isNotBlank() && (packages.isNotEmpty() || dom != null)
        Button(
            onClick = {
                val id = initial?.id ?: "u${System.currentTimeMillis()}"
                onSave(ServiceDef(id, label.trim(), packages, dom))
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}
