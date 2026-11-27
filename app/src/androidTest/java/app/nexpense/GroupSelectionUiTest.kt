// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nexpense.data.DeviceKeyVault
import app.nexpense.data.GroupEntity
import app.nexpense.data.GroupRepository
import app.nexpense.data.NexpenseDatabase
import app.nexpense.signer.DeviceGroupSigner
import app.nexpense.ui.NexpenseTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupSelectionUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val originalKey = preferences.getString("group", null)
    private val database = Room.inMemoryDatabaseBuilder(context, NexpenseDatabase::class.java).build()
    private val signer = DeviceGroupSigner(ByteArray(32) { 7 })
    private val repository = GroupRepository(context, database.dao(), DeviceKeyVault(context),
        enqueueOutbox = { _, _ -> }, signerOverride = { signer })
    private val store = ViewModelStore()

    @Before fun setUp() = runBlocking {
        preferences.edit().remove("group").commit()
        database.dao().saveGroup(GroupEntity("first", "Alpha", "DEVICE", actingMemberId = "actor", relayUrls = ""))
        database.dao().saveGroup(GroupEntity("last", "Zulu", "DEVICE", actingMemberId = "actor", relayUrls = ""))
    }

    @After fun tearDown() {
        compose.runOnIdle { store.clear() }
        database.close()
        preferences.edit().apply {
            if (originalKey == null) remove("group") else putString("group", originalKey)
        }.commit()
    }

    @Test fun savedGroupIsDisplayedAndActingMembersAreCleared() {
        preferences.edit().putString("group", "last").commit()
        val viewModel = MainViewModel(repository, context)
        store.put("main", viewModel)
        compose.setContent { NexpenseTheme { NexpenseApp(viewModel) } }
        compose.waitUntil(10_000) { viewModel.group.value?.group?.publicKey == "last" }
        compose.onNodeWithText("Zulu").assertIsDisplayed()
        assertEquals("last", preferences.getString("group", null))
        runBlocking {
            assertEquals(null, database.dao().group("first")?.actingMemberId)
            assertEquals(null, database.dao().group("last")?.actingMemberId)
        }
    }

    @Test fun unavailableSavedGroupFallsBackToFirstGroup() {
        preferences.edit().putString("group", "missing").commit()
        val viewModel = MainViewModel(repository, context)
        store.put("main", viewModel)
        compose.setContent { NexpenseTheme { NexpenseApp(viewModel) } }
        compose.waitUntil(10_000) { viewModel.group.value?.group?.publicKey == "first" }
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        assertEquals("first", preferences.getString("group", null))
    }

    @Test fun switchingGroupsSurvivesViewModelRecreation() {
        preferences.edit().putString("group", "last").commit()
        val first = MainViewModel(repository, context)
        store.put("first", first)
        val current = mutableStateOf(first)
        compose.setContent { NexpenseTheme { NexpenseApp(current.value) } }
        compose.waitUntil(10_000) { first.group.value?.group?.publicKey == "last" }
        compose.runOnIdle { first.selectGroup("first") }
        compose.waitUntil(10_000) { first.group.value?.group?.publicKey == "first" }
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.runOnIdle {
            val next = MainViewModel(repository, context)
            store.put("next", next)
            current.value = next
        }
        compose.waitUntil(10_000) { current.value.group.value?.group?.publicKey == "first" }
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        assertEquals("first", preferences.getString("group", null))
    }

    @Test fun removingSelectedGroupPersistsFallbackAndRemovingLastClearsIt() {
        preferences.edit().putString("group", "last").commit()
        val viewModel = MainViewModel(repository, context)
        store.put("main", viewModel)
        compose.setContent { NexpenseTheme { NexpenseApp(viewModel) } }
        compose.waitUntil(10_000) { viewModel.group.value?.group?.syncStatus == "Offline" }
        compose.runOnIdle { viewModel.removeGroup("last") }
        compose.waitUntil(10_000) { viewModel.group.value?.group?.publicKey == "first" &&
            viewModel.group.value?.group?.syncStatus == "Offline" }
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        assertEquals("first", preferences.getString("group", null))
        compose.runOnIdle { viewModel.removeGroup("first") }
        compose.waitUntil(10_000) { viewModel.selectedKey.value == null }
        assertEquals(null, preferences.getString("group", null))
        compose.onNodeWithText("Open encrypted group").assertIsDisplayed()
    }
}