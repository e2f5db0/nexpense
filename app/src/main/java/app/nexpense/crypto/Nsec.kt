// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.crypto

import java.security.SecureRandom

object Nsec {
    private const val charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val generator = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    fun generate(random: SecureRandom = SecureRandom()): String = encode(Secp256k1.generateSecret(random))

    fun decode(value: String): ByteArray {
        require(value == value.lowercase() && value.startsWith("nsec1")) { "Expected a lowercase nsec" }
        require(value.length in 8..1023 && value.all { it.code in 33..126 })
        val separator = value.lastIndexOf('1')
        require(separator == 4 && value.length > separator + 7)
        val values = value.substring(separator + 1).map { char ->
            charset.indexOf(char).also { require(it >= 0) { "Invalid bech32 character" } }
        }
        require(verifyChecksum("nsec", values)) { "Invalid nsec checksum" }
        val decoded = convertBits(values.dropLast(6), 5, 8, false).map { it.toByte() }.toByteArray()
        require(decoded.size == 32) { "Invalid nsec length" }
        Secp256k1.publicKey(decoded) // validates scalar range
        return decoded
    }

    fun encode(secret: ByteArray): String {
        Secp256k1.publicKey(secret)
        return encodeBech32("nsec", secret)
    }

    internal fun encodeBech32(hrp: String, data: ByteArray): String {
        val values = convertBits(data.map { it.toInt() and 0xff }, 8, 5, true)
        val checksum = createChecksum(hrp, values)
        return hrp + "1" + (values + checksum).joinToString("") { charset[it].toString() }
    }

    private fun convertBits(data: List<Int>, from: Int, to: Int, pad: Boolean): List<Int> {
        var accumulator = 0
        var bits = 0
        val result = mutableListOf<Int>()
        val maxValue = (1 shl to) - 1
        for (value in data) {
            require(value >= 0 && (value shr from) == 0)
            accumulator = (accumulator shl from) or value
            bits += from
            while (bits >= to) {
                bits -= to
                result += (accumulator shr bits) and maxValue
            }
        }
        if (pad) {
            if (bits > 0) result += (accumulator shl (to - bits)) and maxValue
        } else {
            require(bits < from && ((accumulator shl (to - bits)) and maxValue) == 0) { "Invalid bech32 padding" }
        }
        return result
    }

    private fun hrpExpand(hrp: String) = hrp.map { it.code shr 5 } + 0 + hrp.map { it.code and 31 }

    private fun polymod(values: List<Int>): Int {
        var checksum = 1
        for (value in values) {
            val top = checksum ushr 25
            checksum = (checksum and 0x1ffffff) shl 5 xor value
            for (i in 0..4) if ((top shr i) and 1 == 1) checksum = checksum xor generator[i]
        }
        return checksum
    }

    private fun verifyChecksum(hrp: String, values: List<Int>) = polymod(hrpExpand(hrp) + values) == 1

    private fun createChecksum(hrp: String, values: List<Int>): List<Int> {
        val mod = polymod(hrpExpand(hrp) + values + List(6) { 0 }) xor 1
        return List(6) { (mod shr (5 * (5 - it))) and 31 }
    }
}

object Npub {
    fun encode(publicKeyHex: String): String {
        require(publicKeyHex.length == 64 && publicKeyHex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            "Expected a 32-byte hex public key"
        }
        val data = ByteArray(32) { index -> publicKeyHex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        return Nsec.encodeBech32("npub", data)
    }
}

data class GeneratedGroupKey(val nsec: String, val publicKeyHex: String) {
    companion object {
        fun create(): GeneratedGroupKey {
            val secret = Secp256k1.generateSecret()
            return GeneratedGroupKey(Nsec.encode(secret), Secp256k1.publicKey(secret).toHex())
        }
    }
}
