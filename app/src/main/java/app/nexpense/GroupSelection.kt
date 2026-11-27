// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense

import app.nexpense.data.GroupEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class GroupSelection(
    private val savedKey: String?,
    private val persist: (String?) -> Unit,
    private val startSync: (String) -> Unit,
) {
    private val selectedKey = MutableStateFlow<String?>(null)
    val key = selectedKey.asStateFlow()
    private var selectionChanged = false

    suspend fun initialize(loadGroups: suspend () -> List<GroupEntity>) {
        val groups = loadGroups()
        // A selection made while the database was loading must take precedence.
        if (selectionChanged) return
        val initialKey = groups.firstOrNull { it.publicKey == savedKey }?.publicKey
            ?: groups.firstOrNull()?.publicKey
        if (initialKey == null) {
            if (savedKey != null) persist(null)
        } else {
            select(initialKey)
        }
    }

    fun select(publicKey: String) {
        selectionChanged = true
        selectedKey.value = publicKey
        persist(publicKey)
        startSync(publicKey)
    }

    fun clear() {
        selectionChanged = true
        selectedKey.value = null
        persist(null)
    }
}