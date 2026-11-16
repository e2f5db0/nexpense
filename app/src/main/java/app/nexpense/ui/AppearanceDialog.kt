// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AppearanceDialog(
    accent: AccentColor,
    appFont: AppFont,
    onAccent: (AccentColor) -> Unit,
    onFont: (AppFont) -> Unit,
    dismiss: () -> Unit,
) {
    AlertDialog(onDismissRequest = dismiss,
        title = { Text("Appearance", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Main accent color", style = MaterialTheme.typography.titleMedium)
            AccentColor.entries.forEach { option ->
                OutlinedButton(onClick = { onAccent(option) }, Modifier.fillMaxWidth(),
                    colors = choiceColors(accent == option)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).background(option.primary))
                        Spacer(Modifier.width(12.dp))
                        Text(option.label, Modifier.weight(1f))
                        if (accent == option) Text("✓")
                    }
                }
            }
            Text("Font", style = MaterialTheme.typography.titleMedium)
            AppFont.entries.forEach { option ->
                OutlinedButton(onClick = { onFont(option) }, Modifier.fillMaxWidth(),
                    colors = choiceColors(appFont == option)) {
                    Text((if (appFont == option) "✓ " else "") + option.label,
                        fontFamily = if (option == AppFont.ARCADE) PixelFont else FontFamily.Monospace,
                        textAlign = TextAlign.Center)
                }
            }
        } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = dismiss) { Text("Close") } })
}

@Composable
private fun choiceColors(selected: Boolean) = ButtonDefaults.outlinedButtonColors(
    containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
