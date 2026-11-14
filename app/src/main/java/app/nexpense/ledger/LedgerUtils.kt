// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ledger

import app.nexpense.nostr.Expense
import app.nexpense.nostr.Share
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

fun equalShares(totalMinor: Long, participantIds: Collection<String>): List<Share> {
    require(totalMinor > 0 && participantIds.isNotEmpty())
    val sorted = participantIds.distinct().sorted()
    val base = totalMinor / sorted.size
    val remainder = totalMinor % sorted.size
    return sorted.mapIndexed { index, id -> Share(id, base + if (index < remainder) 1 else 0) }.also {
        require(it.all { share -> share.amountMinor > 0 }) { "Amount is too small for this many participants" }
    }
}

fun euroToMinor(input: String): Long = try {
    BigDecimal(input.trim().replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
} catch (_: Exception) { 0L }

fun minorToEuro(amountMinor: Long): String = NumberFormat.getCurrencyInstance(Locale.GERMANY).apply {
    currency = Currency.getInstance("EUR")
}.format(BigDecimal(amountMinor).movePointLeft(2))

fun expenseForRequest(
    entryId: String,
    payerId: String,
    description: String,
    totalMinor: Long,
    participants: Collection<String>,
    timestamp: Long,
): Expense = Expense(entryId = entryId, payerId = payerId, description = description.trim(),
    totalMinor = totalMinor, incurredAt = timestamp, shares = equalShares(totalMinor, participants))
