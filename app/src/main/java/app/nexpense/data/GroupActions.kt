// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import app.nexpense.ledger.expenseForRequest
import app.nexpense.nostr.GroupSettings
import app.nexpense.nostr.Payloads
import app.nexpense.nostr.Settlement
import java.util.UUID

suspend fun GroupRepository.initialize(publicKey: String, groupName: String, memberNames: List<String>): List<String> {
    val names = memberNames.map { it.trim() }
    require(names.isNotEmpty() && names.all { it.isNotBlank() }) { "Enter a name for every group member" }
    require(names.map { it.lowercase() }.distinct().size == names.size) { "Group member names must be different" }
    saveSettings(publicKey, groupName)
    return names.map { addPerson(publicKey, it) }
}

suspend fun GroupRepository.saveSettings(publicKey: String, name: String) {
    require(name.isNotBlank())
    publish(publicKey, 30078, "nexpense:v1:group", GroupSettings(name = name.trim()))
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
