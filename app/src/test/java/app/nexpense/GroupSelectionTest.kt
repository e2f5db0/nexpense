// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense

import app.nexpense.data.GroupEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSelectionTest {
    private val groups = listOf(GroupEntity("first", "Alpha", "DEVICE"), GroupEntity("last", "Zulu", "DEVICE"))
    private var saved: String? = null
    private val synced = mutableListOf<String>()

    private fun selection(savedKey: String?) = GroupSelection(savedKey, { saved = it }, { synced += it })

    @Test fun restoresSavedGroupInsteadOfFirstGroup() = runTest {
        val selection = selection("last")
        selection.initialize { groups }
        assertEquals("last", selection.key.value)
        assertEquals("last", saved)
        assertEquals(listOf("last"), synced)
    }

    @Test fun missingOrAbsentSavedGroupFallsBackToFirstGroup() = runTest {
        for (savedKey in listOf(null, "missing")) {
            val selection = selection(savedKey)
            selection.initialize { groups }
            assertEquals("first", selection.key.value)
            assertEquals("first", saved)
        }
        assertEquals(listOf("first", "first"), synced)
    }

    @Test fun emptyListClearsStaleSelectionWithoutSyncing() = runTest {
        saved = "missing"
        val selection = selection(saved)
        selection.initialize { emptyList() }
        assertNull(selection.key.value)
        assertNull(saved)
        assertEquals(emptyList<String>(), synced)
    }

    @Test fun selectionDuringLoadIsNotOverwrittenByStartup() = runTest {
        val loaded = CompletableDeferred<List<GroupEntity>>()
        val selection = selection("first")
        val initialization = launch { selection.initialize { loaded.await() } }
        runCurrent()
        selection.select("last")
        loaded.complete(groups)
        initialization.join()
        assertEquals("last", selection.key.value)
        assertEquals("last", saved)
        assertEquals(listOf("last"), synced)
    }

    @Test fun clearingDuringLoadIsNotOverwrittenByStartup() = runTest {
        val loaded = CompletableDeferred<List<GroupEntity>>()
        val selection = selection("first")
        val initialization = launch { selection.initialize { loaded.await() } }
        runCurrent()
        selection.clear()
        loaded.complete(groups)
        initialization.join()
        assertNull(selection.key.value)
        assertNull(saved)
        assertEquals(emptyList<String>(), synced)
    }

    @Test fun changingSelectionPersistsAndRestoresOnNextLaunch() = runTest {
        val firstLaunch = selection("last")
        firstLaunch.initialize { groups }
        firstLaunch.select("first")
        val nextLaunch = selection(saved)
        nextLaunch.initialize { groups }
        assertEquals("first", nextLaunch.key.value)
        assertEquals("first", saved)
        assertEquals(listOf("last", "first", "first"), synced)
    }
}