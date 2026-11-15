// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ledger

import app.nexpense.crypto.toHex
import app.nexpense.nostr.EventDraft
import app.nexpense.nostr.Expense
import app.nexpense.nostr.LedgerEvent
import app.nexpense.nostr.NostrCrypto
import app.nexpense.nostr.Settlement
import app.nexpense.nostr.Share
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerTest {
    private val secret = ByteArray(32) { 7 }
    private val pub = app.nexpense.crypto.Secp256k1.publicKey(secret).toHex()

    private fun event(id: String, time: Long, entry: app.nexpense.nostr.LedgerEntry): LedgerEvent {
        val signed = NostrCrypto.sign(EventDraft(pub, time, 78, emptyList(), id), secret)
        return LedgerEvent(signed, entry)
    }

    private fun expense(id: String = "dinner", payer: String = "alice", shares: List<Share>) =
        Expense(entryId = id, payerId = payer, description = "Dinner", totalMinor = shares.sumOf { it.amountMinor },
            incurredAt = 1, shares = shares)

    @Test
    fun selfShareIsNotDebt() {
        val snapshot = LedgerReducer.reduce(listOf(event("1", 1, expense(shares = listOf(
            Share("alice", 1000), Share("bob", 1000), Share("carol", 1000))))))
        assertEquals(1000, snapshot.balance("bob", "alice"))
        assertEquals(1000, snapshot.balance("carol", "alice"))
        assertEquals(0, snapshot.balance("alice", "alice"))
        assertEquals(Totals(0, 2000), snapshot.totals("alice"))
        assertEquals(Totals(1000, 0), snapshot.totals("bob"))
    }

    @Test
    fun partialAndFullSettlement() {
        val expenseEvent = event("1", 1, expense(shares = listOf(Share("bob", 3000))))
        val partial = Settlement(entryId = "pay1", fromPersonId = "bob", toPersonId = "alice",
            amountMinor = 1200, settledAt = 2)
        val full = Settlement(entryId = "pay2", fromPersonId = "bob", toPersonId = "alice",
            amountMinor = 1800, settledAt = 3)
        val afterPartial = LedgerReducer.reduce(listOf(expenseEvent, event("2", 2, partial)))
        assertEquals(1800, afterPartial.balance("bob", "alice"))
        assertEquals(1800, afterPartial.lines.single().remainingMinor)
        val fullyPaid = LedgerReducer.reduce(listOf(expenseEvent, event("2", 2, partial), event("3", 3, full)))
        assertEquals(0, fullyPaid.balance("bob", "alice"))
        assertEquals(emptyList<DebtLine>(), fullyPaid.lines)
    }

    @Test
    fun oppositeDebtsRemainDirectedWhileBalancesNet() {
        val first = event("1", 1, expense("one", "alice", listOf(Share("bob", 1000))))
        val second = event("2", 2, expense("two", "bob", listOf(Share("alice", 400))))
        val payment = Settlement(entryId = "pay", fromPersonId = "bob", toPersonId = "alice",
            amountMinor = 800, settledAt = 3)
        val snapshot = LedgerReducer.reduce(listOf(first, second, event("3", 3, payment)))
        assertEquals(-200, snapshot.balance("bob", "alice"))
        val lines = snapshot.lines.associateBy { it.debtorId to it.creditorId }
        assertEquals(200L, lines["bob" to "alice"]?.remainingMinor)
        assertEquals(400L, lines["alice" to "bob"]?.remainingMinor)
    }

    @Test
    fun oppositeOutstandingEntriesRemainVisibleWithoutANetBalanceChange() {
        val first = event("1", 1, expense("one", "alice", listOf(Share("bob", 1000))))
        val second = event("2", 2, expense("two", "bob", listOf(Share("alice", 400))))
        val snapshot = LedgerReducer.reduce(listOf(first, second))

        assertEquals(600, snapshot.balance("bob", "alice"))
        val lines = snapshot.lines.associateBy { it.debtorId to it.creditorId }
        assertEquals(1000L, lines["bob" to "alice"]?.originalMinor)
        assertEquals(400L, lines["alice" to "bob"]?.originalMinor)
    }

    @Test
    fun onlyExplicitSettlementsConsumeTheirDirectedLines() {
        val first = event("1", 1, expense("one", "alice", listOf(Share("bob", 1000))))
        val second = event("2", 2, expense("two", "bob", listOf(Share("alice", 400))))
        val paid = Settlement(entryId = "pay", fromPersonId = "bob", toPersonId = "alice",
            amountMinor = 1000, settledAt = 3)
        val snapshot = LedgerReducer.reduce(listOf(first, second, event("3", 3, paid)))

        assertEquals(-400, snapshot.balance("bob", "alice"))
        assertEquals("alice", snapshot.lines.single().debtorId)
        assertEquals("bob", snapshot.lines.single().creditorId)
        assertEquals(400, snapshot.lines.single().remainingMinor)
    }

    @Test
    fun cashOverpaymentCreatesAReverseOutstandingLine() {
        val expenseEvent = event("1", 1, expense(shares = listOf(Share("bob", 1000))))
        val payment = Settlement(entryId = "pay", fromPersonId = "bob", toPersonId = "alice",
            amountMinor = 1500, settledAt = 2)
        val snapshot = LedgerReducer.reduce(listOf(expenseEvent, event("2", 2, payment)))

        assertEquals(-500, snapshot.balance("bob", "alice"))
        assertEquals("alice", snapshot.lines.single().debtorId)
        assertEquals("bob", snapshot.lines.single().creditorId)
        assertEquals(500, snapshot.lines.single().remainingMinor)
    }

    @Test
    fun deletionDuplicatesAndArrivalOrderConverge() {
        val one = event("1", 1, expense("one", "alice", listOf(Share("bob", 1000))))
        val two = event("2", 2, expense("two", "alice", listOf(Share("carol", 500))))
        val expected = LedgerReducer.reduce(listOf(one, two))
        assertEquals(expected.positions, LedgerReducer.reduce(listOf(two, one, one)).positions)
        assertEquals(expected.lines, LedgerReducer.reduce(listOf(two, one, one)).lines)
        assertEquals(0, LedgerReducer.reduce(listOf(one, two), setOf(one.event.id)).balance("bob", "alice"))
        assertEquals(500, LedgerReducer.reduce(listOf(one, two), setOf(one.event.id)).balance("carol", "alice"))
    }

    @Test
    fun splitRemainderAndEuroParsingAreDeterministic() {
        assertEquals(listOf(Share("a", 334), Share("b", 333), Share("c", 333)),
            equalShares(1000, listOf("c", "a", "b")))
        assertEquals(1234, euroToMinor("12,34"))
        assertEquals(1234, euroToMinor("12.34"))
        assertEquals(0, euroToMinor("12.345"))
    }
}
