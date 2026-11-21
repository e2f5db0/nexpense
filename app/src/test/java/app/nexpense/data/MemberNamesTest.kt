// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import app.nexpense.nostr.Person
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberNamesTest {
    private val people = listOf(Person(personId = "a", name = "Alice"), Person(personId = "b", name = " Bob "))

    @Test fun duplicateComparisonTrimsAndIgnoresCase() {
        assertTrue(memberNameExists(people, "  ALICE  ", "b"))
        assertTrue(memberNameExists(people, "bob", "a"))
        assertFalse(memberNameExists(people, "Carol", "a"))
    }

    @Test fun currentMemberIsExcludedButOtherDuplicatesAreNot() {
        assertFalse(memberNameExists(people, "alice", "a"))
        assertTrue(memberNameExists(people + Person(personId = "c", name = "alice"), "Alice", "a"))
        assertFalse(memberNameExists(emptyList(), "Alice", "a"))
    }
}