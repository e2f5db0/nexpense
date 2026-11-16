// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import app.nexpense.nostr.Expense
import app.nexpense.nostr.Payloads
import app.nexpense.nostr.Person
import app.nexpense.nostr.Settlement
import app.nexpense.nostr.Share
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupProjectorTest {
    private val group = GroupEntity("group", "Test", "DEVICE", actingMemberId = "bob")

    private fun event(id: String, time: Long, kind: Int, identifier: String, plaintext: String) =
        CachedEventEntity("group", id, "group", time, kind, "[[\"d\",\"$identifier\"]]", "cipher", "sig", plaintext)

    @Test fun renamedMemberKeepsIdentityBalanceLinesAndHistory() {
        val alice = Person(personId = "alice", name = "Alice")
        val bob = Person(personId = "bob", name = "Bob")
        val expense = Expense(entryId = "dinner", payerId = "alice", description = "Dinner", totalMinor = 1200,
            incurredAt = 1, shares = listOf(Share("bob", 1200)))
        val payment = Settlement(entryId = "payment", fromPersonId = "bob", toPersonId = "alice", amountMinor = 200, settledAt = 2)
        val initial = listOf(
            event("alice", 1, 30078, "nexpense:v1:person:alice", Payloads.encode(alice)),
            event("bob", 1, 30078, "nexpense:v1:person:bob", Payloads.encode(bob)),
            event("expense", 2, 78, "nexpense:v1:ledger:dinner", Payloads.encode(expense)),
            event("payment", 3, 78, "nexpense:v1:ledger:payment", Payloads.encode(payment)),
        )
        val before = GroupProjector.project(group, initial)
        val renamed = event("rename", 4, 30078, "nexpense:v1:person:bob", Payloads.encode(bob.copy(name = "Robert")))
        val after = GroupProjector.project(group, (initial + renamed).reversed())
        assertEquals("Robert", after.actingPerson?.name)
        assertEquals("bob", after.actingPerson?.personId)
        assertEquals(1000L, after.ledger.balance("bob", "alice"))
        assertEquals(before.ledger, after.ledger)
        assertEquals(setOf("alice", "rename", "expense", "payment"),
            requiredReplicationEvents(initial + renamed).map { it.id }.toSet())
    }
}