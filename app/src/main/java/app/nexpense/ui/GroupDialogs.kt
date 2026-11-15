// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import app.nexpense.MainViewModel
import app.nexpense.data.GroupEntity
import app.nexpense.data.GroupState

@Composable
fun ActorDialog(state: GroupState, viewModel: MainViewModel, actionsEnabled: Boolean, dismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Act as a group member") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.people.forEach { person ->
                TextButton(onClick = { viewModel.selectActor(person.personId); dismiss() }, Modifier.fillMaxWidth()) {
                    Text(if (person.personId == state.group.actingMemberId) "✓ ${person.name}" else person.name)
                }
            }
            if (!adding) {
                IconButton(onClick = { adding = true }, Modifier.align(Alignment.CenterHorizontally), enabled = actionsEnabled) {
                    Icon(Icons.Default.Add, "Add a friend")
                }
            } else {
                OutlinedTextField(newName, { newName = it }, Modifier.fillMaxWidth(),
                    label = { Text("New friend's name") }, singleLine = true, enabled = actionsEnabled)
                Button(onClick = {
                    viewModel.addPerson(newName)
                    newName = ""
                    adding = false
                }, Modifier.align(Alignment.CenterHorizontally), enabled = actionsEnabled && newName.isNotBlank()) {
                    Text("Add friend")
                }
            }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Close") } })
}

@Composable
fun GroupSwitcherDialog(groups: List<GroupEntity>, viewModel: MainViewModel, addGroup: () -> Unit, dismiss: () -> Unit) {
    val visible = groups.filterNot { it.hidden }
    val hidden = groups.filter { it.hidden }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Your groups") },
        text = { Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visible.forEach { group -> GroupRow(group, false, viewModel, dismiss) }
            if (hidden.isNotEmpty()) {
                Text("Hidden groups", color = NostrPurple)
                hidden.forEach { group -> GroupRow(group, true, viewModel, dismiss) }
            }
            Button(onClick = { dismiss(); addGroup() }, Modifier.align(Alignment.CenterHorizontally)) {
                Text("New group")
            }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Close") } })
}

@Composable
private fun GroupRow(group: GroupEntity, hidden: Boolean, viewModel: MainViewModel, dismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = {
            if (hidden) viewModel.setGroupHidden(group.publicKey, false)
            viewModel.selectGroup(group.publicKey)
            dismiss()
        }, Modifier.weight(1f)) {
            Text("${group.localName} · ${group.keySource.displayName()}")
        }
        IconButton(onClick = { viewModel.setGroupHidden(group.publicKey, !hidden) }) {
            Icon(if (hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                if (hidden) "Show ${group.localName}" else "Hide ${group.localName}")
        }
    }
}

private fun String.displayName() = when (this) {
    "DEVICE" -> "KEY ON DEVICE"
    "AMBER" -> "AMBER"
    else -> this
}

@Composable
fun SettingsDialog(state: GroupState, viewModel: MainViewModel, actionsEnabled: Boolean, dismiss: () -> Unit) {
    var name by remember(state.displayName) { mutableStateOf(state.displayName) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Group settings") },
        text = { Column {
            OutlinedTextField(name, { name = it }, label = { Text("Group name") }, singleLine = true,
                enabled = actionsEnabled)
            Text("Key source: ${state.group.keySource.displayName()}")
            Text("Group public key\n${state.group.publicKey}")
            Text("EUR only · NIP-44 encrypted")
        } },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) { viewModel.renameGroup(name); dismiss() } },
            enabled = actionsEnabled && name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
