package app.nexpense

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import app.nexpense.signer.AmberConnector
import app.nexpense.ui.DashboardScreen
import app.nexpense.ui.NexpenseTheme
import app.nexpense.ui.OnboardingScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as NexpenseApplication).repository, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent { NexpenseTheme { Surface(Modifier.fillMaxSize()) { NexpenseApp(viewModel) } } }
    }
}

@Composable
fun NexpenseApp(viewModel: MainViewModel) {
    val groups by viewModel.groups.collectAsState()
    val group by viewModel.group.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val backup by viewModel.generatedBackup.collectAsState()
    var addingGroup by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val amberLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.getBooleanExtra("rejected", false) != true) {
            val publicKey = result.data?.getStringExtra("result")
            val signerPackage = result.data?.getStringExtra("package")
            if (publicKey != null && signerPackage != null) {
                viewModel.addAmber(publicKey, signerPackage, "Amber ${publicKey.take(6)}")
                addingGroup = false
            }
        }
    }

    LaunchedEffect(groups) {
        if (group == null && groups.isNotEmpty() && !addingGroup) viewModel.selectGroup(groups.first().publicKey)
    }

    if (group == null || addingGroup) {
        OnboardingScreen(
            busy = busy,
            showCancel = groups.isNotEmpty(),
            onCancel = { addingGroup = false },
            onGenerate = { name, member -> viewModel.generateGroup(name, member); addingGroup = false },
            onImport = { nsec, name -> viewModel.importGroup(nsec, name); addingGroup = false },
            onAmber = { amberLauncher.launch(AmberConnector.connectionIntent()) },
        )
    } else {
        DashboardScreen(viewModel = viewModel, state = group!!, groups = groups, onAddGroup = { addingGroup = true })
    }

    if (busy && group != null) CircularProgressIndicator()
    error?.let {
        AlertDialog(onDismissRequest = viewModel::dismissError, title = { Text("Something went wrong") },
            text = { Text(it) }, confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } })
    }
    backup?.let { nsec ->
        AlertDialog(onDismissRequest = {}, title = { Text("Back up this group key") },
            text = { Text("Everyone in this group needs this nsec. Anyone with it can read and change the ledger. Store it privately.\n\n$nsec") },
            confirmButton = {
                Button(onClick = { clipboard.setText(AnnotatedString(nsec)) }) { Text("Copy nsec") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissBackup) { Text("I have backed it up") } })
    }
}
