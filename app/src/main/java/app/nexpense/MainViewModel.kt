package app.nexpense

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.nexpense.crypto.GeneratedGroupKey
import app.nexpense.data.GroupEntity
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val repository: GroupRepository, context: Context) : ViewModel() {
    private val preferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val selectedKey = MutableStateFlow<String?>(preferences.getString("group", null))
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val generatedBackup = MutableStateFlow<String?>(null)
    val groups = repository.observeGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val group = selectedKey.flatMapLatest { key ->
        if (key == null) flowOf(null) else repository.observeGroup(key)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectGroup(publicKey: String) {
        selectedKey.value = publicKey
        preferences.edit().putString("group", publicKey).apply()
        viewModelScope.launch { runCatching { repository.synchronize(publicKey) } }
    }

    fun generateGroup(name: String, member: String) = launch {
        val generated = GeneratedGroupKey.create()
        val key = repository.addDeviceGroup(generated.nsec, name)
        val actorId = repository.initialize(key, name, member)
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

    fun selectActor(personId: String) = launch { repository.selectActor(currentKey(), personId) }
    fun addPerson(name: String) = launch { repository.addPerson(currentKey(), name) }
    fun renameGroup(name: String) = launch { repository.saveSettings(currentKey(), name) }
    fun sync() = launch { repository.synchronize(currentKey()) }

    fun request(description: String, totalMinor: Long, participants: Collection<String>) = launch {
        val actor = requireActor()
        repository.request(currentKey(), actor, description, totalMinor, participants)
    }

    fun requestCustom(expense: Expense) = launch { repository.requestCustom(currentKey(), expense) }
    fun pay(to: String, amountMinor: Long) = launch { repository.pay(currentKey(), requireActor(), to, amountMinor) }
    fun delete(eventId: String) = launch { repository.delete(currentKey(), eventId, requireActor()) }
    fun dismissError() { error.value = null }
    fun dismissBackup() { generatedBackup.value = null }

    private fun currentKey() = selectedKey.value ?: error("Select a group first")
    private fun requireActor() = group.value?.group?.actingMemberId ?: error("Select who you are acting as")

    private fun launch(operation: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            error.value = null
            try { operation() } catch (t: Throwable) { error.value = t.message ?: "Operation failed" }
            finally { busy.value = false }
        }
    }

    class Factory(private val repository: GroupRepository, private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST") return MainViewModel(repository, context) as T
        }
    }
}
