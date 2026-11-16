// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import android.content.Context
import app.nexpense.crypto.Nsec
import app.nexpense.crypto.Secp256k1
import app.nexpense.crypto.toHex
import app.nexpense.nostr.DeletionMetadata
import app.nexpense.nostr.EventDraft
import app.nexpense.nostr.Expense
import app.nexpense.nostr.GroupSettings
import app.nexpense.nostr.NostrCrypto
import app.nexpense.nostr.NostrEvent
import app.nexpense.nostr.Payloads
import app.nexpense.nostr.Person
import app.nexpense.nostr.RelayClient
import app.nexpense.nostr.RelayTransport
import app.nexpense.nostr.Settlement
import app.nexpense.nostr.protocolJson
import app.nexpense.signer.DeviceGroupSigner
import app.nexpense.signer.GroupSigner
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.util.concurrent.ConcurrentHashMap
import java.io.InputStream
import java.io.OutputStream

class GroupRepository(
    private val context: Context,
    private val dao: NexpenseDao,
    private val vault: DeviceKeyVault,
    private val relays: RelayTransport = RelayClient(),
    private val enqueueOutbox: (Context, String) -> Unit = SyncWorker::enqueueOutbox,
    private val clock: () -> Long = System::currentTimeMillis,
    private val signerOverride: (suspend (String) -> GroupSigner)? = null,
) {
    private val groupLocks = ConcurrentHashMap<String, Mutex>()
    private fun lock(publicKey: String) = groupLocks.getOrPut(publicKey) { Mutex() }

    fun observeGroups() = dao.observeGroups()

    fun observeGroup(publicKey: String): Flow<GroupState> = combine(
        dao.observeGroup(publicKey).filterNotNull(), dao.observeEvents(publicKey),
    ) { group, events -> GroupProjector.project(group, events) }

    fun observeDurability(publicKey: String): Flow<DurabilitySummary> = combine(
        dao.observeGroup(publicKey).filterNotNull(), dao.observeEvents(publicKey),
        dao.observeRelayStates(publicKey), dao.observeDeliveries(publicKey),
    ) { group, events, states, deliveries -> DurabilityCalculator.summarize(group, events, states, deliveries) }

    suspend fun addDeviceGroup(nsec: String, name: String): String {
        val secret = Nsec.decode(nsec.trim())
        val publicKey = Secp256k1.publicKey(secret).toHex()
        require(dao.group(publicKey) == null) { "This group is already on the device" }
        vault.save(publicKey, secret)
        val group = GroupEntity(publicKey, name.ifBlank { "Group ${publicKey.take(6)}" }, "DEVICE")
        dao.saveGroup(group)
        ensureRelayRows(publicKey, relayUrlList(group.relayUrls))
        return publicKey
    }

    suspend fun selectActor(publicKey: String, personId: String) {
        dao.updateActor(publicKey, personId)
    }

    suspend fun clearActingMembers() = dao.clearActingMembers()

    suspend fun groups(): List<GroupEntity> = dao.groups()

    suspend fun removeGroup(publicKey: String) = lock(publicKey).withLock {
        dao.deleteDeliveries(publicKey)
        dao.deleteRelayStates(publicKey)
        dao.deleteEvents(publicKey)
        dao.deleteGroup(publicKey)
        vault.remove(publicKey)
    }

    internal suspend fun publish(publicKey: String, kind: Int, identifier: String, payload: Any, createdAt: Long = now()) {
        val plaintext = encodePayload(payload)
        val signer = signer(publicKey)
        val draft = EventDraft(publicKey, createdAt, kind,
            listOf(listOf("d", identifier), listOf("t", "nexpense-v1")), signer.encrypt(plaintext))
        store(publicKey, signer.sign(draft), plaintext, true)
        enqueueOutbox(context, publicKey)
    }

    suspend fun renamePerson(publicKey: String, personId: String, name: String) = lock(publicKey).withLock {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Enter a member name" }
        val group = dao.group(publicKey) ?: error("Unknown group")
        val events = dao.events(publicKey)
        val people = GroupProjector.project(group, events).people
        val person = people.firstOrNull { it.personId == personId } ?: error("Member not found")
        require(people.none { it.personId != personId && it.name.equals(trimmed, ignoreCase = true) }) {
            "Group member names must be different"
        }
        if (person.name == trimmed) return@withLock
        val identifier = "nexpense:v1:person:$personId"
        val latest = events.filter { event ->
            event.kind == 30078 && runCatching {
                protocolJson.decodeFromString<List<List<String>>>(event.tagsJson)
                    .any { it.firstOrNull() == "d" && it.getOrNull(1) == identifier }
            }.getOrDefault(false)
        }.maxOfOrNull { it.createdAt } ?: error("Member record not found")
        require(now() >= latest) { "Device clock is behind the member record; check the date and time" }
        if (now() == latest) delay(1000L - clock().mod(1000L))
        val timestamp = now()
        require(timestamp > latest) { "Unable to create a newer member record; check the device clock" }
        publish(publicKey, 30078, identifier, person.copy(name = trimmed), timestamp)
    }

    internal suspend fun publishDeletion(publicKey: String, eventId: String, actorId: String) {
        val metadata = DeletionMetadata(targetEventId = eventId, deletedBy = actorId)
        val plaintext = Payloads.encode(metadata)
        val signer = signer(publicKey)
        val draft = EventDraft(publicKey, now(), 5,
            listOf(listOf("e", eventId), listOf("k", "78"), listOf("t", "nexpense-v1")),
            signer.encrypt(plaintext))
        store(publicKey, signer.sign(draft), plaintext, true)
        enqueueOutbox(context, publicKey)
    }

    /**
     * Catches up from every relay with that relay's own cursor, then repairs relays that are missing events.
     * A relay's cursor only advances after that relay reaches EOSE.
     */
    suspend fun synchronize(publicKey: String, forceFull: Boolean = false) = lock(publicKey).withLock {
        val group = dao.group(publicKey) ?: return@withLock
        dao.updateSyncStatus(publicKey, SYNCING_STATUS)
        try {
            val signer = signer(publicKey)
            val urls = relayUrlList(group.relayUrls)
            ensureRelayRows(publicKey, urls)
            val states = dao.relayStates(publicKey).associateBy { it.relayUrl }
            val startedAt = clock()
            val fetched = supervisorScope {
                urls.map { url ->
                    async {
                        val state = states.getValue(url)
                        val full = forceFull || state.syncCursor <= 0 || state.lastFullSyncAt <= 0 ||
                            startedAt - state.lastFullSyncAt > FULL_SYNC_INTERVAL_MS
                        val since = if (full) null else (state.syncCursor - OVERLAP_SECONDS).coerceAtLeast(0)
                        FetchOutcome(url, full, relays.fetch(url, signer, since))
                    }
                }.map { it.await() }
            }
            val known = dao.events(publicKey).map { it.id }.toMutableSet()
            var completed = 0
            fetched.forEach { outcome ->
                val state = states.getValue(outcome.url)
                outcome.result.events.forEach { ingest(publicKey, outcome.url, it, signer, known) }
                if (outcome.result.completed) {
                    completed++
                    dao.saveRelayState(state.copy(
                        syncCursor = maxOf(state.syncCursor, outcome.result.events
                            .filter { it.pubkey == publicKey && it.kind in SUPPORTED_KINDS &&
                                it.createdAt <= startedAt / 1000 && NostrCrypto.verify(it) }
                            .maxOfOrNull { it.createdAt } ?: 0),
                        lastSyncedAt = startedAt,
                        lastFullSyncAt = if (outcome.full) startedAt else state.lastFullSyncAt,
                        lastError = null))
                } else {
                    dao.saveRelayState(state.copy(lastError = outcome.result.error
                        ?: if (outcome.result.connected) "Relay did not finish synchronization" else "Relay unreachable"))
                }
            }
            reconcile(publicKey, fetched.filter { it.result.completed && it.full }.associate { outcome ->
                outcome.url to outcome.result.events.filter { it.pubkey == publicKey && NostrCrypto.verify(it) }
                    .map { it.id }.toSet()
            })
            supervisorScope {
                fetched.filter { it.result.connected }.map { async { verifyUnknown(publicKey, signer, it.url) } }
                    .forEach { it.await() }
            }
            flushPendingLocked(publicKey)
            val latest = dao.group(publicKey) ?: return@withLock
            dao.updateSyncResult(publicKey,
                if (completed > 0) startedAt else latest.lastSyncedAt,
                dao.relayStates(publicKey).maxOfOrNull { it.syncCursor } ?: latest.syncCursor,
                when {
                    completed == 0 -> "Offline"
                    completed < urls.size -> "Partially synced"
                    else -> "Synced"
                })
        } catch (t: Throwable) {
            dao.updateSyncStatus(publicKey, "Sync failed")
            throw t
        }
    }

    private data class FetchOutcome(val url: String, val full: Boolean, val result: app.nexpense.nostr.RelayFetchResult)

    /** Publishes pending events to every relay independently. Returns true when no relay delivery remains pending. */
    suspend fun flushPending(publicKey: String): Boolean = lock(publicKey).withLock { flushPendingLocked(publicKey) }

    private suspend fun flushPendingLocked(publicKey: String): Boolean {
        val group = dao.group(publicKey) ?: return true
        val urls = relayUrlList(group.relayUrls)
        val signer = signer(publicKey)
        ensureRelayRows(publicKey, urls)
        val byId = dao.events(publicKey).associateBy { it.id }
        // Events queued before per-relay tracking existed only carry the legacy pending flag.
        byId.values.filter { it.pending }.forEach { event ->
            urls.forEach { dao.insertDelivery(EventDeliveryEntity(publicKey, event.id, it, DeliveryStatus.PENDING)) }
        }
        val requiredIds = requiredReplicationEvents(byId.values.toList()).map { it.id }.toSet()
        byId.values.filter { it.pending && it.id !in requiredIds }.forEach { dao.setPending(publicKey, it.id, false) }
        val pending = dao.allDeliveries(publicKey)
            .filter { it.status == DeliveryStatus.PENDING && it.relayUrl in urls && it.eventId in requiredIds }
        val outcomes = supervisorScope {
            pending.groupBy { it.relayUrl }.map { (url, rows) ->
                async { Triple(url, rows, publishInBatches(url, signer, rows.mapNotNull { byId[it.eventId] })) }
            }.map { it.await() }
        }
        val attemptedAt = clock()
        outcomes.forEach { (url, rows, outcome) ->
            rows.forEach { row ->
                val accepted = outcome.acknowledgements[row.eventId]
                dao.saveDelivery(row.copy(
                    status = if (accepted == true) DeliveryStatus.ACKNOWLEDGED else DeliveryStatus.PENDING,
                    attempts = row.attempts + 1, lastAttemptAt = attemptedAt,
                    lastError = when (accepted) {
                        true -> null
                        false -> "Relay rejected the event"
                        null -> outcome.error ?: "No response from relay"
                    }))
            }
            if (!outcome.connected) {
                dao.relayStates(publicKey).firstOrNull { it.relayUrl == url }
                    ?.let { dao.saveRelayState(it.copy(lastError = outcome.error ?: "Relay unreachable")) }
            }
        }
        val deliveries = dao.allDeliveries(publicKey).filter { it.relayUrl in urls }
        byId.values.filter { it.pending }.forEach { event ->
            if (isReplicated(deliveries.filter { it.eventId == event.id }, minOf(REPLICATION_QUORUM, urls.size))) {
                dao.setPending(publicKey, event.id, false)
            }
        }
        return deliveries.none { it.status == DeliveryStatus.PENDING && it.eventId in requiredIds }
    }

    suspend fun addRelay(publicKey: String, url: String) = lock(publicKey).withLock {
        val group = dao.group(publicKey) ?: error("Unknown group")
        val normalized = url.trim()
        require(isValidRelayUrl(normalized)) { "Enter a secure relay address starting with wss://" }
        val urls = relayUrlList(group.relayUrls)
        require(normalized !in urls) { "This relay is already configured" }
        require(urls.size < MAX_RELAYS) { "Use at most $MAX_RELAYS relays to limit battery use and metadata exposure" }
        dao.updateRelayUrls(publicKey, (urls + normalized).joinToString(","))
        ensureRelayRows(publicKey, listOf(normalized))
        // Backfill the new relay with everything needed to reconstruct the group.
        requiredReplicationEvents(dao.events(publicKey)).forEach {
            dao.saveDelivery(EventDeliveryEntity(publicKey, it.id, normalized, DeliveryStatus.PENDING))
        }
        enqueueOutbox(context, publicKey)
    }

    suspend fun removeRelay(publicKey: String, url: String) = lock(publicKey).withLock {
        val group = dao.group(publicKey) ?: error("Unknown group")
        val urls = relayUrlList(group.relayUrls)
        require(url in urls) { "Relay is not configured" }
        require(urls.size > 1) { "Keep at least one relay" }
        dao.updateRelayUrls(publicKey, (urls - url).joinToString(","))
        dao.deleteRelayState(publicKey, url)
        dao.deleteRelayDeliveries(publicKey, url)
    }

    /** Exports signed ciphertext only; neither plaintext nor the group secret leaves the database. */
    suspend fun exportBackup(publicKey: String, output: OutputStream): EventBackup.Inspection {
        val inspection = output.use {
            val events = dao.events(publicKey).map { event -> event.event() }
            EventBackup.write(it, publicKey, events, clock())
        }
        dao.updateBackupTime(publicKey, inspection.exportedAt)
        return inspection
    }

    /**
     * Validates the whole file (signatures, group key, checksum, decryptability) before inserting anything.
     * Restored events are not marked pending; the next synchronization verifies and repairs relay copies.
     */
    suspend fun restoreBackup(publicKey: String, open: () -> InputStream): EventBackup.Inspection = lock(publicKey).withLock {
        val signer = signer(publicKey)
        val restored = mutableListOf<CachedEventEntity>()
        val inspection = open().use { stream ->
            EventBackup.read(stream, publicKey) { event ->
                require(event.kind in SUPPORTED_KINDS) { "Backup contains an unsupported event" }
                val plaintext = signer.decrypt(event.content)
                restored.add(CachedEventEntity(publicKey, event.id, event.pubkey, event.createdAt, event.kind,
                    protocolJson.encodeToString(event.tags), event.content, event.sig, plaintext, false))
            }
        }
        // Room's list insert is transactional; no events are inserted before the footer is validated.
        dao.insertEvents(restored)
        inspection
    }

    private suspend fun publishInBatches(url: String, signer: GroupSigner, events: List<CachedEventEntity>): PublishOutcome {
        // Deletion tombstones go last so a relay never sees them before their targets.
        val ordered = events.sortedWith(compareBy({ if (it.kind == 5) 1 else 0 }, { it.createdAt }, { it.id }))
        val acknowledgements = mutableMapOf<String, Boolean>()
        var connected = false
        var error: String? = null
        for (batch in ordered.chunked(PUBLISH_BATCH)) {
            val result = relays.publish(url, signer, batch.map { it.event() })
            connected = connected || result.connected
            acknowledgements += result.results
            if (!result.connected || result.results.size < batch.size) { error = result.error; break }
        }
        return PublishOutcome(acknowledgements, connected, error)
    }

    private data class PublishOutcome(val acknowledgements: Map<String, Boolean>, val connected: Boolean, val error: String?)

    private suspend fun ensureRelayRows(publicKey: String, urls: List<String>) {
        urls.forEach { dao.insertRelayState(RelayStateEntity(publicKey, it)) }
    }

    /** Creates delivery rows and rechecks previously acknowledged events absent from a full listing. */
    private suspend fun reconcile(publicKey: String, fullListings: Map<String, Set<String>>) {
        val group = dao.group(publicKey) ?: return
        val urls = relayUrlList(group.relayUrls)
        val required = requiredReplicationEvents(dao.events(publicKey))
        val existing = dao.allDeliveries(publicKey).associateBy { it.eventId to it.relayUrl }
        urls.forEach { url ->
            required.forEach { event ->
                val row = existing[event.id to url]
                if (row == null) {
                    dao.saveDelivery(EventDeliveryEntity(publicKey, event.id, url, DeliveryStatus.UNKNOWN))
                } else if (fullListings[url]?.let { event.id !in it } == true &&
                    row.status in setOf(DeliveryStatus.ACKNOWLEDGED, DeliveryStatus.VERIFIED)) {
                    // A relay may silently limit a full listing; confirm missing IDs separately.
                    dao.saveDelivery(row.copy(status = DeliveryStatus.UNKNOWN))
                }
            }
        }
    }

    /** Confirms unknown copies by event ID; copies the relay definitely lacks become pending. */
    private suspend fun verifyUnknown(publicKey: String, signer: GroupSigner, url: String) {
        val unknown = dao.allDeliveries(publicKey).filter { it.relayUrl == url && it.status == DeliveryStatus.UNKNOWN }
        for (batch in unknown.chunked(VERIFY_BATCH)) {
            val result = relays.fetchIds(url, signer, batch.map { it.eventId })
            if (!result.completed) return
            val present = result.events.filter { it.pubkey == publicKey && NostrCrypto.verify(it) }.map { it.id }.toSet()
            batch.forEach { row ->
                dao.saveDelivery(if (row.eventId in present) row.copy(status = DeliveryStatus.VERIFIED, lastError = null)
                else row.copy(status = DeliveryStatus.PENDING, lastError = "Missing on relay"))
            }
        }
    }

    private suspend fun ingest(publicKey: String, url: String, event: NostrEvent, signer: GroupSigner,
                               known: MutableSet<String>) {
        if (event.pubkey != publicKey || event.kind !in SUPPORTED_KINDS || !NostrCrypto.verify(event)) return
        if (event.id !in known) {
            val plaintext = runCatching { signer.decrypt(event.content) }.getOrNull() ?: return
            store(publicKey, event, plaintext, false)
            known += event.id
        }
        dao.saveDelivery(EventDeliveryEntity(publicKey, event.id, url, DeliveryStatus.VERIFIED))
    }

    private suspend fun store(publicKey: String, event: NostrEvent, plaintext: String, pending: Boolean) {
        val cached = CachedEventEntity(publicKey, event.id, event.pubkey, event.createdAt, event.kind,
            protocolJson.encodeToString(event.tags), event.content, event.sig, plaintext, pending)
        if (pending) {
            val urls = relayUrlList(dao.group(publicKey)?.relayUrls.orEmpty())
            ensureRelayRows(publicKey, urls)
            dao.insertPendingEvent(cached, urls.map { EventDeliveryEntity(publicKey, event.id, it, DeliveryStatus.PENDING) })
        } else dao.insertEvent(cached)
    }

    internal suspend fun signer(publicKey: String): GroupSigner {
        signerOverride?.let { return it(publicKey) }
        val group = dao.group(publicKey) ?: error("Unknown group")
        return when (group.keySource) {
            "DEVICE" -> DeviceGroupSigner(vault.load(publicKey))
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

    private fun now() = clock() / 1000

    companion object {
        const val SYNCING_STATUS = "Syncing…"
        private val SUPPORTED_KINDS = setOf(78, 30078, 5)
        private const val OVERLAP_SECONDS = 86_400L
        private const val FULL_SYNC_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L
        private const val PUBLISH_BATCH = 50
        private const val VERIFY_BATCH = 50
    }
}
