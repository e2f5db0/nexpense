// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.data

import app.nexpense.crypto.Secp256k1
import app.nexpense.crypto.toHex
import app.nexpense.nostr.EventDraft
import app.nexpense.nostr.NostrCrypto
import app.nexpense.signer.DeviceGroupSigner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class EventBackupTest {
    private val secret = ByteArray(32) { 7 }
    private val publicKey = Secp256k1.publicKey(secret).toHex()

    @Test
    fun roundTripPreservesSignedCiphertextWithoutSecretOrPlaintext() = runBlocking {
        val signer = DeviceGroupSigner(secret)
        val encrypted = signer.encrypt("Secret dinner 1250")
        val event = NostrCrypto.sign(EventDraft(publicKey, 10, 78,
            listOf(listOf("t", "nexpense-v1")), encrypted), secret)
        val output = ByteArrayOutputStream()
        EventBackup.write(output, publicKey, listOf(event), 100)
        val bytes = output.toByteArray()
        val text = bytes.toString(Charsets.UTF_8)
        assertFalse(text.contains("Secret dinner"))
        assertFalse(text.contains(secret.toHex()))
        val restored = mutableListOf<app.nexpense.nostr.NostrEvent>()
        val info = EventBackup.read(bytes.inputStream(), publicKey) { restored.add(it) }
        assertEquals(1, info.eventCount)
        assertEquals(event, restored.single())
        assertEquals("Secret dinner 1250", signer.decrypt(restored.single().content))
    }

    @Test
    fun rejectsWrongGroupTruncationCorruptionAndDuplicates() = runBlocking {
        val event = NostrCrypto.sign(EventDraft(publicKey, 10, 78, emptyList(), "ciphertext"), secret)
        val output = ByteArrayOutputStream()
        EventBackup.write(output, publicKey, listOf(event), 100)
        val text = output.toString("UTF-8")
        assertTrue(runCatching { EventBackup.read(text.byteInputStream(), "00".repeat(32)) }.isFailure)
        assertTrue(runCatching { EventBackup.read(text.substringBeforeLast("{\"record\":\"footer\"").byteInputStream(), publicKey) }.isFailure)
        assertTrue(runCatching { EventBackup.read(text.replace("ciphertext", "tampered").byteInputStream(), publicKey) }.isFailure)
        val lines = text.lines()
        val duplicate = listOf(lines[0], lines[1], lines[1], lines[2]).joinToString("\n")
        assertTrue(runCatching { EventBackup.read(duplicate.byteInputStream(), publicKey) }.isFailure)
        assertTrue(runCatching { EventBackup.write(ByteArrayOutputStream(), publicKey, listOf(event, event)) }.isFailure)
    }
}
