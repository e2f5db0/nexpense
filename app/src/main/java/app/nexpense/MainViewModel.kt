// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.nexpense.crypto.GeneratedGroupKey
import app.nexpense.data.GroupRepository
import app.nexpense.data.GroupState
import app.nexpense.data.addPerson
import app.nexpense.data.delete
import app.nexpense.data.initialize
import app.nexpense.data.pay
import app.nexpense.data.request
import app.nexpense.data.requestCustom
import app.nexpense.data.saveSettings
import app.nexpense.nostr.Expense
import app.nexpense.ui.AccentColor
import app.nexpense.ui.AppFont
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val repository: GroupRepository, context: Context) : ViewModel() {
    private val preferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val appearancePreferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val selectedKey = MutableStateFlow<String?>(preferences.getString("group", null))
    private val syncingKeys = mutableSetOf<String>()
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val generatedBackup = MutableStateFlow<String?>(null)
    val accent = MutableStateFlow(appearancePreferences.getString("accent", null)
        ?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() } ?: AccentColor.PURPLE)
    val appFont = MutableStateFlow(appearancePreferences.getString("font", null)
        ?.let { runCatching { AppFont.valueOf(it) }.getOrNull() } ?: AppFont.ARCADE)
    val groups = repository.observeGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val group = selectedKey.flatMapLatest { key ->
        if (key == null) flowOf(null) else repository.observeGroup(key)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        selectedKey.value?.let(::selectGroup)
    }

    fun selectGroup(publicKey: String) {
        selectedKey.value = publicKey
        preferences.edit().putString("group", publicKey).apply()
        startSync(publicKey)
    }

    fun sync() {
        val key = selectedKey.value ?: return
        startSync(key)
    }

    fun clearGroupSelection() {
        selectedKey.value = null
        preferences.edit().remove("group").apply()
    }

    fun generateGroup(name: String, members: List<String>) = launch {
        val generated = GeneratedGroupKey.create()
        val key = repository.addDeviceGroup(generated.nsec, name)
        val actorId = repository.initialize(key, name, members).first()
        repository.selectActor(key, actorId)
        generatedBackup.value = generated.nsec
        selectGroup(key)
    }

    fun importGroup(nsec: String, name: String) = launch {
        selectGroup(repository.addDeviceGroup(nsec, name))
    }

    fun addAmber(publicKey: String, signerPackage: String, name: String) = launch {
        selectGroup(repository.addAmberGroup(publicKey, signerPackage, name))
    }

    fun selectActor(personId: String) = launchLocal { repository.selectActor(currentKey(), personId) }
    fun setAccent(value: AccentColor) {
        accent.value = value
        appearancePreferences.edit().putString("accent", value.name).apply()
    }
    fun setAppFont(value: AppFont) {
        appFont.value = value
        appearancePreferences.edit().putString("font", value.name).apply()
    }
    fun removeGroup(publicKey: String) = launchLocal {
        if (publicKey in syncingKeys) {
            error.value = "Wait for the current synchronization to finish"
        } else {
            repository.removeGroup(publicKey)
            if (selectedKey.value == publicKey) {
                val next = repository.groups().firstOrNull { it.publicKey != publicKey }
                if (next == null) clearGroupSelection() else selectGroup(next.publicKey)
            }
        }
    }
    fun addPerson(name: String) = launch { repository.addPerson(currentKey(), name) }
    fun renameGroup(name: String) = launch { repository.saveSettings(currentKey(), name) }

    fun request(description: String, totalMinor: Long, participants: Collection<String>) = launch {
        val actor = requireActor()
        repository.request(currentKey(), actor, description, totalMinor, participants)
    }

    fun requestCustom(expense: Expense) = launch { repository.requestCustom(currentKey(), expense) }
    fun pay(to: String, amountMinor: Long) = launch { repository.pay(currentKey(), requireActor(), to, amountMinor) }
    fun delete(eventId: String) = launch { repository.delete(currentKey(), eventId, requireActor()) }
    fun dismissError() { error.value = null }
    fun dismissBackup() { generatedBackup.value = null }

    private fun startSync(publicKey: String) {
        if (syncingKeys.add(publicKey)) {
            viewModelScope.launch {
                try { repository.synchronize(publicKey) }
                catch (t: Throwable) { error.value = t.message ?: "Synchronization failed" }
                finally { syncingKeys.remove(publicKey) }
            }
        }
    }

    private fun currentKey() = selectedKey.value ?: error("Select a group first")
    private fun requireActor() = group.value?.group?.actingMemberId ?: error("Select who you are acting as")

    private fun launch(operation: suspend () -> Unit) {
        if (busy.value) {
            error.value = "Wait for the current operation to finish"
            return
        }
        viewModelScope.launch {
            busy.value = true
            error.value = null
            try { operation() } catch (t: Throwable) { error.value = t.message ?: "Operation failed" }
            finally { busy.value = false }
        }
    }

    private fun launchLocal(operation: suspend () -> Unit) {
        viewModelScope.launch {
            try { operation() } catch (t: Throwable) { error.value = t.message ?: "Operation failed" }
        }
    }

    class Factory(private val repository: GroupRepository, private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST") return MainViewModel(repository, context) as T
        }
    }
}
