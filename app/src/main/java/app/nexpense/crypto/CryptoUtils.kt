// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 e2f5db0

package app.nexpense.crypto

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0 && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "Invalid hex" }
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

fun taggedHash(tag: String, vararg inputs: ByteArray): ByteArray {
    val tagHash = sha256(tag.toByteArray(Charsets.UTF_8))
    return sha256(inputs.fold(tagHash + tagHash) { acc, bytes -> acc + bytes })
}

fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(message)

fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray = hmacSha256(salt, ikm)

fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
    require(length in 0..255 * 32)
    var previous = ByteArray(0)
    var output = ByteArray(0)
    var counter = 1
    while (output.size < length) {
        previous = hmacSha256(prk, previous + info + byteArrayOf(counter.toByte()))
        output += previous
        counter++
    }
    return output.copyOf(length)
}

fun ByteArray.xor(other: ByteArray): ByteArray {
    require(size == other.size)
    return ByteArray(size) { (this[it].toInt() xor other[it].toInt()).toByte() }
}
