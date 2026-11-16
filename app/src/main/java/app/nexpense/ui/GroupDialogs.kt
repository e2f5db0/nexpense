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
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nexpense.MainViewModel
import app.nexpense.crypto.Npub
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
fun AddMemberDialog(viewModel: MainViewModel, busy: Boolean, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Add a new member") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
                label = { Text("Member name") }, singleLine = true, enabled = !busy)
            Text("The member is added to the shared group ledger and published with the group key.",
                style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(onClick = {
            viewModel.addPerson(name, selectAsActor = true)
            dismiss()
        }, enabled = !busy && name.isNotBlank()) { Text("Add member") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun GroupSwitcherDialog(groups: List<GroupEntity>, viewModel: MainViewModel, addGroup: () -> Unit,
    switchingOnly: Boolean = false, dismiss: () -> Unit) {
    var removing by remember { mutableStateOf<GroupEntity?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Your groups") },
        text = { Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { group -> GroupRow(group, viewModel, !switchingOnly, dismiss) { removing = group } }
            Button(onClick = { dismiss(); addGroup() }, Modifier.align(Alignment.CenterHorizontally), enabled = !switchingOnly) {
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
private fun GroupRow(group: GroupEntity, viewModel: MainViewModel, removeEnabled: Boolean, dismiss: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = {
            viewModel.selectGroup(group.publicKey)
            dismiss()
        }, Modifier.weight(1f)) {
            Text(group.localName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRemove, enabled = removeEnabled) {
            Icon(Icons.Default.Delete, "Remove ${group.localName}")
        }
    }
}

@Composable
fun SettingsDialog(state: GroupState, viewModel: MainViewModel, actionsEnabled: Boolean, dismiss: () -> Unit) {
    var name by remember(state.displayName) { mutableStateOf(state.displayName) }
    val npub = remember(state.group.publicKey) { Npub.encode(state.group.publicKey) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(onDismissRequest = dismiss, title = { Text("Group settings") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Group name") }, singleLine = true,
                enabled = actionsEnabled)
            Text("Group public key (npub)")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(npub, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { clipboard.setText(AnnotatedString(npub)) }) {
                    Icon(Icons.Default.ContentCopy, "Copy npub")
                }
            }
        } },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) { viewModel.renameGroup(name); dismiss() } },
            enabled = actionsEnabled && name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
