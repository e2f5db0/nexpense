// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nexpense.MainViewModel
import app.nexpense.data.GroupState
import app.nexpense.ledger.euroToMinor
import app.nexpense.ledger.minorToEuro
import java.math.BigDecimal

@Composable
fun PayDialog(state: GroupState, viewModel: MainViewModel, actionsEnabled: Boolean, dismiss: () -> Unit) {
    val actor = state.actingPerson ?: return
    val creditors = state.people.filter { state.ledger.balance(actor.personId, it.personId) > 0 }
    var selectedId by remember { mutableStateOf(creditors.firstOrNull()?.personId) }
    var amount by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val selected = creditors.firstOrNull { it.personId == selectedId }
    val balance = selected?.let { state.ledger.balance(actor.personId, it.personId) } ?: 0
    val amountMinor = euroToMinor(amount)

    if (confirm && selected != null) {
        AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Record cash payment?") },
            text = { Text("Acting as ${actor.name}\n\nRecord ${minorToEuro(amountMinor)} paid in cash to ${selected.name}. This only updates shared bookkeeping.") },
            confirmButton = { Button(onClick = { viewModel.pay(selected.personId, amountMinor); dismiss() },
                enabled = actionsEnabled) { Text("Confirm payment") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Back") } })
    } else {
        AlertDialog(onDismissRequest = dismiss, title = { Text("Pay · acting as ${actor.name}") },
            text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (creditors.isEmpty()) Text("You have no outstanding debts.")
                creditors.forEach { person ->
                    OutlinedButton(onClick = { selectedId = person.personId; amount = "" }, Modifier.fillMaxWidth(),
                        enabled = actionsEnabled) {
                        Text("${if (selectedId == person.personId) "✓ " else ""}${person.name} · ${minorToEuro(state.ledger.balance(actor.personId, person.personId))}",
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (selected != null) {
                    OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Amount in EUR") },
                        singleLine = true, enabled = actionsEnabled)
                    TextButton(onClick = { amount = BigDecimal(balance).movePointLeft(2).toPlainString() },
                        enabled = actionsEnabled) { Text("Pay in full") }
                }
            } },
            confirmButton = { Button(onClick = { confirm = true },
                enabled = actionsEnabled && selected != null && amountMinor > 0 && amountMinor <= balance) { Text("Review") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
    }
}
