// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import app.nexpense.ledger.minorToEuro
import app.nexpense.nostr.Person
import app.nexpense.nostr.Share

fun requestSummary(actor: Person, people: List<Person>, description: String, shares: List<Share>): String {
    val byId = people.associateBy { it.personId }
    val debts = shares.filter { it.personId != actor.personId }.map { share ->
        val debtor = byId[share.personId]?.let(::memberLabel) ?: "Unknown member [${share.personId.take(4)}]"
        "${memberLabel(actor)} is requesting ${minorToEuro(share.amountMinor)} - ${description.trim()} from $debtor."
    }
    val ownShare = shares.firstOrNull { it.personId == actor.personId }?.let {
        "${memberLabel(actor)}'s own share: ${minorToEuro(it.amountMinor)} (no debt)."
    }
    return (debts + listOfNotNull(ownShare)).joinToString("\n\n")
}