// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.nostr

import app.nexpense.crypto.Secp256k1
import app.nexpense.crypto.hexToBytes
import app.nexpense.crypto.sha256
import app.nexpense.crypto.toHex
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.addJsonArray

object NostrCrypto {
    fun canonicalId(draft: EventDraft): ByteArray = sha256(buildJsonArray {
        add(0)
        add(draft.pubkey)
        add(draft.createdAt)
        add(draft.kind)
        addJsonArray {
            draft.tags.forEach { tag ->
                addJsonArray { tag.forEach(::add) }
            }
        }
        add(draft.content)
    }.toString().toByteArray(Charsets.UTF_8))

    fun sign(draft: EventDraft, secret: ByteArray): NostrEvent {
        val id = canonicalId(draft)
        require(Secp256k1.publicKey(secret).toHex() == draft.pubkey) { "Wrong key for group" }
        return NostrEvent(
            id = id.toHex(),
            pubkey = draft.pubkey,
            createdAt = draft.createdAt,
            kind = draft.kind,
            tags = draft.tags,
            content = draft.content,
            sig = Secp256k1.sign(id, secret).toHex(),
        )
    }

    fun verify(event: NostrEvent): Boolean {
        if (event.id.length != 64 || event.pubkey.length != 64 || event.sig.length != 128) return false
        val draft = EventDraft(event.pubkey, event.createdAt, event.kind, event.tags, event.content)
        val expected = runCatching { canonicalId(draft) }.getOrElse { return false }
        return runCatching {
            expected.toHex() == event.id && Secp256k1.verify(expected, event.pubkey.hexToBytes(), event.sig.hexToBytes())
        }.getOrDefault(false)
    }
}
