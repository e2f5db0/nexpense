package app.nexpense.nostr

import app.nexpense.signer.GroupSigner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
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
import java.util.concurrent.TimeUnit

class RelayClient(private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()) {
    suspend fun fetch(relays: List<String>, signer: GroupSigner, since: Long? = null): RelayFetchResult = supervisorScope {
        val targets = relays.distinct()
        val results = targets.map { url -> async { fetchOne(url, signer, since) } }.map { it.await() }
        RelayFetchResult(results.flatMap { it.events }.associateBy { it.id }.values.toList(),
            results.count { it.connected }, targets.size)
    }

    suspend fun publish(relays: List<String>, signer: GroupSigner, event: NostrEvent): Boolean = supervisorScope {
        relays.distinct().map { url -> async { publishOne(url, signer, event) } }.map { it.await() }.any { it }
    }

    private suspend fun fetchOne(url: String, signer: GroupSigner, since: Long?): RelayFetch {
        val result = CompletableDeferred<RelayFetch>()
        val events = mutableListOf<NostrEvent>()
        val subscription = "nexpense-${UUID.randomUUID()}"
        val filter = buildJsonObject {
            putJsonArray("authors") { add(signer.publicKey) }
            putJsonArray("kinds") { add(78); add(30078); add(5) }
            putJsonArray("#t") { add("nexpense-v1") }
            if (since != null && since > 0) put("since", since)
        }
        val request = buildJsonArray { add("REQ"); add(subscription); add(filter) }.toString()
        lateinit var socket: WebSocket
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(request) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = protocolJson.parseToJsonElement(text).jsonArray
                when (message[0].jsonPrimitive.content) {
                    "EVENT" -> runCatching {
                        protocolJson.decodeFromJsonElement(NostrEvent.serializer(), message[2])
                    }.getOrNull()?.takeIf { it.pubkey == signer.publicKey }?.let(events::add)
                    "EOSE" -> {
                        webSocket.close(1000, null)
                        result.complete(RelayFetch(events, true))
                    }
                    "AUTH" -> authenticate(url, signer, message[1].jsonPrimitive.content, webSocket)
                    "OK" -> if (message.getOrNull(2)?.jsonPrimitive?.content == "true") webSocket.send(request)
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                result.complete(RelayFetch(events, false))
            }
        }
        socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
        return withTimeoutOrNull(15_000) { result.await() } ?: run { socket.cancel(); RelayFetch(events, false) }
    }

    private suspend fun publishOne(url: String, signer: GroupSigner, event: NostrEvent): Boolean {
        val result = CompletableDeferred<Boolean>()
        lateinit var socket: WebSocket
        val listener = object : WebSocketListener() {
            fun sendEvent(webSocket: WebSocket) {
                webSocket.send(buildJsonArray { add("EVENT"); add(protocolJson.encodeToJsonElement(NostrEvent.serializer(), event)) }.toString())
            }
            override fun onOpen(webSocket: WebSocket, response: Response) { sendEvent(webSocket) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = protocolJson.parseToJsonElement(text).jsonArray
                when (message[0].jsonPrimitive.content) {
                    "OK" -> {
                        val accepted = message.getOrNull(2)?.jsonPrimitive?.content == "true"
                        if (message.getOrNull(1)?.jsonPrimitive?.content == event.id) {
                            result.complete(accepted)
                            webSocket.close(1000, null)
                        } else if (accepted) sendEvent(webSocket) // AUTH accepted; retry the original write.
                    }
                    "AUTH" -> authenticate(url, signer, message[1].jsonPrimitive.content, webSocket)
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { result.complete(false) }
        }
        socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
        return withTimeoutOrNull(12_000) { result.await() } ?: run { socket.cancel(); false }
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

data class RelayFetchResult(val events: List<NostrEvent>, val connected: Int, val attempted: Int)
private data class RelayFetch(val events: List<NostrEvent>, val connected: Boolean)
