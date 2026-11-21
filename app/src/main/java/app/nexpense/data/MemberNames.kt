// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import app.nexpense.nostr.Person

fun memberNameExists(people: List<Person>, name: String, exceptPersonId: String? = null): Boolean =
    people.any { it.personId != exceptPersonId && it.name.trim().equals(name.trim(), ignoreCase = true) }