// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nexpense.MainViewModel
import app.nexpense.data.GroupState
import app.nexpense.ledger.euroToMinor
import app.nexpense.ledger.minorToEuro
import app.nexpense.nostr.Expense
import app.nexpense.nostr.Share
import java.util.UUID

@Composable
fun RequestDialog(state: GroupState, viewModel: MainViewModel, actionsEnabled: Boolean, dismiss: () -> Unit) {
    val actor = state.actingPerson ?: return
    var description by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var custom by remember { mutableStateOf(false) }
    var customAmounts by remember { mutableStateOf(mapOf<String, String>()) }
    var confirm by remember { mutableStateOf(false) }
    val shares = selected.sorted().map { Share(it, euroToMinor(customAmounts[it] ?: "")) }
    val totalMinor = if (custom) shares.sumOf { it.amountMinor } else euroToMinor(total)
    val valid = description.isNotBlank() && selected.isNotEmpty() && totalMinor > 0 && (!custom || shares.all { it.amountMinor > 0 })

    if (confirm) {
        AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Create debt request?") },
            text = { Text("Acting as ${actor.name}\n\n${minorToEuro(totalMinor)} · ${description.trim()}\n\nShared with ${selected.size} participant(s), ${if (actor.personId in selected) "including" else "excluding"} you.") },
            confirmButton = { Button(onClick = {
                if (custom) viewModel.requestCustom(Expense(entryId = UUID.randomUUID().toString(), payerId = actor.personId,
                    description = description.trim(), totalMinor = totalMinor, incurredAt = System.currentTimeMillis() / 1000, shares = shares))
                else viewModel.request(description, totalMinor, selected)
                dismiss()
            }, enabled = actionsEnabled) { Text("Confirm request") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Back") } })
    } else {
        AlertDialog(onDismissRequest = dismiss, title = { Text("Request · acting as ${actor.name}") },
            text = { Column(Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(),
                    label = { Text("Expense description") }, singleLine = true, enabled = actionsEnabled)
                if (!custom) OutlinedTextField(total, { total = it }, Modifier.fillMaxWidth(),
                    label = { Text("Total amount in EUR") }, singleLine = true, enabled = actionsEnabled)
                Row(Modifier.fillMaxWidth().toggleable(custom, enabled = actionsEnabled, role = Role.Checkbox) { custom = it },
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(custom, null, enabled = actionsEnabled)
                    Text("Custom amounts", Modifier.weight(1f))
                }
                OutlinedButton(onClick = { selected = state.people.map { it.personId }.toSet() },
                    Modifier.fillMaxWidth(), enabled = actionsEnabled,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("Everyone, including me", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                state.people.forEach { person ->
                    Row(Modifier.fillMaxWidth().toggleable(
                        value = person.personId in selected,
                        enabled = actionsEnabled,
                        role = Role.Checkbox,
                        onValueChange = { checked -> selected = if (checked) selected + person.personId else selected - person.personId },
                    ).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(person.personId in selected, null, enabled = actionsEnabled)
                        Text(person.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (custom && person.personId in selected) OutlinedTextField(customAmounts[person.personId] ?: "",
                        { customAmounts = customAmounts + (person.personId to it) }, Modifier.fillMaxWidth(),
                        label = { Text("${person.name}'s share EUR") },
                        singleLine = true, enabled = actionsEnabled)
                }
                Text(if (actor.personId in selected) "Your own share is recorded but does not become a debt to yourself." else "Only selected people share this expense.")
            } },
            confirmButton = { Button(onClick = { confirm = true }, enabled = valid && actionsEnabled) { Text("Review") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
    }
}
