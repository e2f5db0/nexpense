// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.nostr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val protocolJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

@Serializable
data class NostrEvent(
    val id: String,
    val pubkey: String,
    @SerialName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
)

@Serializable
data class EventDraft(
    val pubkey: String,
    @SerialName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
)

@Serializable
data class GroupSettings(
    val version: Int = 1,
    val type: String = "group_settings",
    val name: String,
    val currency: String = "EUR",
)

@Serializable
data class Person(
    val version: Int = 1,
    val type: String = "person",
    @SerialName("person_id") val personId: String,
    val name: String,
    val active: Boolean = true,
)

@Serializable
data class Share(
    @SerialName("person_id") val personId: String,
    @SerialName("amount_minor") val amountMinor: Long,
)

sealed interface LedgerEntry {
    val entryId: String
}

@Serializable
data class Expense(
    val version: Int = 1,
    val type: String = "expense",
    @SerialName("entry_id") override val entryId: String,
    @SerialName("payer_id") val payerId: String,
    val description: String,
    val currency: String = "EUR",
    @SerialName("total_minor") val totalMinor: Long,
    @SerialName("incurred_at") val incurredAt: Long,
    val shares: List<Share>,
) : LedgerEntry

@Serializable
data class Settlement(
    val version: Int = 1,
    val type: String = "settlement",
    @SerialName("entry_id") override val entryId: String,
    @SerialName("from_person_id") val fromPersonId: String,
    @SerialName("to_person_id") val toPersonId: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String = "EUR",
    val note: String = "Cash",
    @SerialName("settled_at") val settledAt: Long,
) : LedgerEntry

@Serializable
data class DeletionMetadata(
    val version: Int = 1,
    val type: String = "deletion",
    @SerialName("target_event_id") val targetEventId: String,
    @SerialName("deleted_by") val deletedBy: String,
    val reason: String = "",
)

data class LedgerEvent(val event: NostrEvent, val entry: LedgerEntry)

object Payloads {
    inline fun <reified T> encode(value: T): String = protocolJson.encodeToString(value)

    fun decodeLedger(content: String): LedgerEntry {
        val root = protocolJson.parseToJsonElement(content).jsonObject
        require(root["version"]?.jsonPrimitive?.content == "1") { "Unsupported protocol version" }
        return when (root["type"]?.jsonPrimitive?.content) {
            "expense" -> protocolJson.decodeFromString<Expense>(content).also(::validateExpense)
            "settlement" -> protocolJson.decodeFromString<Settlement>(content).also(::validateSettlement)
            else -> error("Unknown ledger payload")
        }
    }

    fun validateExpense(value: Expense) {
        require(value.version == 1 && value.type == "expense")
        require(value.totalMinor > 0 && value.currency == "EUR")
        require(value.description.isNotBlank() && value.shares.isNotEmpty())
        require(value.shares.all { it.amountMinor > 0 && it.personId.isNotBlank() })
        require(value.shares.map { it.personId }.distinct().size == value.shares.size)
        require(value.shares.sumOf(Share::amountMinor) == value.totalMinor)
    }

    fun validateSettlement(value: Settlement) {
        require(value.version == 1 && value.type == "settlement")
        require(value.amountMinor > 0 && value.currency == "EUR")
        require(value.fromPersonId != value.toPersonId)
    }
}
