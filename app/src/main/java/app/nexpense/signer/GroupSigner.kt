// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.signer

import app.nexpense.crypto.Nip44
import app.nexpense.crypto.Secp256k1
import app.nexpense.crypto.toHex
import app.nexpense.nostr.EventDraft
import app.nexpense.nostr.NostrCrypto
import app.nexpense.nostr.NostrEvent

interface GroupSigner {
    val publicKey: String
    suspend fun sign(draft: EventDraft): NostrEvent
    suspend fun encrypt(plaintext: String): String
    suspend fun decrypt(ciphertext: String): String
}

class DeviceGroupSigner(private val secret: ByteArray) : GroupSigner {
    override val publicKey: String = Secp256k1.publicKey(secret).toHex()

    override suspend fun sign(draft: EventDraft): NostrEvent = NostrCrypto.sign(draft, secret)

    override suspend fun encrypt(plaintext: String): String =
        Nip44.encrypt(plaintext, Nip44.conversationKey(secret, Secp256k1.publicKey(secret)))

    override suspend fun decrypt(ciphertext: String): String =
        Nip44.decrypt(ciphertext, Nip44.conversationKey(secret, Secp256k1.publicKey(secret)))
}
