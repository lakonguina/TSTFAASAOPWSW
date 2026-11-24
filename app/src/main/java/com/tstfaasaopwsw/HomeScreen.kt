package com.tstfaasaopwsw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Phone-style home screen: a grid of app icons. Google -> browser, Settings -> blocking. */
@Composable
internal fun HomeScreen(onBrowser: () -> Unit, onBlocking: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(scheme.surface, scheme.surfaceVariant)))) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 72.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PhoneIcon(Icons.Filled.Search, "Google", Color(0xFF4285F4), onBrowser)
            PhoneIcon(Icons.Filled.Settings, "Réglages", Color(0xFF5F6368), onBlocking)
        }
    }
}

/** One launcher-style icon: rounded coloured badge with a label underneath. */
@Composable
private fun PhoneIcon(icon: ImageVector, label: String, bg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = bg, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(34.dp), tint = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
    }
}
