// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ledger

import app.nexpense.nostr.Expense
import app.nexpense.nostr.LedgerEvent
import app.nexpense.nostr.Settlement

data class Totals(val youOweMinor: Long = 0, val owedToYouMinor: Long = 0)

data class DebtLine(
    val eventId: String,
    val entryId: String,
    val debtorId: String,
    val creditorId: String,
    val description: String,
    val originalMinor: Long,
    val remainingMinor: Long,
    val createdAt: Long,
    val settlement: Boolean = false,
)

data class LedgerSnapshot(
    val positions: Map<Pair<String, String>, Long>,
    val lines: List<DebtLine>,
    val entries: List<LedgerEvent>,
) {
    fun balance(first: String, second: String): Long =
        (positions[first to second] ?: 0) - (positions[second to first] ?: 0)

    fun totals(actorId: String): Totals {
        val people = positions.keys.flatMap { it.toList() }.toSet()
        var owe = 0L
        var owed = 0L
        people.filter { it != actorId }.forEach { other ->
            val balance = balance(actorId, other)
            if (balance > 0) owe += balance else owed -= balance
        }
        return Totals(owe, owed)
    }

    fun linesBetween(actorId: String, otherId: String): List<DebtLine> = lines.filter {
        (it.debtorId == actorId && it.creditorId == otherId) ||
            (it.debtorId == otherId && it.creditorId == actorId)
    }.sortedWith(compareByDescending<DebtLine> { it.createdAt }.thenByDescending { it.eventId })
}

object LedgerReducer {
    fun reduce(events: Collection<LedgerEvent>, deletedEventIds: Set<String> = emptySet()): LedgerSnapshot {
        val unique = events.filter { it.event.id !in deletedEventIds }.associateBy { it.event.id }
        val ordered = unique.values.sortedWith(compareBy({ it.event.createdAt }, { it.event.id }))
        val positions = mutableMapOf<Pair<String, String>, Long>()
        val pairLines = mutableMapOf<Pair<String, String>, MutableList<DebtLine>>()

        fun linesFor(debtor: String, creditor: String): MutableList<DebtLine> =
            pairLines.getOrPut(debtor to creditor) { mutableListOf() }

        fun position(debtor: String, creditor: String, amount: Long) {
            val key = debtor to creditor
            positions[key] = (positions[key] ?: 0) + amount
        }

        fun addObligation(debtor: String, creditor: String, line: DebtLine) {
            linesFor(debtor, creditor) += line
        }

        fun settle(from: String, to: String, amount: Long, event: LedgerEvent, settlement: Settlement) {
            var remaining = amount
            val iterator = linesFor(from, to).listIterator()
            while (remaining > 0 && iterator.hasNext()) {
                val existing = iterator.next()
                if (existing.debtorId == from && existing.creditorId == to) {
                    val consumed = minOf(remaining, existing.remainingMinor)
                    val updated = existing.copy(remainingMinor = existing.remainingMinor - consumed)
                    iterator.set(updated)
                    remaining -= consumed
                    if (updated.remainingMinor == 0L) iterator.remove()
                }
            }
            if (remaining > 0) {
                linesFor(to, from) += DebtLine(event.event.id, settlement.entryId, to, from, "Overpayment · ${settlement.note}",
                    remaining, remaining, event.event.createdAt, true)
            }
        }

        ordered.forEach { ledgerEvent ->
            when (val entry = ledgerEvent.entry) {
                is Expense -> entry.shares.filter { it.personId != entry.payerId }.forEach { share ->
                    position(share.personId, entry.payerId, share.amountMinor)
                    addObligation(share.personId, entry.payerId, DebtLine(
                        ledgerEvent.event.id, entry.entryId, share.personId, entry.payerId,
                        entry.description, share.amountMinor, share.amountMinor, ledgerEvent.event.createdAt))
                }
                is Settlement -> {
                    position(entry.fromPersonId, entry.toPersonId, -entry.amountMinor)
                    settle(entry.fromPersonId, entry.toPersonId, entry.amountMinor, ledgerEvent, entry)
                }
            }
        }
        return LedgerSnapshot(positions.filterValues { it != 0L }, pairLines.values.flatten(),
            ordered.sortedWith(compareByDescending<LedgerEvent> { it.event.createdAt }.thenByDescending { it.event.id }))
    }
}
