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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val publicKey: String,
    val localName: String,
    val keySource: String,
    val signerPackage: String? = null,
    val actingMemberId: String? = null,
    val relayUrls: String = "wss://relay.damus.io,wss://nos.lol,wss://relay.nostr.band",
    val lastSyncedAt: Long = 0,
    val syncCursor: Long = 0,
    val syncStatus: String = "Never synchronized",
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

    @Query("SELECT * FROM groups WHERE publicKey = :publicKey")
    suspend fun group(publicKey: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGroup(group: GroupEntity)

    @Query("DELETE FROM groups WHERE publicKey = :publicKey")
    suspend fun deleteGroup(publicKey: String)

    @Query("SELECT * FROM events WHERE groupPublicKey = :publicKey ORDER BY createdAt DESC")
    fun observeEvents(publicKey: String): Flow<List<CachedEventEntity>>

    @Query("SELECT * FROM events WHERE groupPublicKey = :publicKey")
    suspend fun events(publicKey: String): List<CachedEventEntity>

    @Query("SELECT * FROM events WHERE groupPublicKey = :publicKey AND pending = 1")
    suspend fun pendingEvents(publicKey: String): List<CachedEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: CachedEventEntity): Long

    @Query("UPDATE events SET pending = 0 WHERE groupPublicKey = :publicKey AND id = :eventId")
    suspend fun markPublished(publicKey: String, eventId: String)

    @Query("DELETE FROM events WHERE groupPublicKey = :publicKey")
    suspend fun deleteEvents(publicKey: String)
}

@Database(entities = [GroupEntity::class, CachedEventEntity::class], version = 1, exportSchema = true)
abstract class NexpenseDatabase : RoomDatabase() {
    abstract fun dao(): NexpenseDao

    companion object {
        fun create(context: Context): NexpenseDatabase = Room.databaseBuilder(
            context.applicationContext, NexpenseDatabase::class.java, "nexpense.db",
        ).fallbackToDestructiveMigration().build()
    }
}
