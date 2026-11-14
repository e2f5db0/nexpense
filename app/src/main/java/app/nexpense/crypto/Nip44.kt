// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.crypto

import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Nip44 {
    fun conversationKey(secret: ByteArray, publicKey: ByteArray): ByteArray =
        hkdfExtract("nip44-v2".toByteArray(), Secp256k1.ecdh(secret, publicKey))

    fun encrypt(plaintext: String, conversationKey: ByteArray, nonce: ByteArray = SecureRandom().generateSeed(32)): String {
        require(conversationKey.size == 32 && nonce.size == 32)
        val padded = pad(plaintext)
        val keys = messageKeys(conversationKey, nonce)
        val ciphertext = chacha20(keys.chachaKey, keys.chachaNonce, padded)
        val mac = hmacSha256(keys.hmacKey, nonce + ciphertext)
        return Base64.getEncoder().encodeToString(byteArrayOf(2) + nonce + ciphertext + mac)
    }

    fun decrypt(payload: String, conversationKey: ByteArray): String {
        require(!payload.startsWith("#")) { "Unsupported NIP-44 encoding" }
        require(payload.length >= 132) { "NIP-44 payload too short" }
        val decoded = Base64.getDecoder().decode(payload)
        require(decoded.size >= 99 && decoded[0].toInt() == 2) { "Unsupported NIP-44 version" }
        require(conversationKey.size == 32)
        val nonce = decoded.copyOfRange(1, 33)
        val ciphertext = decoded.copyOfRange(33, decoded.size - 32)
        val suppliedMac = decoded.copyOfRange(decoded.size - 32, decoded.size)
        val keys = messageKeys(conversationKey, nonce)
        val expectedMac = hmacSha256(keys.hmacKey, nonce + ciphertext)
        require(MessageDigest.isEqual(expectedMac, suppliedMac)) { "Invalid NIP-44 MAC" }
        return unpad(chacha20(keys.chachaKey, keys.chachaNonce, ciphertext))
    }

    internal fun calcPaddedLength(length: Int): Int {
        require(length > 0)
        if (length <= 32) return 32
        // Avoid floating point/logarithm rounding at powers of two.
        var nextPower = 1
        while (nextPower < length) nextPower = nextPower shl 1
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * ((length - 1) / chunk + 1)
    }

    private fun messageKeys(conversationKey: ByteArray, nonce: ByteArray): MessageKeys {
        val bytes = hkdfExpand(conversationKey, nonce, 76)
        return MessageKeys(bytes.copyOfRange(0, 32), bytes.copyOfRange(32, 44), bytes.copyOfRange(44, 76))
    }

    private fun pad(plaintext: String): ByteArray {
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty())
        val paddedLength = calcPaddedLength(bytes.size)
        val prefix = if (bytes.size < 65_536) {
            ByteBuffer.allocate(2).putShort(bytes.size.toShort()).array()
        } else {
            ByteBuffer.allocate(6).putShort(0).putInt(bytes.size).array()
        }
        return prefix + bytes + ByteArray(paddedLength - bytes.size)
    }

    private fun unpad(padded: ByteArray): String {
        require(padded.size >= 34)
        val shortLength = ByteBuffer.wrap(padded, 0, 2).short.toInt() and 0xffff
        val prefixLength: Int
        val plaintextLength: Long
        if (shortLength == 0) {
            require(padded.size >= 38)
            prefixLength = 6
            plaintextLength = ByteBuffer.wrap(padded, 2, 4).int.toLong() and 0xffffffffL
        } else {
            prefixLength = 2
            plaintextLength = shortLength.toLong()
        }
        require(plaintextLength in 1..Int.MAX_VALUE.toLong())
        val length = plaintextLength.toInt()
        require(prefixLength + length <= padded.size)
        require(calcPaddedLength(length) == padded.size - prefixLength)
        require(padded.copyOfRange(prefixLength + length, padded.size).all { it == 0.toByte() })
        return String(padded, prefixLength, length, Charsets.UTF_8)
    }

    private fun chacha20(key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray {
        val engine = ChaCha7539Engine()
        engine.init(true, ParametersWithIV(KeyParameter(key), nonce))
        return ByteArray(input.size).also { engine.processBytes(input, 0, input.size, it, 0) }
    }

    private data class MessageKeys(val chachaKey: ByteArray, val chachaNonce: ByteArray, val hmacKey: ByteArray)
}
