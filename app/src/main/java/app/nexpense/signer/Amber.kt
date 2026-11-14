// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.signer

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.nexpense.nostr.EventDraft
import app.nexpense.nostr.NostrEvent
import app.nexpense.nostr.NostrCrypto
import app.nexpense.nostr.protocolJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

object AmberConnector {
    fun connectionIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
        putExtra("type", "get_public_key")
        putExtra("permissions", """[{"type":"sign_event"},{"type":"nip44_encrypt"},{"type":"nip44_decrypt"}]""")
    }
}

class AmberGroupSigner(
    private val context: Context,
    override val publicKey: String,
    private val signerPackage: String,
) : GroupSigner {
    override suspend fun sign(draft: EventDraft): NostrEvent {
        val response = query("SIGN_EVENT", protocolJson.encodeToString(draft), "")
        val eventJson = response["event"] ?: error("Amber did not return a signed event")
        return protocolJson.decodeFromString<NostrEvent>(eventJson).also {
            require(it.pubkey == publicKey) { "Amber signed with the wrong account" }
            require(it.kind == draft.kind && it.createdAt == draft.createdAt && it.tags == draft.tags && it.content == draft.content)
            require(NostrCrypto.verify(it)) { "Amber returned an invalid event signature" }
        }
    }

    override suspend fun encrypt(plaintext: String): String =
        query("NIP44_ENCRYPT", plaintext, publicKey)["result"] ?: error("Amber encryption failed")

    override suspend fun decrypt(ciphertext: String): String =
        query("NIP44_DECRYPT", ciphertext, publicKey)["result"] ?: error("Amber decryption failed")

    private suspend fun query(type: String, payload: String, otherPublicKey: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse("content://$signerPackage.$type")
            val cursor = context.contentResolver.query(uri, null, null,
                arrayOf(payload, otherPublicKey, publicKey), null)
                ?: error("Amber permission is not remembered; reconnect Amber")
            cursor.use {
                if (it.getColumnIndex("rejected") >= 0) error("Amber rejected the request")
                require(it.moveToFirst()) { "Amber returned no result" }
                buildMap {
                    for (column in arrayOf("result", "event")) {
                        val index = it.getColumnIndex(column)
                        if (index >= 0 && !it.isNull(index)) put(column, it.getString(index))
                    }
                }
            }
        }
}
