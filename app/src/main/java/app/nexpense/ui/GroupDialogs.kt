// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
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
import app.nexpense.data.EventBackup
import app.nexpense.data.GroupEntity
import java.text.DateFormat
import java.util.Date
import app.nexpense.data.GroupState

@Composable
fun ActorDialog(state: GroupState, viewModel: MainViewModel, actionsEnabled: Boolean, dismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Act as a group member") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.people.forEach { person ->
                TextButton(onClick = { viewModel.selectActor(person.personId); dismiss() }, Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NameAvatar(person.name, 24.dp)
                        Text(if (person.personId == state.group.actingMemberId) "✓ ${person.name}" else person.name)
                    }
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
            text = { Text("The group key and all cached events will be deleted from this device. The nsec alone cannot recover events lost by relays. Export a backup first and store the nsec separately.") },
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
    val durability by viewModel.durability.collectAsState()
    var relayUrl by remember { mutableStateOf("") }
    val publicKey = state.group.publicKey
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(EventBackup.MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportBackup(uri, publicKey)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restoreBackup(uri, publicKey)
    }
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
            durability?.takeIf { it.groupPublicKey == publicKey }?.let { info ->
                Text(info.headline, style = MaterialTheme.typography.titleMedium)
                Text("Relay acknowledgments and verification do not guarantee long-term retention.", style = MaterialTheme.typography.bodySmall)
                info.relays.forEach { relay ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(relay.url, style = MaterialTheme.typography.bodySmall)
                            Text("${relay.label} · ${relay.verified} verified · ${relay.acknowledged} acknowledged · ${relay.pending + relay.unknown} waiting",
                                style = MaterialTheme.typography.bodySmall)
                            relay.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        }
                        IconButton(onClick = { viewModel.removeRelay(relay.url) }, enabled = actionsEnabled && info.relays.size > 1) {
                            Icon(Icons.Default.Delete, "Remove relay ${relay.url}")
                        }
                    }
                }
                OutlinedTextField(relayUrl, { relayUrl = it }, Modifier.fillMaxWidth(), label = { Text("Add wss:// relay") },
                    singleLine = true, enabled = actionsEnabled)
                TextButton(onClick = { viewModel.addRelay(relayUrl); relayUrl = "" }, enabled = actionsEnabled && relayUrl.isNotBlank()) {
                    Text("Add relay")
                }
                Text("More relays use more battery and expose activity metadata to more operators.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::verifyAndRepair, enabled = actionsEnabled && !state.syncing) { Text("Verify and repair now") }
                val backupDate = if (info.lastBackupAt == 0L) "Never" else DateFormat.getDateTimeInstance().format(Date(info.lastBackupAt))
                Text("Last backup: $backupDate · Export regularly, especially after changes", style = MaterialTheme.typography.bodySmall)
            }
            Text("Backup contains encrypted events and visible Nostr metadata, not the nsec. Keep both safely, separately.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { export.launch("nexpense-${publicKey.take(8)}.${EventBackup.FILE_EXTENSION}") }, enabled = actionsEnabled) {
                Text("Export encrypted backup")
            }
            TextButton(onClick = { restore.launch(arrayOf("*/*")) }, enabled = actionsEnabled) { Text("Restore encrypted backup") }
        } },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) { viewModel.renameGroup(name); dismiss() } },
            enabled = actionsEnabled && name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
