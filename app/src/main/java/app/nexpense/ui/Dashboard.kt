// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nexpense.MainViewModel
import app.nexpense.data.GroupEntity
import app.nexpense.data.GroupState
import app.nexpense.ledger.minorToEuro
import app.nexpense.nostr.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, state: GroupState, groups: List<GroupEntity>, onAddGroup: () -> Unit) {
    var actors by remember { mutableStateOf(false) }
    var groupSwitcher by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var pay by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf(false) }
    var activity by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Person?>(null) }
    val actor = state.actingPerson

    Scaffold(topBar = {
        TopAppBar(title = { Text(state.displayName, Modifier.clickable { groupSwitcher = true }, fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = viewModel::sync) { Icon(Icons.Default.Refresh, "Synchronize") }
                IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "Group settings") }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AssistChip(onClick = { actors = true }, label = { Text(actor?.let { "Acting as ${it.name}" } ?: "Select who you are") })
            Text("${state.group.syncStatus} · ${state.pendingCount} pending", style = MaterialTheme.typography.bodySmall)
            if (actor == null) {
                Text("Who are you acting as?", style = MaterialTheme.typography.headlineSmall)
                state.people.forEach { person -> Button(onClick = { viewModel.selectActor(person.personId) }, Modifier.fillMaxWidth()) { Text(person.name) } }
                Button(onClick = { actors = true }) { Text("Add a friend") }
            } else {
                val totals = state.ledger.totals(actor.personId)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmountCard("You owe", totals.youOweMinor, Modifier.weight(1f), MaterialTheme.colorScheme.error)
                    AmountCard("You are owed", totals.owedToYouMinor, Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { pay = true }, Modifier.weight(1f)) { Text("Pay") }
                    Button(onClick = { request = true }, Modifier.weight(1f)) { Text("Request") }
                }
                Text("Activity", Modifier.clickable { activity = true }, color = MaterialTheme.colorScheme.primary)
                Text("People", style = MaterialTheme.typography.titleLarge)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.people.filter { it.personId != actor.personId }, key = { it.personId }) { person ->
                        val balance = state.ledger.balance(actor.personId, person.personId)
                        Card(Modifier.fillMaxWidth().clickable { detail = person }) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(person.name, fontWeight = FontWeight.SemiBold)
                                    Text(if (balance > 0) "You owe ${person.name}" else if (balance < 0) "${person.name} owes you" else "Settled")
                                }
                                Text(minorToEuro(kotlin.math.abs(balance)), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    if (actors) ActorDialog(state, viewModel) { actors = false }
    if (groupSwitcher) GroupSwitcherDialog(groups, viewModel, onAddGroup) { groupSwitcher = false }
    if (settings) SettingsDialog(state, viewModel) { settings = false }
    if (pay) PayDialog(state, viewModel) { pay = false }
    if (request) RequestDialog(state, viewModel) { request = false }
    if (activity) ActivityDialog(state, viewModel) { activity = false }
    detail?.let { PersonDetailDialog(state, it, viewModel) { detail = null } }
}
