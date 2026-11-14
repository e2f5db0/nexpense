package app.nexpense.crypto

import app.nexpense.nostr.EventDraft
import app.nexpense.nostr.NostrCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoTest {
    @Test
    fun officialNip44Vector() {
        val sec1 = "0000000000000000000000000000000000000000000000000000000000000001".hexToBytes()
        val sec2 = "0000000000000000000000000000000000000000000000000000000000000002".hexToBytes()
        val pub2 = Secp256k1.publicKey(sec2)
        val key = Nip44.conversationKey(sec1, pub2)
        assertEquals("c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d", key.toHex())
        val nonce = "0000000000000000000000000000000000000000000000000000000000000001".hexToBytes()
        val payload = Nip44.encrypt("a", key, nonce)
        assertEquals("AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtDKfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb", payload)
        assertEquals("a", Nip44.decrypt(payload, Nip44.conversationKey(sec2, Secp256k1.publicKey(sec1))))
    }

    @Test
    fun officialBip340SignatureVector() {
        val secret = "0000000000000000000000000000000000000000000000000000000000000003".hexToBytes()
        val publicKey = "F9308A019258C31049344F85F89D5229B531C845836F99B08601F113BCE036F9".hexToBytes()
        val message = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
        val aux = "0000000000000000000000000000000000000000000000000000000000000000".hexToBytes()
        val signature = Secp256k1.sign(message, secret, aux)
        assertEquals("E907831F80848D1069A5371B402410364BDF1C5F8307B0084C55F1CE2DCA821525F66A4A85EA8B71E482A74F382D2CE5EBEEE8FDB2172F477DF4900D310536C0", signature.toHex().uppercase())
        assertArrayEquals(publicKey, Secp256k1.publicKey(secret))
        assertTrue(Secp256k1.verify(message, publicKey, signature))
    }

    @Test
    fun selfEncryptionAndTamperDetection() {
        val secret = ByteArray(32) { (it + 1).toByte() }
        val publicKey = Secp256k1.publicKey(secret)
        val key = Nip44.conversationKey(secret, publicKey)
        val encrypted = Nip44.encrypt("Secret dinner €12.50", key)
        assertEquals("Secret dinner €12.50", Nip44.decrypt(encrypted, key))
        val decoded = java.util.Base64.getDecoder().decode(encrypted)
        decoded[decoded.lastIndex] = (decoded.last().toInt() xor 1).toByte()
        val tampered = java.util.Base64.getEncoder().encodeToString(decoded)
        assertFalse(runCatching { Nip44.decrypt(tampered, key) }.isSuccess)
    }

    @Test
    fun nsecRoundTripAndEventSignature() {
        val secret = Secp256k1.generateSecret()
        assertArrayEquals(secret, Nsec.decode(Nsec.encode(secret)))
        val pubkey = Secp256k1.publicKey(secret).toHex()
        val event = NostrCrypto.sign(EventDraft(pubkey, 1_700_000_000, 78,
            listOf(listOf("t", "nexpense-v1")), "encrypted"), secret)
        assertTrue(NostrCrypto.verify(event))
        assertFalse(NostrCrypto.verify(event.copy(content = "tampered")))
        assertFalse(NostrCrypto.verify(event.copy(sig = "00".repeat(64))))
    }

    @Test
    fun officialExtendedLengthNip44Vector() {
        val sec1 = "0000000000000000000000000000000000000000000000000000000000000001".hexToBytes()
        val pub2 = Secp256k1.publicKey("0000000000000000000000000000000000000000000000000000000000000002".hexToBytes())
        val key = Nip44.conversationKey(sec1, pub2)
        val nonce = "0000000000000000000000000000000000000000000000000000000000000001".hexToBytes()
        val plaintext = "a".repeat(65_536)
        val payload = Nip44.encrypt(plaintext, key, nonce)
        assertEquals("b7b4edb36ba92e267d322d56d9aebc22e7fa96ff52e3c12adc07f07a43cbc616", sha256(payload.toByteArray()).toHex())
        assertEquals("bf718b6f653bebc184e1479f1935b8da974d701b893afcf49e701f3e2f9f9c5a", sha256(plaintext.toByteArray()).toHex())
        assertEquals(plaintext, Nip44.decrypt(payload, key))
    }

    @Test
    fun paddingBoundaries() {
        assertEquals(32, Nip44.calcPaddedLength(1))
        assertEquals(32, Nip44.calcPaddedLength(32))
        assertEquals(64, Nip44.calcPaddedLength(33))
        assertEquals(256, Nip44.calcPaddedLength(256))
        assertEquals(320, Nip44.calcPaddedLength(257))
        assertEquals(65_536, Nip44.calcPaddedLength(65_535))
        assertEquals(65_536, Nip44.calcPaddedLength(65_536))
    }
}
