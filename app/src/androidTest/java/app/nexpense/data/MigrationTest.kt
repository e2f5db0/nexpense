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

    @Test
    fun migrationsFromVersions1And2ReachCurrentSchema() {
        for (version in 1..2) {
            val database = "migration-to-v4-$version"
            helper.createDatabase(database, version).apply {
                execSQL("INSERT INTO groups (publicKey, localName, keySource, relayUrls, lastSyncedAt, syncCursor, syncStatus) VALUES ('group-key', 'Weekend', 'DEVICE', 'wss://example.com', 123, 456, 'Synced')")
                close()
            }
            val migrations = if (version == 1) arrayOf(NexpenseDatabase.MIGRATION_1_2,
                NexpenseDatabase.MIGRATION_2_3, NexpenseDatabase.MIGRATION_3_4)
            else arrayOf(NexpenseDatabase.MIGRATION_2_3, NexpenseDatabase.MIGRATION_3_4)
            helper.runMigrationsAndValidate(database, 4, true, *migrations).apply {
                query("SELECT localName, syncCursor, lastBackupAt FROM groups").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Weekend", cursor.getString(0))
                    assertEquals(456L, cursor.getLong(1))
                    assertEquals(0L, cursor.getLong(2))
                }
                close()
            }
        }
    }

    @Test
    fun migrationFromVersion3PreservesPendingEventsAndStartsRelayCursorsFromZero() {
        val database = "migration-test-v3"
        helper.createDatabase(database, 3).apply {
            execSQL("INSERT INTO groups (publicKey, localName, keySource, relayUrls, lastSyncedAt, syncCursor, syncStatus) VALUES ('group-key', 'Weekend', 'DEVICE', 'wss://example.com', 123, 456, 'Synced')")
            execSQL("INSERT INTO events (groupPublicKey, id, pubkey, createdAt, kind, tagsJson, content, sig, plaintext, pending) VALUES ('group-key', 'event-id', 'group-key', 10, 78, '[]', 'ciphertext', 'signature', 'plaintext', 1)")
            close()
        }
        helper.runMigrationsAndValidate(database, 4, true, NexpenseDatabase.MIGRATION_3_4).apply {
            query("SELECT lastBackupAt, syncCursor FROM groups").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
                assertEquals(456L, cursor.getLong(1))
            }
            query("SELECT pending, content FROM events").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals("ciphertext", cursor.getString(1))
            }
            query("SELECT COUNT(*) FROM relay_states").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            close()
        }
    }

    private companion object {
        const val TEST_DATABASE_V1 = "migration-test-v1"
        const val TEST_DATABASE_V2 = "migration-test-v2"
    }
}
