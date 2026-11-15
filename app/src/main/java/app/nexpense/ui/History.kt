// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nexpense.MainViewModel
import app.nexpense.data.GroupState
import app.nexpense.ledger.minorToEuro
import app.nexpense.nostr.Expense
import app.nexpense.nostr.LedgerEvent
import app.nexpense.nostr.Person
import app.nexpense.nostr.Settlement

@Composable
fun ActivityDialog(state: GroupState, viewModel: MainViewModel, actionsEnabled: Boolean, dismiss: () -> Unit) {
    var deleting by remember { mutableStateOf<LedgerEvent?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("All activity") },
        text = { LazyColumn(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.ledger.entries, key = { it.event.id }) { ledgerEvent ->
                val entry = ledgerEvent.entry
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            when (entry) {
                                is Expense -> {
                                    Text(entry.description)
                                    Text("${minorToEuro(entry.totalMinor)} · paid by ${state.name(entry.payerId)}")
                                }
                                is Settlement -> {
                                    Text("Cash settlement")
                                    Text("${minorToEuro(entry.amountMinor)} · ${state.name(entry.fromPersonId)} → ${state.name(entry.toPersonId)}")
                                }
                            }
                        }
                        IconButton(onClick = { deleting = ledgerEvent }, enabled = actionsEnabled) {
                            Icon(Icons.Default.Delete, "Delete entry")
                        }
                    }
                }
            }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Close") } })
    DeleteConfirmation(deleting, viewModel, actionsEnabled) { deleting = null }
}

@Composable
fun PersonDetailDialog(state: GroupState, person: Person, viewModel: MainViewModel, actionsEnabled: Boolean, dismiss: () -> Unit) {
    val actor = state.actingPerson ?: return
    var deleting by remember { mutableStateOf<LedgerEvent?>(null) }
    val lines = state.ledger.linesBetween(actor.personId, person.personId)
    AlertDialog(onDismissRequest = dismiss, title = { Text("${actor.name} ↔ ${person.name}") },
        text = { LazyColumn(Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (lines.isEmpty()) item { Text("No outstanding debt lines.") }
            items(lines, key = { it.eventId }) { line ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(line.description, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(minorToEuro(line.remainingMinor), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (line.debtorId == actor.personId) "You owe ${person.name}" else "${person.name} owes you",
                                color = if (line.debtorId == actor.personId) MaterialTheme.colorScheme.error else Blue)
                        }
                        val target = state.ledger.entries.firstOrNull { it.event.id == line.eventId }
                        if (target != null) IconButton(onClick = { deleting = target }, enabled = actionsEnabled) {
                            Icon(Icons.Default.Delete, "Delete debt entry")
                        }
                    }
                }
            }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Close") } })
    DeleteConfirmation(deleting, viewModel, actionsEnabled) { deleting = null }
}

@Composable
private fun DeleteConfirmation(target: LedgerEvent?, viewModel: MainViewModel, actionsEnabled: Boolean, dismiss: () -> Unit) {
    if (target != null) AlertDialog(onDismissRequest = dismiss, title = { Text("Are you sure?") },
        text = { Text("Remove this ledger entry for everyone in the group? Balances will be recalculated. Deletion cannot erase copies held by other devices or servers.") },
        confirmButton = { TextButton(onClick = { viewModel.delete(target.event.id); dismiss() }, enabled = actionsEnabled) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

private fun GroupState.name(id: String) = people.firstOrNull { it.personId == id }?.name ?: "Unknown member"
