// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val publicKey: String,
    val localName: String,
    val keySource: String,
    val signerPackage: String? = null,
    val actingMemberId: String? = null,
    val relayUrls: String = "wss://relay.damus.io,wss://nos.lol,wss://nostr.mom,wss://relay.snort.social",
    val lastSyncedAt: Long = 0,
    val syncCursor: Long = 0,
    val syncStatus: String = "Never synchronized",
    val lastBackupAt: Long = 0,
)

@Entity(tableName = "relay_states", primaryKeys = ["groupPublicKey", "relayUrl"])
data class RelayStateEntity(
    val groupPublicKey: String,
    val relayUrl: String,
    val syncCursor: Long = 0,
    val lastSyncedAt: Long = 0,
    val lastFullSyncAt: Long = 0,
    val lastError: String? = null,
)

object DeliveryStatus {
    const val UNKNOWN = "UNKNOWN"
    const val PENDING = "PENDING"
    const val ACKNOWLEDGED = "ACKNOWLEDGED"
    const val VERIFIED = "VERIFIED"
}

@Entity(tableName = "event_deliveries", primaryKeys = ["groupPublicKey", "eventId", "relayUrl"])
data class EventDeliveryEntity(
    val groupPublicKey: String,
    val eventId: String,
    val relayUrl: String,
    val status: String = DeliveryStatus.PENDING,
    val attempts: Int = 0,
    val lastAttemptAt: Long = 0,
    val lastError: String? = null,
)

@Entity(tableName = "events", primaryKeys = ["groupPublicKey", "id"])
data class CachedEventEntity(
    val groupPublicKey: String,
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tagsJson: String,
    val content: String,
    val sig: String,
    val plaintext: String?,
    val pending: Boolean = false,
)

