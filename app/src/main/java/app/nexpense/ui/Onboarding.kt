package app.nexpense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    busy: Boolean,
    showCancel: Boolean,
    onCancel: () -> Unit,
    onGenerate: (String, String) -> Unit,
    onImport: (String, String) -> Unit,
    onAmber: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var memberName by rememberSaveable { mutableStateOf("") }
    var nsec by androidx.compose.runtime.remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (showCancel) TextButton(onClick = onCancel) { Text("Cancel") }
        Text("Nexpense", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = NostrPurple)
        Text("Private shared expenses for trusted groups.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("The nsec is the group. Keep it secret: everyone who has it can read and edit every expense.",
            style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("New group", "Enter nsec", "Amber").forEachIndexed { index, label ->
                OutlinedButton(onClick = { tab = index }, colors = if (tab == index) ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant) else ButtonDefaults.outlinedButtonColors()) {
                    Text(label)
                }
            }
        }
        when (tab) {
            0 -> {
                OutlinedTextField(groupName, { groupName = it }, Modifier.fillMaxWidth(), label = { Text("Group name") }, singleLine = true)
                OutlinedTextField(memberName, { memberName = it }, Modifier.fillMaxWidth(), label = { Text("Your name in the group") }, singleLine = true)
                Button(onClick = { if (groupName.isNotBlank() && memberName.isNotBlank()) onGenerate(groupName, memberName) },
                    Modifier.fillMaxWidth(), enabled = !busy) { Text("Generate group key") }
                Text("Back up the generated nsec before sharing expenses. You can share it privately with trusted friends later.",
                    style = MaterialTheme.typography.bodySmall)
            }
            1 -> {
                OutlinedTextField(nsec, { nsec = it.trim() }, Modifier.fillMaxWidth(), label = { Text("nsec1…") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(groupName, { groupName = it }, Modifier.fillMaxWidth(),
                    label = { Text("Local group name (optional)") }, singleLine = true)
                Button(onClick = { if (nsec.isNotBlank()) onImport(nsec, groupName) }, Modifier.fillMaxWidth(),
                    enabled = !busy) { Text("Open encrypted group") }
            }
            else -> {
                Text("Select the group key already stored in Amber. Nexpense never receives its nsec; Amber signs and encrypts through NIP-55.")
                Button(onClick = onAmber, Modifier.fillMaxWidth(), enabled = !busy) { Text("Connect Amber") }
            }
        }
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
    }
}
