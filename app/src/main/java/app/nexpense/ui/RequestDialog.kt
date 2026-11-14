package app.nexpense.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import app.nexpense.MainViewModel
import app.nexpense.data.GroupState
import app.nexpense.ledger.euroToMinor
import app.nexpense.ledger.minorToEuro
import app.nexpense.nostr.Expense
import app.nexpense.nostr.Share
import java.util.UUID

@Composable
fun RequestDialog(state: GroupState, viewModel: MainViewModel, dismiss: () -> Unit) {
    val actor = state.actingPerson ?: return
    var description by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(state.people.map { it.personId }.filter { it != actor.personId }.toSet()) }
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
            }) { Text("Confirm request") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Back") } })
    } else {
        AlertDialog(onDismissRequest = dismiss, title = { Text("Request · acting as ${actor.name}") },
            text = { Column(Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(description, { description = it }, label = { Text("Expense description") }, singleLine = true)
                if (!custom) OutlinedTextField(total, { total = it }, label = { Text("Total amount in EUR") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(custom, { custom = it })
                    Text("Enter a custom amount per person")
                }
                OutlinedButton(onClick = { selected = state.people.map { it.personId }.toSet() }) { Text("Everyone, including me") }
                state.people.forEach { person ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(person.personId in selected, { checked ->
                            selected = if (checked) selected + person.personId else selected - person.personId
                        })
                        Text(person.name, Modifier.weight(1f))
                    }
                    if (custom && person.personId in selected) OutlinedTextField(customAmounts[person.personId] ?: "",
                        { customAmounts = customAmounts + (person.personId to it) }, label = { Text("${person.name}'s share EUR") }, singleLine = true)
                }
                Text(if (actor.personId in selected) "Your own share is recorded but does not become a debt to yourself." else "Only selected people share this expense.")
            } },
            confirmButton = { Button(onClick = { confirm = true }, enabled = valid) { Text("Review") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
    }
}