@Dao
interface NexpenseDao {
    @Query("SELECT * FROM groups ORDER BY localName COLLATE NOCASE")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE publicKey = :publicKey")
    fun observeGroup(publicKey: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups")
    suspend fun groups(): List<GroupEntity>

    @Query("SELECT * FROM relay_states WHERE groupPublicKey = :publicKey")
    fun observeRelayStates(publicKey: String): Flow<List<RelayStateEntity>>

    @Query("SELECT * FROM event_deliveries WHERE groupPublicKey = :publicKey")
    fun observeDeliveries(publicKey: String): Flow<List<EventDeliveryEntity>>

    @Query("SELECT * FROM relay_states WHERE groupPublicKey = :publicKey")
    suspend fun relayStates(publicKey: String): List<RelayStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRelayState(state: RelayStateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelayState(state: RelayStateEntity): Long

    @Query("SELECT * FROM event_deliveries WHERE groupPublicKey = :publicKey")
    suspend fun allDeliveries(publicKey: String): List<EventDeliveryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDelivery(delivery: EventDeliveryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDelivery(delivery: EventDeliveryEntity): Long

    @Query("DELETE FROM relay_states WHERE groupPublicKey = :publicKey AND relayUrl = :relayUrl")
    suspend fun deleteRelayState(publicKey: String, relayUrl: String)

    @Query("DELETE FROM event_deliveries WHERE groupPublicKey = :publicKey AND relayUrl = :relayUrl")
    suspend fun deleteRelayDeliveries(publicKey: String, relayUrl: String)

    @Query("DELETE FROM relay_states WHERE groupPublicKey = :publicKey")
    suspend fun deleteRelayStates(publicKey: String)

    @Query("DELETE FROM event_deliveries WHERE groupPublicKey = :publicKey")
    suspend fun deleteDeliveries(publicKey: String)

    @Query("SELECT * FROM groups WHERE publicKey = :publicKey")
    suspend fun group(publicKey: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGroup(group: GroupEntity)

    @Query("UPDATE groups SET actingMemberId = NULL")
    suspend fun clearActingMembers()

    @Query("UPDATE groups SET actingMemberId = :personId WHERE publicKey = :publicKey")
    suspend fun updateActor(publicKey: String, personId: String)

    @Query("UPDATE groups SET syncStatus = :status WHERE publicKey = :publicKey")
    suspend fun updateSyncStatus(publicKey: String, status: String)

    @Query("UPDATE groups SET lastSyncedAt = :lastSyncedAt, syncCursor = :cursor, syncStatus = :status WHERE publicKey = :publicKey")
    suspend fun updateSyncResult(publicKey: String, lastSyncedAt: Long, cursor: Long, status: String)

    @Query("UPDATE groups SET lastBackupAt = :time WHERE publicKey = :publicKey")
    suspend fun updateBackupTime(publicKey: String, time: Long)

    @Query("UPDATE groups SET relayUrls = :urls WHERE publicKey = :publicKey")
    suspend fun updateRelayUrls(publicKey: String, urls: String)

    @Query("DELETE FROM groups WHERE publicKey = :publicKey")
    suspend fun deleteGroup(publicKey: String)

    @Query("SELECT * FROM events WHERE groupPublicKey = :publicKey ORDER BY createdAt DESC")
    fun observeEvents(publicKey: String): Flow<List<CachedEventEntity>>

    @Query("SELECT * FROM events WHERE groupPublicKey = :publicKey")
    suspend fun events(publicKey: String): List<CachedEventEntity>

    @Query("SELECT * FROM events WHERE groupPublicKey = :publicKey AND pending = 1")
    suspend fun pendingEvents(publicKey: String): List<CachedEventEntity>

    @androidx.room.Transaction
    suspend fun insertPendingEvent(event: CachedEventEntity, deliveries: List<EventDeliveryEntity>) {
        insertEvent(event)
        deliveries.forEach { insertDelivery(it) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: CachedEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<CachedEventEntity>)

    @Query("UPDATE events SET pending = :pending WHERE groupPublicKey = :publicKey AND id = :eventId")
    suspend fun setPending(publicKey: String, eventId: String, pending: Boolean)

    @Query("DELETE FROM events WHERE groupPublicKey = :publicKey")
    suspend fun deleteEvents(publicKey: String)
}

@Database(entities = [GroupEntity::class, CachedEventEntity::class, RelayStateEntity::class,
    EventDeliveryEntity::class], version = 4, exportSchema = true)
abstract class NexpenseDatabase : RoomDatabase() {
    abstract fun dao(): NexpenseDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE groups ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS groups_without_hidden (
                        publicKey TEXT NOT NULL,
                        localName TEXT NOT NULL,
                        keySource TEXT NOT NULL,
                        signerPackage TEXT,
                        actingMemberId TEXT,
                        relayUrls TEXT NOT NULL,
                        lastSyncedAt INTEGER NOT NULL,
                        syncCursor INTEGER NOT NULL,
                        syncStatus TEXT NOT NULL,
                        PRIMARY KEY(publicKey)
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO groups_without_hidden (publicKey, localName, keySource, signerPackage, actingMemberId,
                        relayUrls, lastSyncedAt, syncCursor, syncStatus)
                    SELECT publicKey, localName, keySource, signerPackage, actingMemberId,
                        relayUrls, lastSyncedAt, syncCursor, syncStatus
                    FROM groups
                """.trimIndent())
                database.execSQL("DROP TABLE groups")
                database.execSQL("ALTER TABLE groups_without_hidden RENAME TO groups")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE groups ADD COLUMN lastBackupAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS relay_states (
                        groupPublicKey TEXT NOT NULL,
                        relayUrl TEXT NOT NULL,
                        syncCursor INTEGER NOT NULL DEFAULT 0,
                        lastSyncedAt INTEGER NOT NULL DEFAULT 0,
                        lastFullSyncAt INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        PRIMARY KEY(groupPublicKey, relayUrl)
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS event_deliveries (
                        groupPublicKey TEXT NOT NULL,
                        eventId TEXT NOT NULL,
                        relayUrl TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        attempts INTEGER NOT NULL DEFAULT 0,
                        lastAttemptAt INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT,
                        PRIMARY KEY(groupPublicKey, eventId, relayUrl)
                    )
                """.trimIndent())
            }
        }

        fun create(context: Context): NexpenseDatabase = Room.databaseBuilder(
            context.applicationContext, NexpenseDatabase::class.java, "nexpense.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
