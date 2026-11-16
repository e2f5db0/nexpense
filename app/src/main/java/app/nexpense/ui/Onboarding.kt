// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    busy: Boolean,
    showCancel: Boolean,
    onCancel: () -> Unit,
    onGenerate: (String, List<String>) -> Unit,
    onImport: (String, String) -> Unit,
) {
    var tab by rememberSaveable(showCancel) { mutableIntStateOf(if (showCancel) 0 else 1) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var memberName by rememberSaveable { mutableStateOf("") }
    var additionalMembers by rememberSaveable { mutableStateOf(listOf<String>()) }
    var nsec by androidx.compose.runtime.remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 24.dp, top = 48.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(buildAnnotatedString {
            append("N")
            withStyle(SpanStyle(color = LogoYellow)) { append("€") }
            append("xpense")
        }, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text("Private shared expenses for trusted groups.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("The nsec is the group. Keep it secret: everyone who has it can read and edit every expense.",
            style = MaterialTheme.typography.bodyMedium)
        if (showCancel) TextButton(onClick = onCancel, Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("New group", "Enter group key").forEachIndexed { index, label ->
                OutlinedButton(onClick = { tab = index }, Modifier.fillMaxWidth(), enabled = !busy,
                    colors = if (tab == index) ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant) else ButtonDefaults.outlinedButtonColors()) {
                    Text((if (tab == index) "✓ " else "") + label)
                }
            }
        }
        when (tab) {
            0 -> {
                OutlinedTextField(groupName, { groupName = it }, Modifier.fillMaxWidth(), label = { Text("Group name") },
                    singleLine = true, enabled = !busy)
                OutlinedTextField(memberName, { memberName = it }, Modifier.fillMaxWidth(), label = { Text("Your name in the group") },
                    singleLine = true, enabled = !busy)
                additionalMembers.forEachIndexed { index, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(name, { value ->
                            additionalMembers = additionalMembers.mapIndexed { i, old -> if (i == index) value else old }
                        }, Modifier.weight(1f), label = { Text("Friend ${index + 1}'s name") }, singleLine = true, enabled = !busy)
                        IconButton(onClick = { additionalMembers = additionalMembers.filterIndexed { i, _ -> i != index } },
                            enabled = !busy) { Icon(Icons.Default.Close, "Remove friend ${index + 1}") }
                    }
                }
                OutlinedButton(onClick = { additionalMembers = additionalMembers + "" }, enabled = !busy) {
                    Icon(Icons.Default.Add, null)
                    Text("Add another member")
                }
                val members = listOf(memberName) + additionalMembers
                val validMembers = members.all { it.isNotBlank() } &&
                    members.map { it.trim().lowercase() }.distinct().size == members.size
                Button(onClick = { onGenerate(groupName, members.map(String::trim)) }, Modifier.fillMaxWidth(),
                    enabled = !busy && groupName.isNotBlank() && validMembers) { Text("Generate group key") }
                Text("Back up the generated nsec before sharing expenses. You can share it privately with trusted friends later.",
                    style = MaterialTheme.typography.bodySmall)
            }
            1 -> {
                OutlinedTextField(nsec, { nsec = it }, Modifier.fillMaxWidth(),
                    label = { Text("nsec1… or 64-character hex key") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy)
                OutlinedTextField(groupName, { groupName = it }, Modifier.fillMaxWidth(),
                    label = { Text("Local group name (optional)") }, singleLine = true, enabled = !busy)
                Button(onClick = { if (nsec.isNotBlank()) onImport(nsec, groupName) }, Modifier.fillMaxWidth(),
                    enabled = !busy) { Text("Open encrypted group") }
            }
        }
        if (busy) NostrichLoading(Modifier.align(Alignment.CenterHorizontally), size = 48.dp)
    }
}
