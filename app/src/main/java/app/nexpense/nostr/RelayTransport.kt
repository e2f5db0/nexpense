// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.nostr

import app.nexpense.signer.GroupSigner

interface RelayTransport {
    suspend fun fetch(relayUrl: String, signer: GroupSigner, since: Long? = null): RelayFetchResult
    suspend fun fetchIds(relayUrl: String, signer: GroupSigner, eventIds: List<String>): RelayFetchResult
    suspend fun publish(relayUrl: String, signer: GroupSigner, events: List<NostrEvent>): RelayPublishResult
}

data class RelayFetchResult(
    val relayUrl: String,
    val events: List<NostrEvent>,
    val connected: Boolean,
    val completed: Boolean,
    val error: String? = null,
)

data class RelayPublishResult(
    val relayUrl: String,
    val results: Map<String, Boolean>,
    val connected: Boolean,
    val error: String? = null,
)
