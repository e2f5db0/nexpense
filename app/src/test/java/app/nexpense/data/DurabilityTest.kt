// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurabilityTest {
    private fun event(id: String, kind: Int, time: Long, tags: String) =
        CachedEventEntity("group", id, "group", time, kind, tags, "ciphertext", "signature", "{}")

    @Test
    fun repairKeepsLatestSettingsLiveLedgerAndTombstones() {
        val settings1 = event("settings1", 30078, 1, "[[\"d\",\"group\"]]")
        val settings2 = event("settings2", 30078, 2, "[[\"d\",\"group\"]]")
        val deleted = event("deleted", 78, 3, "[]")
        val live = event("live", 78, 4, "[]")
        val tombstone = event("tombstone", 5, 5, "[[\"e\",\"deleted\"]]")
        assertEquals(setOf("settings2", "live", "tombstone"),
            requiredReplicationEvents(listOf(settings1, settings2, deleted, live, tombstone)).map { it.id }.toSet())
    }

    @Test
    fun summaryDistinguishesAcknowledgedFromVerifiedAndUsesMinimumCopies() {
        val group = GroupEntity("group", "Test", "DEVICE", relayUrls = "wss://one,wss://two,wss://three")
        val events = listOf(event("one", 78, 1, "[]"), event("two", 78, 2, "[]"))
        val deliveries = listOf(
            EventDeliveryEntity("group", "one", "wss://one", DeliveryStatus.VERIFIED),
            EventDeliveryEntity("group", "one", "wss://two", DeliveryStatus.ACKNOWLEDGED),
            EventDeliveryEntity("group", "two", "wss://one", DeliveryStatus.ACKNOWLEDGED),
            EventDeliveryEntity("group", "two", "wss://two", DeliveryStatus.PENDING))
        val summary = DurabilityCalculator.summarize(group, events, emptyList(), deliveries)
        assertEquals(1, summary.minimumCopies)
        assertEquals("Replicated to 1 of 3 relays", summary.headline)
        assertTrue(summary.backupRecommended)
        assertTrue(isReplicated(deliveries.filter { it.eventId == "one" }))
        assertFalse(isReplicated(deliveries.filter { it.eventId == "two" }))
    }

    @Test
    fun onlySecureRelayAddressesAreAccepted() {
        assertTrue(isValidRelayUrl("wss://relay.example.com"))
        assertFalse(isValidRelayUrl("ws://relay.example.com"))
        assertFalse(isValidRelayUrl("wss://relay.example.com,evil"))
        assertFalse(isValidRelayUrl("wss://user:password@relay.example.com"))
        assertFalse(isValidRelayUrl("wss://relay.example.com/#fragment"))
        assertEquals(listOf("wss://one", "wss://two"), relayUrlList("wss://one, wss://two,wss://one"))
    }
}
