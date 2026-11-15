// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NexpenseDatabase::class.java)

    @Test
    fun migrationFromVersion1PreservesExistingData() {
        helper.createDatabase(TEST_DATABASE_V1, 1).apply {
            execSQL("""
                INSERT INTO groups (
                    publicKey, localName, keySource, signerPackage, actingMemberId,
                    relayUrls, lastSyncedAt, syncCursor, syncStatus
                ) VALUES (
                    'group-key', 'Weekend', 'DEVICE', NULL, 'member-1',
                    'wss://example.com', 123, 456, 'Synced'
                )
            """.trimIndent())
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE_V1, 3, true,
            NexpenseDatabase.MIGRATION_1_2, NexpenseDatabase.MIGRATION_2_3).apply {
            query("SELECT localName, actingMemberId, lastSyncedAt, syncCursor FROM groups").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Weekend", cursor.getString(0))
                assertEquals("member-1", cursor.getString(1))
                assertEquals(123L, cursor.getLong(2))
                assertEquals(456L, cursor.getLong(3))
            }
            close()
        }
    }

    @Test
    fun migrationFromVersion2RemovesHiddenFlag() {
        helper.createDatabase(TEST_DATABASE_V2, 2).apply {
            execSQL("""
                INSERT INTO groups (
                    publicKey, localName, keySource, signerPackage, actingMemberId,
                    relayUrls, lastSyncedAt, syncCursor, syncStatus, hidden
                ) VALUES (
                    'group-key', 'Weekend', 'DEVICE', NULL, 'member-1',
                    'wss://example.com', 123, 456, 'Synced', 1
                )
            """.trimIndent())
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE_V2, 3, true, NexpenseDatabase.MIGRATION_2_3).apply {
            query("SELECT localName, actingMemberId, lastSyncedAt, syncCursor FROM groups").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Weekend", cursor.getString(0))
                assertEquals("member-1", cursor.getString(1))
                assertEquals(123L, cursor.getLong(2))
                assertEquals(456L, cursor.getLong(3))
            }
            query("PRAGMA table_info(groups)").use { cursor ->
                val columns = mutableListOf<String>()
                while (cursor.moveToNext()) columns.add(cursor.getString(1))
                assertFalse(columns.contains("hidden"))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DATABASE_V1 = "migration-test-v1"
        const val TEST_DATABASE_V2 = "migration-test-v2"
    }
}
