// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.nexpense.signer.AmberConnector
import app.nexpense.ui.AppearanceDialog
import app.nexpense.ui.DashboardScreen
import app.nexpense.ui.NexpenseTheme
import app.nexpense.ui.NostrichLoading
import app.nexpense.ui.OnboardingScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as NexpenseApplication).repository, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val accent by viewModel.accent.collectAsState()
            val appFont by viewModel.appFont.collectAsState()
            NexpenseTheme(accent, appFont) { Surface(Modifier.fillMaxSize()) { NexpenseApp(viewModel) } }
        }
    }
}

@Composable
fun NexpenseApp(viewModel: MainViewModel) {
    val groups by viewModel.groups.collectAsState()
    val group by viewModel.group.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val backup by viewModel.generatedBackup.collectAsState()
    val accent by viewModel.accent.collectAsState()
    val appFont by viewModel.appFont.collectAsState()
    var addingGroup by rememberSaveable { mutableStateOf(false) }
    var appearance by rememberSaveable { mutableStateOf(false) }
    val activeGroup = group
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

    LaunchedEffect(groups, addingGroup) {
        val current = group
        if (current == null && groups.isNotEmpty() && !addingGroup) {
            viewModel.selectGroup(groups.first().publicKey)
        }
    }

    if (activeGroup == null || addingGroup) {
        OnboardingScreen(
            busy = busy,
            showCancel = groups.isNotEmpty(),
            onCancel = { addingGroup = false },
            onGenerate = { name, members -> viewModel.generateGroup(name, members); addingGroup = false },
            onImport = { nsec, name -> viewModel.importGroup(nsec, name); addingGroup = false },
            onAmber = { amberLauncher.launch(AmberConnector.connectionIntent()) },
        )
    } else {
        DashboardScreen(viewModel = viewModel, state = activeGroup, groups = groups, busy = busy,
            onAppearance = { appearance = true }, onAddGroup = { addingGroup = true })
    }

    if (appearance) AppearanceDialog(accent, appFont, viewModel::setAccent, viewModel::setAppFont) { appearance = false }
    if (busy && activeGroup != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        NostrichLoading(size = 64.dp)
    }
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
