// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nexpense.MainViewModel
import app.nexpense.R
import app.nexpense.data.GroupEntity
import app.nexpense.data.GroupState
import app.nexpense.ledger.minorToEuro
import app.nexpense.nostr.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel, state: GroupState, groups: List<GroupEntity>, busy: Boolean,
    onAppearance: () -> Unit, onAddGroup: () -> Unit) {
    var actors by remember { mutableStateOf(false) }
    var groupSwitcher by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var pay by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf(false) }
    var activity by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Person?>(null) }
    var addMember by remember { mutableStateOf(false) }
    val actor = state.actingPerson
    val waitingForPeople = actor == null && state.people.isEmpty()
    val actorSyncGate = actor == null && state.syncing
    val actionsEnabled = !busy && actor != null
    val accent = MaterialTheme.colorScheme.primary

    Scaffold(topBar = {
        CenterAlignedTopAppBar(navigationIcon = {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (state.syncing) NostrichLoading(size = 22.dp)
            }
        }, title = {
            Row(Modifier.clickable { groupSwitcher = true }, verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo_icon), "Nexpense logo",
                    Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(8.dp))
                Text(state.displayName, Modifier.weight(1f, fill = false), fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.ArrowDropDown, "Switch group")
            }
        }, actions = {
            IconButton(onClick = viewModel::sync, enabled = !busy && !state.syncing) {
                Icon(Icons.Default.Refresh, "Synchronize")
            }
            IconButton(onClick = { settings = true }, enabled = !actorSyncGate) {
                Icon(Icons.Default.Settings, "Group settings")
            }
            IconButton(onClick = onAppearance, enabled = !actorSyncGate) {
                Icon(Icons.Default.Palette, "Appearance")
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (actor != null) {
                Row(Modifier.fillMaxWidth().clickable { actors = true },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    NameAvatar(actor.name, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(actor.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.ArrowDropDown, "Choose group member")
                }
            }
            when {
                state.pendingCount > 0 -> Text("${state.pendingCount} ${if (state.pendingCount == 1) "change" else "changes"} waiting to sync",
                    style = MaterialTheme.typography.bodySmall)
                state.group.syncStatus == "Offline" -> Text("Offline · changes will sync later",
                    style = MaterialTheme.typography.bodySmall)
                state.group.syncStatus == "Sync failed" -> Text("Sync failed · try again",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (actor == null) {
                if (waitingForPeople) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        NostrichLoading(size = 64.dp)
                        if (state.syncing) {
                            Text("Loading group members…", textAlign = TextAlign.Center)
                        } else {
                            Text("No group members loaded yet. Sync to load an existing group, or add the first member below.",
                                textAlign = TextAlign.Center)
                            OutlinedButton(onClick = { addMember = true }, enabled = !busy) {
                                Icon(Icons.Default.Add, null)
                                Text("Add a new member")
                            }
                        }
                    }
                } else {
                    Text("Who are you acting as?", Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    state.people.forEach { person -> Button(onClick = { viewModel.selectActor(person.personId) }, Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NameAvatar(person.name, 24.dp)
                            Text(person.name)
                        }
                    } }
                }
            } else {
                val totals = state.ledger.totals(actor.personId)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmountCard("You owe", totals.youOweMinor, Modifier.weight(1f), MaterialTheme.colorScheme.error)
                    AmountCard("You are owed", totals.owedToYouMinor, Modifier.weight(1f), Blue)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { pay = true }, Modifier.weight(1f), enabled = actionsEnabled) { Text("Pay") }
                    Button(onClick = { request = true }, Modifier.weight(1f), enabled = actionsEnabled) { Text("Request") }
                }
                Text("Activity", Modifier.clickable { activity = true }, color = MaterialTheme.colorScheme.primary)
                Text("People", style = MaterialTheme.typography.titleLarge)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.people.filter { it.personId != actor.personId }, key = { it.personId }) { person ->
                        val balance = state.ledger.balance(actor.personId, person.personId)
                        val balanceColor = if (balance > 0) MaterialTheme.colorScheme.error else if (balance < 0) Blue
                        else MaterialTheme.colorScheme.onSurfaceVariant
                        Card(Modifier.fillMaxWidth().clickable { detail = person },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                NameAvatar(person.name, 28.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(person.name, fontWeight = FontWeight.SemiBold)
                                    Text(if (balance > 0) "You owe ${person.name}" else if (balance < 0) "${person.name} owes you" else "Settled",
                                        color = balanceColor)
                                }
                                Text(minorToEuro(kotlin.math.abs(balance)), fontWeight = FontWeight.Bold, color = balanceColor)
                            }
                        }
                    }
                }
            }
        }
    }
    if (actors) ActorDialog(state, viewModel, actionsEnabled) { actors = false }
    if (addMember) AddMemberDialog(viewModel, busy) { addMember = false }
    if (groupSwitcher) GroupSwitcherDialog(groups, viewModel, onAddGroup, actorSyncGate) { groupSwitcher = false }
    if (settings) SettingsDialog(state, viewModel, !busy && !actorSyncGate) { settings = false }
    if (pay) PayDialog(state, viewModel, actionsEnabled) { pay = false }
    if (request) RequestDialog(state, viewModel, actionsEnabled) { request = false }
    if (activity) ActivityDialog(state, viewModel, actionsEnabled) { activity = false }
    detail?.let { PersonDetailDialog(state, it, viewModel, actionsEnabled) { detail = null } }
}
