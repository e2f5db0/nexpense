// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import app.nexpense.crypto.toHex
import app.nexpense.nostr.NostrCrypto
import app.nexpense.nostr.NostrEvent
import app.nexpense.nostr.protocolJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** A streaming, ciphertext-only backup. The group secret is deliberately never included. */
object EventBackup {
    const val MIME_TYPE = "application/octet-stream"
    const val FILE_EXTENSION = "nexpense-backup"

    @Serializable
    private data class Header(
        val record: String = "header",
        val format: String = "nexpense-event-backup",
        val version: Int = 1,
        val groupPublicKey: String,
        val exportedAt: Long,
    )

    @Serializable
    private data class EventRecord(val record: String = "event", val event: NostrEvent)

    @Serializable
    private data class Footer(
        val record: String = "footer",
        val eventCount: Int,
        val eventsSha256: String,
    )

    data class Inspection(val groupPublicKey: String, val exportedAt: Long, val eventCount: Int, val digest: String)

    fun write(output: OutputStream, groupPublicKey: String, events: List<NostrEvent>,
              exportedAt: Long = System.currentTimeMillis()): Inspection {
        val writer = output.bufferedWriter(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val ordered = events.sortedWith(compareBy(NostrEvent::createdAt, NostrEvent::id))
        require(ordered.map { it.id }.distinct().size == ordered.size) { "Duplicate backup event" }
        writer.write(protocolJson.encodeToString(Header(groupPublicKey = groupPublicKey, exportedAt = exportedAt)))
        writer.newLine()
        ordered.forEach { event ->
            require(event.pubkey == groupPublicKey && NostrCrypto.verify(event)) { "Invalid backup event" }
            val line = protocolJson.encodeToString(EventRecord(event = event))
            val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
            digest.update(bytes)
            writer.write(line)
            writer.newLine()
        }
        val hexDigest = digest.digest().toHex()
        writer.write(protocolJson.encodeToString(Footer(eventCount = ordered.size, eventsSha256 = hexDigest)))
        writer.newLine()
        writer.flush()
        return Inspection(groupPublicKey, exportedAt, ordered.size, hexDigest)
    }

    /** Callbacks run before footer validation; callers must not commit changes until this returns. */
    suspend fun read(input: InputStream, expectedPublicKey: String,
                     onEvent: suspend (NostrEvent) -> Unit = {}): Inspection {
        val reader = input.bufferedReader(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val seen = mutableSetOf<String>()
        var header: Header? = null
        var footer: Footer? = null
        var count = 0
        while (true) {
            val line = reader.readLine() ?: break
            require(line.isNotBlank()) { "Blank backup record" }
            if (header == null) {
                header = protocolJson.decodeFromString<Header>(line).also {
                    require(it.record == "header" && it.format == "nexpense-event-backup" && it.version == 1) {
                        "Unsupported backup format"
                    }
                    require(it.groupPublicKey == expectedPublicKey) { "Backup belongs to a different group" }
                }
                continue
            }
            require(footer == null) { "Records found after backup footer" }
            val recordType = protocolJson.parseToJsonElement(line).jsonObject["record"]?.jsonPrimitive?.content
            if (recordType == "event") {
                require(count < MAX_EVENTS) { "Backup contains too many events" }
                val record = protocolJson.decodeFromString<EventRecord>(line)
                require(record.record == "event")
                val event = record.event
                require(event.pubkey == expectedPublicKey && NostrCrypto.verify(event)) { "Invalid backup event" }
                require(seen.add(event.id)) { "Duplicate backup event" }
                digest.update((line + "\n").toByteArray(Charsets.UTF_8))
                onEvent(event)
                count++
            } else {
                footer = protocolJson.decodeFromString<Footer>(line)
                require(footer?.record == "footer") { "Invalid backup footer" }
            }
        }
        val validHeader = header ?: error("Empty backup")
        val validFooter = footer ?: error("Backup is truncated: missing footer")
        val hexDigest = digest.digest().toHex()
        require(validFooter.eventCount == count && validFooter.eventsSha256 == hexDigest) {
            "Backup is truncated or corrupt"
        }
        return Inspection(validHeader.groupPublicKey, validHeader.exportedAt, count, hexDigest)
    }

    private const val MAX_EVENTS = 100_000
}
