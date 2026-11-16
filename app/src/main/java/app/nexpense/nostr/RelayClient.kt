// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.nostr

import app.nexpense.signer.GroupSigner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.Collections
import java.util.concurrent.TimeUnit

class RelayClient(private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()) : RelayTransport {
    override suspend fun fetch(relayUrl: String, signer: GroupSigner, since: Long?): RelayFetchResult {
        val filter = buildJsonObject {
            putJsonArray("authors") { add(signer.publicKey) }
            putJsonArray("kinds") { add(78); add(30078); add(5) }
            putJsonArray("#t") { add("nexpense-v1") }
            if (since != null && since > 0) put("since", since)
        }
        return fetchWithFilter(relayUrl, signer, filter)
    }

    override suspend fun fetchIds(relayUrl: String, signer: GroupSigner, eventIds: List<String>): RelayFetchResult {
        if (eventIds.isEmpty()) return RelayFetchResult(relayUrl, emptyList(), connected = true, completed = true)
        val filter = buildJsonObject {
            putJsonArray("ids") { eventIds.distinct().forEach(::add) }
            putJsonArray("authors") { add(signer.publicKey) }
        }
        return fetchWithFilter(relayUrl, signer, filter)
    }

    private suspend fun fetchWithFilter(url: String, signer: GroupSigner, filter: JsonObject): RelayFetchResult {
        val result = CompletableDeferred<RelayFetchResult>()
        val events = Collections.synchronizedList(mutableListOf<NostrEvent>())
        fun snapshot() = synchronized(events) { events.associateBy { it.id }.values.toList() }
        val subscription = "nexpense-${UUID.randomUUID()}"
        val request = buildJsonArray { add("REQ"); add(subscription); add(filter) }.toString()
        val connected = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finish(completed: Boolean, error: String? = null) {
            result.complete(RelayFetchResult(url, snapshot(), connected.get(), completed, error))
        }
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected.set(true)
                webSocket.send(request)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val message = protocolJson.parseToJsonElement(text).jsonArray
                    when (message[0].jsonPrimitive.content) {
                        "EVENT" -> runCatching {
                            protocolJson.decodeFromJsonElement(NostrEvent.serializer(), message[2])
                        }.getOrNull()?.takeIf { it.pubkey == signer.publicKey }?.let(events::add)
                        "EOSE" -> if (message.getOrNull(1)?.jsonPrimitive?.content == subscription) {
                            finish(completed = true)
                            webSocket.close(1000, null)
                        }
                        "AUTH" -> authenticate(url, signer, message[1].jsonPrimitive.content, webSocket)
                        "OK" -> if (message.getOrNull(2)?.jsonPrimitive?.content == "true") webSocket.send(request)
                        "CLOSED" -> {
                            val reason = message.getOrNull(2)?.jsonPrimitive?.content.orEmpty()
                            if (!reason.startsWith("auth-required:")) {
                                finish(completed = false, error = reason)
                                webSocket.close(1000, null)
                            }
                        }
                    }
                }.onFailure { finish(completed = false, error = it.message) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(completed = false, error = t.message)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish(completed = false, error = "Relay closed before completing synchronization")
            }
        }
        val socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
        return withTimeoutOrNull(15_000) { result.await() } ?: run {
            socket.cancel()
            RelayFetchResult(url, snapshot(), connected.get(),
                completed = false, error = "Relay synchronization timed out")
        }
    }

    override suspend fun publish(relayUrl: String, signer: GroupSigner, events: List<NostrEvent>): RelayPublishResult {
        val targets = events.distinctBy { it.id }
        if (targets.isEmpty()) return RelayPublishResult(relayUrl, emptyMap(), connected = true)
        val result = CompletableDeferred<RelayPublishResult>()
        val acknowledgements = Collections.synchronizedMap(mutableMapOf<String, Boolean>())
        fun snapshot() = synchronized(acknowledgements) { acknowledgements.toMap() }
        val connected = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finish(error: String? = null) {
            result.complete(RelayPublishResult(relayUrl, snapshot(), connected.get(), error))
        }
        lateinit var socket: WebSocket
        val listener = object : WebSocketListener() {
            fun sendOutstanding(webSocket: WebSocket) {
                targets.filter { it.id !in acknowledgements }.forEach { event ->
                    webSocket.send(buildJsonArray {
                        add("EVENT")
                        add(protocolJson.encodeToJsonElement(NostrEvent.serializer(), event))
                    }.toString())
                }
            }
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected.set(true)
                sendOutstanding(webSocket)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val message = protocolJson.parseToJsonElement(text).jsonArray
                    when (message[0].jsonPrimitive.content) {
                        "OK" -> {
                            val id = message.getOrNull(1)?.jsonPrimitive?.content
                            val accepted = message.getOrNull(2)?.jsonPrimitive?.content == "true"
                            if (targets.any { it.id == id }) {
                                val reason = message.getOrNull(3)?.jsonPrimitive?.content.orEmpty()
                                if (!accepted && reason.startsWith("auth-required:")) return@runCatching
                                acknowledgements[id!!] = accepted
                                if (acknowledgements.size == targets.size) {
                                    finish()
                                    webSocket.close(1000, null)
                                }
                            } else if (accepted) {
                                sendOutstanding(webSocket) // AUTH accepted; retry every unacknowledged event.
                            }
                        }
                        "AUTH" -> authenticate(relayUrl, signer, message[1].jsonPrimitive.content, webSocket)
                    }
                }.onFailure { finish(it.message) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { finish(t.message) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish("Relay closed before acknowledging all events")
            }
        }
        socket = client.newWebSocket(Request.Builder().url(relayUrl).build(), listener)
        return withTimeoutOrNull(12_000) { result.await() } ?: run {
            socket.cancel()
            RelayPublishResult(relayUrl, snapshot(), connected.get(), "Relay publication timed out")
        }
    }

    private fun authenticate(url: String, signer: GroupSigner, challenge: String, socket: WebSocket) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val draft = EventDraft(signer.publicKey, System.currentTimeMillis() / 1000, 22242,
                    listOf(listOf("relay", url), listOf("challenge", challenge)), "")
                val event = signer.sign(draft)
                socket.send(buildJsonArray { add("AUTH"); add(protocolJson.encodeToJsonElement(NostrEvent.serializer(), event)) }.toString())
            }
        }
    }
}
