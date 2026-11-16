// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import app.nexpense.nostr.protocolJson
import java.net.URI

const val REPLICATION_QUORUM = 2
const val MAX_RELAYS = 8

fun relayUrlList(relayUrls: String): List<String> =
    relayUrls.split(',').map(String::trim).filter(String::isNotEmpty).distinct()

fun isValidRelayUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "wss" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null &&
        url.none { it.isWhitespace() || it == ',' }
}.getOrDefault(false)

private fun isCopy(status: String) = status == DeliveryStatus.ACKNOWLEDGED || status == DeliveryStatus.VERIFIED

fun isReplicated(deliveries: List<EventDeliveryEntity>, quorum: Int = REPLICATION_QUORUM): Boolean =
    deliveries.count { isCopy(it.status) } >= quorum

/** Events needed to reconstruct current state on a relay. Superseded settings and deleted targets are optional. */
fun requiredReplicationEvents(events: List<CachedEventEntity>): List<CachedEventEntity> {
    val deleted = events.filter { it.kind == 5 }.flatMap { tagValues(it, "e") }.toSet()
    val latestAddressable = events.filter { it.kind == 30078 }
        .groupBy { tagValues(it, "d").firstOrNull() ?: it.id }.values.map { versions ->
            versions.maxWithOrNull(compareBy<CachedEventEntity> { it.createdAt }.thenBy { it.id })!!
        }
    return events.filter { it.kind == 5 || (it.kind == 78 && it.id !in deleted) } + latestAddressable
}

private fun tagValues(entity: CachedEventEntity, name: String): List<String> =
    runCatching {
        protocolJson.decodeFromString<List<List<String>>>(entity.tagsJson)
            .filter { it.firstOrNull() == name }.mapNotNull { it.getOrNull(1) }
    }.getOrDefault(emptyList())

data class RelayStatus(
    val url: String,
    val label: String,
    val verified: Int,
    val acknowledged: Int,
    val pending: Int,
    val unknown: Int,
    val lastSyncedAt: Long,
    val lastError: String?,
)

data class DurabilitySummary(
    val relays: List<RelayStatus>,
    val groupPublicKey: String,
    val requiredEvents: Int,
    val minimumCopies: Int,
    val headline: String,
    val lastBackupAt: Long,
    val backupRecommended: Boolean,
)

object DurabilityCalculator {
    fun summarize(group: GroupEntity, events: List<CachedEventEntity>, states: List<RelayStateEntity>,
                  deliveries: List<EventDeliveryEntity>): DurabilitySummary {
        val active = relayUrlList(group.relayUrls)
        val required = requiredReplicationEvents(events)
        val requiredIds = required.map { it.id }.toSet()
        val relevant = deliveries.filter { it.eventId in requiredIds && it.relayUrl in active }
        val byRelay = relevant.groupBy { it.relayUrl }
        val stateByUrl = states.associateBy { it.relayUrl }
        val statuses = active.map { url ->
            val rows = byRelay[url].orEmpty()
            val verified = rows.count { it.status == DeliveryStatus.VERIFIED }
            val acknowledged = rows.count { it.status == DeliveryStatus.ACKNOWLEDGED }
            val pending = rows.count { it.status == DeliveryStatus.PENDING }
            // Required events without any delivery row are not yet known to be on this relay.
            val unknown = requiredIds.size - verified - acknowledged - pending
            val state = stateByUrl[url]
            val label = when {
                state == null || state.lastSyncedAt == 0L && state.lastError == null -> "Not synced yet"
                state.lastError != null -> "Delayed"
                pending > 0 || unknown > 0 -> "Catching up"
                acknowledged > 0 -> "Replicated"
                else -> "Verified"
            }
            RelayStatus(url, label, verified, acknowledged, pending, unknown, state?.lastSyncedAt ?: 0, state?.lastError)
        }
        val copiesById = relevant.filter { isCopy(it.status) }.groupingBy { it.eventId }.eachCount()
        val minimumCopies = if (required.isEmpty()) active.size else requiredIds.minOf { copiesById[it] ?: 0 }
        val allVerified = required.isNotEmpty() && relevant.size == requiredIds.size * active.size &&
            relevant.all { it.status == DeliveryStatus.VERIFIED }
        val headline = when {
            required.isEmpty() -> "Nothing to replicate yet"
            minimumCopies == 0 -> "Saved locally"
            minimumCopies < active.size -> "Replicated to $minimumCopies of ${active.size} relays"
            allVerified -> "Verified on ${active.size} of ${active.size} relays"
            else -> "Replicated to ${active.size} of ${active.size} relays"
        }
        val newest = required.maxOfOrNull { it.createdAt } ?: 0
        return DurabilitySummary(statuses, group.publicKey, required.size, minimumCopies, headline, group.lastBackupAt,
            required.isNotEmpty() && newest * 1000 > group.lastBackupAt)
    }
}
