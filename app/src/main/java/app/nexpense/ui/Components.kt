// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nexpense.ledger.minorToEuro
import app.nexpense.nostr.Person

fun memberLabel(person: Person): String = "${person.name} [${person.personId.take(4)}]"

@Composable
fun MemberName(person: Person, modifier: Modifier = Modifier, prefix: String = "",
    style: TextStyle = LocalTextStyle.current, fontWeight: FontWeight? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(prefix + person.name, Modifier.weight(1f, fill = false), style = style, fontWeight = fontWeight,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraSmall) {
            Text(person.personId.take(4), Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), maxLines = 1)
        }
    }
}

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
