// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import app.nexpense.ledger.expenseForRequest
import app.nexpense.nostr.GroupSettings
import app.nexpense.nostr.Payloads
import app.nexpense.nostr.Person
import app.nexpense.nostr.Settlement
import java.util.UUID

suspend fun GroupRepository.initialize(publicKey: String, groupName: String, firstMemberName: String): String {
    saveSettings(publicKey, groupName)
    return addPerson(publicKey, firstMemberName)
}

suspend fun GroupRepository.saveSettings(publicKey: String, name: String) {
    require(name.isNotBlank())
    publish(publicKey, 30078, "nexpense:v1:group", GroupSettings(name = name.trim()))
}

suspend fun GroupRepository.addPerson(publicKey: String, name: String): String {
    require(name.isNotBlank())
    val person = Person(personId = UUID.randomUUID().toString(), name = name.trim())
    publish(publicKey, 30078, "nexpense:v1:person:${person.personId}", person)
    return person.personId
}

suspend fun GroupRepository.request(publicKey: String, payerId: String, description: String,
                                    totalMinor: Long, participants: Collection<String>) {
    require(description.isNotBlank())
    require(participants.isNotEmpty()) { "Select at least one participant" }
    val expense = expenseForRequest(UUID.randomUUID().toString(), payerId, description, totalMinor,
        participants, System.currentTimeMillis() / 1000)
    Payloads.validateExpense(expense)
    publish(publicKey, 78, "nexpense:v1:ledger:${expense.entryId}", expense)
}

suspend fun GroupRepository.requestCustom(publicKey: String, expense: app.nexpense.nostr.Expense) {
    Payloads.validateExpense(expense)
    publish(publicKey, 78, "nexpense:v1:ledger:${expense.entryId}", expense)
}

suspend fun GroupRepository.pay(publicKey: String, from: String, to: String, amountMinor: Long) {
    require(amountMinor > 0)
    val settlement = Settlement(entryId = UUID.randomUUID().toString(), fromPersonId = from,
        toPersonId = to, amountMinor = amountMinor, settledAt = System.currentTimeMillis() / 1000)
    publish(publicKey, 78, "nexpense:v1:ledger:${settlement.entryId}", settlement)
}

suspend fun GroupRepository.delete(publicKey: String, eventId: String, actorId: String) {
    publishDeletion(publicKey, eventId, actorId)
}
