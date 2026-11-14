package app.nexpense.crypto

import org.bouncycastle.asn1.x9.ECNamedCurveTable
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.jce.spec.ECParameterSpec
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.SecureRandom

object Secp256k1 {
    private val params = CustomNamedCurves.getByName("secp256k1")
        ?: ECNamedCurveTable.getByName("secp256k1")
    private val curve = params.curve
    private val g = params.g.normalize()
    val n: BigInteger = params.n
    private val p = curve.field.characteristic

    fun generateSecret(random: SecureRandom = SecureRandom()): ByteArray {
        while (true) {
            val candidate = ByteArray(32).also(random::nextBytes)
            val value = BigInteger(1, candidate)
            if (value > BigInteger.ZERO && value < n) return candidate
        }
    }

    fun publicKey(secret: ByteArray): ByteArray {
        val point = g.multiply(secretScalar(secret)).normalize()
        return point.xCoord.encoded
    }

    fun ecdh(secret: ByteArray, publicKey: ByteArray): ByteArray {
        val point = liftX(publicKey).multiply(secretScalar(secret)).normalize()
        require(!point.isInfinity) { "Invalid ECDH point" }
        return point.xCoord.encoded
    }

    fun sign(message: ByteArray, secret: ByteArray, auxRand: ByteArray = SecureRandom().generateSeed(32)): ByteArray {
        require(message.size == 32 && auxRand.size == 32)
        val d0 = secretScalar(secret)
        val pPoint = g.multiply(d0).normalize()
        val d = if (pPoint.hasEvenY()) d0 else n.subtract(d0)
        val px = pPoint.xCoord.encoded
        val t = d.to32Bytes().xor(taggedHash("BIP0340/aux", auxRand))
        val k0 = BigInteger(1, taggedHash("BIP0340/nonce", t, px, message)).mod(n)
        require(k0.signum() != 0) { "Invalid nonce" }
        val rPoint = g.multiply(k0).normalize()
        val k = if (rPoint.hasEvenY()) k0 else n.subtract(k0)
        val rx = rPoint.xCoord.encoded
        val e = BigInteger(1, taggedHash("BIP0340/challenge", rx, px, message)).mod(n)
        val s = k.add(e.multiply(d)).mod(n)
        return rx + s.to32Bytes()
    }

    fun verify(message: ByteArray, publicKey: ByteArray, signature: ByteArray): Boolean = runCatching {
        require(message.size == 32 && signature.size == 64)
        val r = BigInteger(1, signature.copyOfRange(0, 32))
        val s = BigInteger(1, signature.copyOfRange(32, 64))
        if (r >= p || s >= n) return false
        val pPoint = liftX(publicKey)
        val e = BigInteger(1, taggedHash("BIP0340/challenge", signature.copyOfRange(0, 32), publicKey, message)).mod(n)
        val rPoint = g.multiply(s).add(pPoint.negate().multiply(e)).normalize()
        !rPoint.isInfinity && rPoint.hasEvenY() && rPoint.xCoord.toBigInteger() == r
    }.getOrDefault(false)

    fun isValidPublicKey(publicKey: ByteArray): Boolean = runCatching { liftX(publicKey); true }.getOrDefault(false)

    private fun secretScalar(secret: ByteArray): BigInteger {
        require(secret.size == 32)
        return BigInteger(1, secret).also { require(it > BigInteger.ZERO && it < n) { "Invalid secret key" } }
    }

    private fun liftX(publicKey: ByteArray): ECPoint {
        require(publicKey.size == 32)
        val x = BigInteger(1, publicKey)
        require(x < p) { "Invalid public key" }
        val c = x.modPow(BigInteger.valueOf(3), p).add(BigInteger.valueOf(7)).mod(p)
        var y = c.modPow(p.add(BigInteger.ONE).shiftRight(2), p)
        require(y.multiply(y).mod(p) == c) { "Public key is not on curve" }
        if (y.testBit(0)) y = p.subtract(y)
        return curve.createPoint(x, y).normalize()
    }

    private fun ECPoint.hasEvenY(): Boolean = !yCoord.toBigInteger().testBit(0)
    private fun BigInteger.to32Bytes(): ByteArray {
        val raw = toByteArray()
        return ByteArray(32).also { output ->
            val copy = raw.copyOfRange(maxOf(0, raw.size - 32), raw.size)
            copy.copyInto(output, 32 - copy.size)
        }
    }
}
