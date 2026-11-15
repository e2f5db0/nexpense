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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
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
    var removing by remember { mutableStateOf<GroupEntity?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Your groups") },
        text = { Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { group -> GroupRow(group, viewModel, dismiss) { removing = group } }
            Button(onClick = { dismiss(); addGroup() }, Modifier.align(Alignment.CenterHorizontally)) {
                Text("New group")
            }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Close") } })
    removing?.let { group ->
        AlertDialog(onDismissRequest = { removing = null },
            title = { Text("Remove ${group.localName}?") },
            text = { Text("The group key and its cached events are deleted from this device, as if the group was never here. Anyone with the nsec can re-import the group later.") },
            confirmButton = { TextButton(onClick = {
                viewModel.removeGroup(group.publicKey)
                removing = null
                dismiss()
            }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } })
    }
}

@Composable
private fun GroupRow(group: GroupEntity, viewModel: MainViewModel, dismiss: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = {
            viewModel.selectGroup(group.publicKey)
            dismiss()
        }, Modifier.weight(1f)) {
            Text("${group.localName} · ${group.keySource.displayName()}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, "Remove ${group.localName}")
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
        text = { Column(Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Group name") }, singleLine = true,
                enabled = actionsEnabled)
            Text("Key source: ${state.group.keySource.displayName()}")
            Text("Group public key\n${state.group.publicKey}")
            Text("EUR only · NIP-44 encrypted")
        } },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) { viewModel.renameGroup(name); dismiss() } },
            enabled = actionsEnabled && name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
