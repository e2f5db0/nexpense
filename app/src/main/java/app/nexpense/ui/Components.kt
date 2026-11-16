// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nexpense.crypto.sha256
import app.nexpense.ledger.minorToEuro

@Composable
fun AmountCard(label: String, amount: Long, modifier: Modifier, color: Color) {
    Card(modifier, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(minorToEuro(amount), style = MaterialTheme.typography.headlineSmall, color = color,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val avatarPalette = listOf(
    Color(0xFFFB923C), Color(0xFF60A5FA), Color(0xFF8B5CF6), Color(0xFFF87171),
    Color(0xFF4ADE80), Color(0xFFFACC15), Color(0xFFA3E635), Color(0xFF2DD4BF),
    Color(0xFFF472B6), Color(0xFF38BDF8),
)

// Deterministic identicon-style avatar: a 5x5 grid mirrored left-to-right whose
// colors come from a SHA-256 hash of the name, so each member is instantly
// recognizable without any stored image.
@Composable
fun NameAvatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    val (background, foreground, cells) = remember(name) { avatarSpec(name) }
    Box(modifier.size(size).clip(CircleShape).background(background)) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = minOf(this.size.width, this.size.height) / 5f
            cells.forEachIndexed { index, filled ->
                if (!filled) return@forEachIndexed
                val column = index % 3
                val row = index / 3
                drawRect(color = foreground, topLeft = Offset(column * cell, row * cell), size = Size(cell, cell))
                if (column < 2) {
                    drawRect(color = foreground, topLeft = Offset((4 - column) * cell, row * cell), size = Size(cell, cell))
                }
            }
        }
    }
}

private fun avatarSpec(name: String): Triple<Color, Color, List<Boolean>> {
    val hash = sha256(name.trim().lowercase().toByteArray(Charsets.UTF_8))
    val background = avatarPalette[(hash[0].toInt() and 0xFF) % avatarPalette.size]
    var foreground = avatarPalette[(hash[1].toInt() and 0xFF) % avatarPalette.size]
    if (foreground == background) foreground = avatarPalette[(avatarPalette.indexOf(foreground) + 1) % avatarPalette.size]
    val cells = (0 until 15).map { bit -> ((hash[bit / 8 + 2].toInt() shr (bit % 8)) and 1) == 1 }
    return Triple(background, foreground, cells)
}
