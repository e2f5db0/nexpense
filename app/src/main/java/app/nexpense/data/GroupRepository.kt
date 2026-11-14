package app.nexpense.data

import android.content.Context
import app.nexpense.crypto.Nsec
import app.nexpense.crypto.Secp256k1
import app.nexpense.crypto.hexToBytes
import app.nexpense.crypto.toHex
import app.nexpense.nostr.DeletionMetadata
import app.nexpense.nostr.EventDraft
import app.nexpense.nostr.Expense
import app.nexpense.nostr.GroupSettings
import app.nexpense.nostr.LedgerEntry
import app.nexpense.nostr.NostrCrypto
import app.nexpense.nostr.NostrEvent
import app.nexpense.nostr.Payloads
import app.nexpense.nostr.Person
import app.nexpense.nostr.RelayClient
import app.nexpense.nostr.Settlement
import app.nexpense.nostr.protocolJson
import app.nexpense.signer.AmberGroupSigner
import app.nexpense.signer.DeviceGroupSigner
import app.nexpense.signer.GroupSigner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.serialization.encodeToString

class GroupRepository(
    private val context: Context,
    private val dao: NexpenseDao,
    private val vault: DeviceKeyVault,
    private val relays: RelayClient = RelayClient(),
) {
    fun observeGroups() = dao.observeGroups()

    fun observeGroup(publicKey: String): Flow<GroupState> = combine(
        dao.observeGroup(publicKey).filterNotNull(), dao.observeEvents(publicKey),
    ) { group, events -> GroupProjector.project(group, events) }

    suspend fun addDeviceGroup(nsec: String, name: String): String {
        val secret = Nsec.decode(nsec.trim())
        val publicKey = Secp256k1.publicKey(secret).toHex()
        require(dao.group(publicKey) == null) { "This group is already on the device" }
        vault.save(publicKey, secret)
        dao.saveGroup(GroupEntity(publicKey, name.ifBlank { "Group ${publicKey.take(6)}" }, "DEVICE"))
        return publicKey
    }

    suspend fun addAmberGroup(publicKey: String, signerPackage: String, name: String): String {
        require(publicKey.length == 64 && Secp256k1.isValidPublicKey(publicKey.hexToBytes()))
        require(dao.group(publicKey) == null) { "This group is already on the device" }
        dao.saveGroup(GroupEntity(publicKey, name.ifBlank { "Amber group ${publicKey.take(6)}" }, "AMBER", signerPackage))
        return publicKey
    }

    suspend fun selectActor(publicKey: String, personId: String) {
        dao.group(publicKey)?.let { dao.saveGroup(it.copy(actingMemberId = personId)) }
    }

    internal suspend fun publish(publicKey: String, kind: Int, identifier: String, payload: Any) {
        val plaintext = encodePayload(payload)
        val signer = signer(publicKey)
        val draft = EventDraft(publicKey, now(), kind,
            listOf(listOf("d", identifier), listOf("t", "nexpense-v1")), signer.encrypt(plaintext))
        store(publicKey, signer.sign(draft), plaintext, true)
        publishPending(publicKey)
    }

    internal suspend fun publishDeletion(publicKey: String, eventId: String, actorId: String) {
        val metadata = DeletionMetadata(targetEventId = eventId, deletedBy = actorId)
        val plaintext = Payloads.encode(metadata)
        val signer = signer(publicKey)
        val draft = EventDraft(publicKey, now(), 5,
            listOf(listOf("e", eventId), listOf("k", "78"), listOf("t", "nexpense-v1")),
            signer.encrypt(plaintext))
        store(publicKey, signer.sign(draft), plaintext, true)
        publishPending(publicKey)
    }

    suspend fun synchronize(publicKey: String) {
        val group = dao.group(publicKey) ?: return
        dao.saveGroup(group.copy(syncStatus = "Syncing…"))
        val signer = signer(publicKey)
        val since = group.syncCursor.takeIf { it > 0 }?.let { (it - 86_400).coerceAtLeast(0) }
        val result = relays.fetch(group.relayUrls.split(',').filter(String::isNotBlank), signer, since)
        result.events.forEach { ingest(publicKey, it) }
        publishPending(publicKey)
        val latest = dao.group(publicKey)!!
        val status = if (result.connected == 0) "Offline · no relay responded" else
            "Synced from ${result.connected}/${result.attempted} relays"
        dao.saveGroup(latest.copy(lastSyncedAt = if (result.connected > 0) System.currentTimeMillis() else latest.lastSyncedAt,
            syncCursor = maxOf(latest.syncCursor, result.events.maxOfOrNull { it.createdAt } ?: 0), syncStatus = status))
    }

    private suspend fun publishPending(publicKey: String) {
        val group = dao.group(publicKey) ?: return
        val signer = signer(publicKey)
        dao.pendingEvents(publicKey).forEach {
            if (relays.publish(group.relayUrls.split(',').filter(String::isNotBlank), signer, it.event())) {
                dao.markPublished(publicKey, it.id)
            }
        }
    }

    private suspend fun ingest(publicKey: String, event: NostrEvent) {
        if (event.pubkey != publicKey || event.kind !in setOf(78, 30078, 5) || !NostrCrypto.verify(event)) return
        val plaintext = runCatching { signer(publicKey).decrypt(event.content) }.getOrNull() ?: return
        store(publicKey, event, plaintext, false)
    }

    private suspend fun store(publicKey: String, event: NostrEvent, plaintext: String, pending: Boolean) {
        dao.insertEvent(CachedEventEntity(publicKey, event.id, event.pubkey, event.createdAt, event.kind,
            protocolJson.encodeToString(event.tags), event.content, event.sig, plaintext, pending))
    }

    internal suspend fun signer(publicKey: String): GroupSigner {
        val group = dao.group(publicKey) ?: error("Unknown group")
        return when (group.keySource) {
            "DEVICE" -> DeviceGroupSigner(vault.load(publicKey))
            "AMBER" -> AmberGroupSigner(context, publicKey, group.signerPackage ?: error("Amber package missing"))
            else -> error("Unknown key source")
        }
    }

    private fun encodePayload(payload: Any) = when (payload) {
        is GroupSettings -> Payloads.encode(payload)
        is Person -> Payloads.encode(payload)
        is Expense -> Payloads.encode(payload)
        is Settlement -> Payloads.encode(payload)
        else -> error("Unsupported payload")
    }

    private fun now() = System.currentTimeMillis() / 1000
}
