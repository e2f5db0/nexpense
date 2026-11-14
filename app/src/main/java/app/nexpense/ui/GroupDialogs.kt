package app.nexpense.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.nexpense.MainViewModel
import app.nexpense.data.GroupEntity
import app.nexpense.data.GroupState

@Composable
fun ActorDialog(state: GroupState, viewModel: MainViewModel, dismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Act as a group member") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            state.people.forEach { person ->
                TextButton(onClick = { viewModel.selectActor(person.personId); dismiss() }, Modifier.fillMaxWidth()) {
                    Text(if (person.personId == state.group.actingMemberId) "✓ ${person.name}" else person.name)
                }
            }
            OutlinedTextField(newName, { newName = it }, label = { Text("New friend's name") }, singleLine = true)
            Button(onClick = { if (newName.isNotBlank()) { viewModel.addPerson(newName); newName = "" } }) { Text("Add friend") }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Close") } })
}

@Composable
fun GroupSwitcherDialog(groups: List<GroupEntity>, viewModel: MainViewModel, addGroup: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Your groups") },
        text = { Column {
            groups.forEach { group -> TextButton(onClick = { viewModel.selectGroup(group.publicKey); dismiss() }, Modifier.fillMaxWidth()) {
                Text("${group.localName} · ${group.keySource}")
            } }
            Button(onClick = { dismiss(); addGroup() }) { Text("Add or generate group") }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Close") } })
}

@Composable
fun SettingsDialog(state: GroupState, viewModel: MainViewModel, dismiss: () -> Unit) {
    var name by remember(state.displayName) { mutableStateOf(state.displayName) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Group settings") },
        text = { Column {
            OutlinedTextField(name, { name = it }, label = { Text("Group name") }, singleLine = true)
            Text("Key source: ${state.group.keySource}")
            Text("Group public key\n${state.group.publicKey}")
            Text("EUR only · NIP-44 encrypted · Nostr relays")
        } },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) { viewModel.renameGroup(name); dismiss() } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
