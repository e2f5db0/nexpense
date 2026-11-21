// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.ui

import app.nexpense.ledger.equalShares
import app.nexpense.ledger.minorToEuro
import app.nexpense.nostr.Person
import app.nexpense.nostr.Share
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestSummaryTest {
    private val alice = Person(personId = "aaaa-1234", name = "Alice")
    private val bob = Person(personId = "bbbb-1234", name = "Bob")
    private val carol = Person(personId = "cccc-1234", name = "Carol")
    private val people = listOf(alice, bob, carol)

    @Test fun singleDebtorUsesTheActualShareAndDescription() {
        assertEquals("Alice [aaaa] is requesting ${minorToEuro(500)} - Dinner from Bob [bbbb].",
            requestSummary(alice, people, " Dinner ", listOf(Share(bob.personId, 500))))
    }

    @Test fun equalSplitShowsRemaindersAndOwnShareWithoutCreatingSelfDebt() {
        val shares = equalShares(1000, listOf(carol.personId, alice.personId, bob.personId))
        assertEquals("Alice [aaaa] is requesting ${minorToEuro(333)} - Lunch from Bob [bbbb].\n\n" +
            "Alice [aaaa] is requesting ${minorToEuro(333)} - Lunch from Carol [cccc].\n\n" +
            "Alice [aaaa]'s own share: ${minorToEuro(334)} (no debt).",
            requestSummary(alice, people, "Lunch", shares))
    }

    @Test fun customAmountsAndMissingMembersAreDisplayed() {
        assertEquals("Alice [aaaa] is requesting ${minorToEuro(200)} - Taxi from Bob [bbbb].\n\n" +
            "Alice [aaaa] is requesting ${minorToEuro(800)} - Taxi from Unknown member [dddd].",
            requestSummary(alice, people, "Taxi", listOf(Share(bob.personId, 200), Share("dddd-1234", 800))))
    }

    @Test fun requesterOnlyIsNotDescribedAsDebt() {
        assertEquals("Alice [aaaa]'s own share: ${minorToEuro(500)} (no debt).",
            requestSummary(alice, people, "Dinner", listOf(Share(alice.personId, 500))))
    }
}