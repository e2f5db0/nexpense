// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nexpense.crypto.Secp256k1
import app.nexpense.crypto.toHex
import app.nexpense.nostr.RelayFetchResult
import app.nexpense.nostr.RelayPublishResult
import app.nexpense.nostr.RelayTransport
import app.nexpense.nostr.NostrEvent
import app.nexpense.signer.DeviceGroupSigner
import app.nexpense.signer.GroupSigner
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayDurabilityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = Room.inMemoryDatabaseBuilder(context, NexpenseDatabase::class.java).build()
    private val dao = database.dao()
    private val secret = ByteArray(32) { 7 }
    private val publicKey = Secp256k1.publicKey(secret).toHex()
    private val signer = DeviceGroupSigner(secret)
    private val relay = FakeRelay()
    private var time = 1_800_000_000_000L
    private val repository = GroupRepository(context, dao, DeviceKeyVault(context), relay,
        enqueueOutbox = { _, _ -> }, clock = { time }, signerOverride = { signer })
    private val urls = listOf("wss://one.example", "wss://two.example", "wss://three.example")

    @After fun close() = database.close()

    @Test fun retriesEachRelayAndKeepsFailedDeliveriesAfterQuorum() = runBlocking {
        dao.saveGroup(GroupEntity(publicKey, "Test", "DEVICE", relayUrls = urls.joinToString(",")))
        repository.saveSettings(publicKey, "Weekend")
        relay.unreachable.addAll(urls.drop(1))
        assertFalse(repository.flushPending(publicKey))
        assertEquals(1, dao.allDeliveries(publicKey).count { it.status == DeliveryStatus.ACKNOWLEDGED })
        assertEquals(2, dao.allDeliveries(publicKey).count { it.status == DeliveryStatus.PENDING })
        assertEquals(1, dao.pendingEvents(publicKey).size)
        relay.unreachable.remove(urls[1])
        assertFalse(repository.flushPending(publicKey))
        assertEquals(2, dao.allDeliveries(publicKey).count { it.status == DeliveryStatus.ACKNOWLEDGED })
        assertTrue(dao.pendingEvents(publicKey).isEmpty())
        relay.unreachable.clear()
        assertTrue(repository.flushPending(publicKey))
        assertEquals(3, dao.allDeliveries(publicKey).count { it.status == DeliveryStatus.ACKNOWLEDGED })
    }

    @Test fun partialFetchDoesNotAdvanceCursorAndLaterRepairsMissingCopies() = runBlocking {
        dao.saveGroup(GroupEntity(publicKey, "Test", "DEVICE", relayUrls = urls.joinToString(",")))
        repository.saveSettings(publicKey, "Weekend")
        assertTrue(repository.flushPending(publicKey))
        relay.unreachable.add(urls[1])
        repository.synchronize(publicKey)
        val before = dao.relayStates(publicKey).associateBy { it.relayUrl }
        assertEquals(0L, before.getValue(urls[1]).syncCursor)
        assertTrue(before.getValue(urls[0]).syncCursor > 0)
        relay.unreachable.clear()
        repository.synchronize(publicKey, forceFull = true)
        assertTrue(relay.events.getValue(urls[1]).isNotEmpty())
        repository.synchronize(publicKey, forceFull = true)
        assertTrue(dao.allDeliveries(publicKey).all { it.status == DeliveryStatus.VERIFIED })
    }

    @Test fun backupRestoreIsAtomicIdempotentAndReconstructsOfflineState() = runBlocking {
        dao.saveGroup(GroupEntity(publicKey, "Test", "DEVICE", relayUrls = urls.joinToString(",")))
        repository.saveSettings(publicKey, "Weekend")
        val alice = repository.addPerson(publicKey, "Alice")
        val bob = repository.addPerson(publicKey, "Bob")
        repository.request(publicKey, alice, "Dinner", 1200, listOf(bob))
        val expenseId = dao.events(publicKey).first { it.kind == 78 }.id
        repository.delete(publicKey, expenseId, alice)
        repository.request(publicKey, alice, "Lunch", 500, listOf(bob))
        val expected = dao.events(publicKey)
        val failingOutput = object : java.io.ByteArrayOutputStream() {
            override fun close() { throw java.io.IOException("simulated close failure") }
        }
        assertTrue(runCatching { repository.exportBackup(publicKey, failingOutput) }.isFailure)
        assertEquals(0L, dao.group(publicKey)!!.lastBackupAt)
        val output = java.io.ByteArrayOutputStream()
        repository.exportBackup(publicKey, output)
        val bytes = output.toByteArray()
        dao.deleteEvents(publicKey)
        dao.deleteDeliveries(publicKey)
        val truncated = bytes.toString(Charsets.UTF_8).substringBeforeLast("{\"record\":\"footer\"").toByteArray()
        assertTrue(runCatching { repository.restoreBackup(publicKey) { truncated.inputStream() } }.isFailure)
        assertTrue(dao.events(publicKey).isEmpty())
        repository.restoreBackup(publicKey) { bytes.inputStream() }
        repository.restoreBackup(publicKey) { bytes.inputStream() }
        val actual = dao.events(publicKey)
        assertEquals(expected.map { it.id }.toSet(), actual.map { it.id }.toSet())
        val projected = GroupProjector.project(dao.group(publicKey)!!, actual)
        assertEquals("Weekend", projected.settings.name)
        assertEquals(setOf("Alice", "Bob"), projected.people.map { it.name }.toSet())
        assertEquals(500L, projected.ledger.balance(bob, alice))
    }

    @Test fun renamePreservesDebtsHistoryAndActorAcrossRapidEdits() = runBlocking {
        dao.saveGroup(GroupEntity(publicKey, "Test", "DEVICE", relayUrls = urls.joinToString(",")))
        val alice = repository.addPerson(publicKey, "Alice")
        val bob = repository.addPerson(publicKey, "Bob")
        repository.selectActor(publicKey, bob)
        repository.request(publicKey, alice, "Dinner", 1200, listOf(bob))
        repository.pay(publicKey, bob, alice, 200)
        val before = GroupProjector.project(dao.group(publicKey)!!, dao.events(publicKey))

        // Advance the simulated clock while a same-second rename waits for the next second.
        time += 900
        val rename = async { repository.renamePerson(publicKey, bob, " Robert ") }
        delay(20)
        time += 100
        rename.await()
        val after = GroupProjector.project(dao.group(publicKey)!!, dao.events(publicKey))
        assertEquals("Robert", after.people.first { it.personId == bob }.name)
        assertEquals(bob, after.actingPerson?.personId)
        assertEquals(1000L, after.ledger.balance(bob, alice))
        assertEquals(before.ledger, after.ledger)
        assertTrue(runCatching { repository.renamePerson(publicKey, bob, "alice") }.isFailure)
        assertTrue(runCatching { repository.renamePerson(publicKey, bob, "   ") }.isFailure)
        assertTrue(runCatching { repository.renamePerson(publicKey, "missing", "Robert") }.isFailure)

        time -= 2000
        assertTrue(runCatching { repository.renamePerson(publicKey, bob, "Bobby") }.isFailure)
        assertEquals("Robert", GroupProjector.project(dao.group(publicKey)!!, dao.events(publicKey))
            .people.first { it.personId == bob }.name)
    }

    @Test fun renameChecksLatestRosterAndSerializesConcurrentAdditions() = runBlocking {
        dao.saveGroup(GroupEntity(publicKey, "Test", "DEVICE", relayUrls = urls.joinToString(",")))
        val alice = repository.addPerson(publicKey, "Alice")
        val bob = repository.addPerson(publicKey, "Bob")
        val eventsBefore = dao.events(publicKey).size
        assertTrue(runCatching { repository.renamePerson(publicKey, bob, " ALICE ") }.isFailure)
        assertEquals(eventsBefore, dao.events(publicKey).size)

        time += 1000
        repository.renamePerson(publicKey, alice, "Carol")
        time += 1000
        repository.renamePerson(publicKey, bob, "alice")
        val beforeConcurrent = dao.events(publicKey).size
        time += 900
        val rename = async { repository.renamePerson(publicKey, bob, "Dave") }
        delay(20)
        val add = async { repository.addPerson(publicKey, "Dave") }
        delay(20)
        assertEquals(beforeConcurrent, dao.events(publicKey).size)
        time += 100
        rename.await()
        add.await()
        val names = GroupProjector.project(dao.group(publicKey)!!, dao.events(publicKey)).people.map { it.name }
        assertEquals(2, names.count { it == "Dave" })
        assertTrue(runCatching { repository.renamePerson(publicKey, alice, "dave") }.isFailure)
    }

    private class FakeRelay : RelayTransport {
        val unreachable = mutableSetOf<String>()
        val events = mutableMapOf<String, MutableMap<String, NostrEvent>>()
        override suspend fun fetch(relayUrl: String, signer: GroupSigner, since: Long?): RelayFetchResult {
            if (relayUrl in unreachable) return RelayFetchResult(relayUrl, emptyList(), false, false, "offline")
            val found = events[relayUrl].orEmpty().values.filter { since == null || it.createdAt >= since }
            return RelayFetchResult(relayUrl, found, true, true)
        }
        override suspend fun fetchIds(relayUrl: String, signer: GroupSigner, eventIds: List<String>): RelayFetchResult {
            val found = events[relayUrl].orEmpty().values.filter { it.id in eventIds }
            return RelayFetchResult(relayUrl, found, true, true)
        }
        override suspend fun publish(relayUrl: String, signer: GroupSigner, events: List<NostrEvent>): RelayPublishResult {
            if (relayUrl in unreachable) return RelayPublishResult(relayUrl, emptyMap(), false, "offline")
            val stored = this.events.getOrPut(relayUrl) { mutableMapOf() }
            events.forEach { stored[it.id] = it }
            return RelayPublishResult(relayUrl, events.associate { it.id to true }, true)
        }
    }
}
