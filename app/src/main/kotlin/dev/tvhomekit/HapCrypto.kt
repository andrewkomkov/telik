package dev.tvhomekit

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

internal object Tlv {
    const val METHOD = 0
    const val IDENTIFIER = 1
    const val SALT = 2
    const val PUBLIC_KEY = 3
    const val PROOF = 4
    const val ENCRYPTED = 5
    const val STATE = 6
    const val ERROR = 7
    const val SIGNATURE = 10
    const val PERMISSIONS = 11
    const val SEPARATOR = 0xFF

    const val ERROR_AUTHENTICATION = 2
    const val ERROR_UNAVAILABLE = 6

    fun decode(data: ByteArray): Map<Int, ByteArray> {
        val out = LinkedHashMap<Int, ByteArray>()
        var i = 0
        while (i + 2 <= data.size) {
            val tag = data[i].toInt() and 0xFF
            val length = data[i + 1].toInt() and 0xFF
            val value = data.copyOfRange(i + 2, i + 2 + length)
            out[tag] = (out[tag] ?: ByteArray(0)) + value
            i += 2 + length
        }
        return out
    }

    fun encode(vararg items: Pair<Int, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((tag, value) in items) {
            if (value.isEmpty()) {
                out.write(tag)
                out.write(0)
            }
            var offset = 0
            while (offset < value.size) {
                val length = minOf(255, value.size - offset)
                out.write(tag)
                out.write(length)
                out.write(value, offset, length)
                offset += length
            }
        }
        return out.toByteArray()
    }

    fun state(value: Int) = byteArrayOf(value.toByte())
}

internal object HapCrypto {
    private val random = SecureRandom()

    fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    fun sha512(vararg parts: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-512")
        parts.forEach(digest::update)
        return digest.digest()
    }

    fun hkdf(key: ByteArray, salt: String, info: String): ByteArray {
        val generator = HKDFBytesGenerator(SHA512Digest())
        generator.init(HKDFParameters(key, salt.toByteArray(), info.toByteArray()))
        return ByteArray(32).also { generator.generateBytes(it, 0, 32) }
    }

    fun nonce(label: String) = ByteArray(4) + label.toByteArray()

    fun nonce(counter: Long): ByteArray {
        val out = ByteArray(12)
        for (i in 0 until 8) out[4 + i] = (counter ushr (8 * i)).toByte()
        return out
    }

    private fun cipher(encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray?) =
        ChaCha20Poly1305().apply { init(encrypt, AEADParameters(KeyParameter(key), 128, nonce, aad)) }

    fun seal(key: ByteArray, nonce: ByteArray, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val aead = cipher(true, key, nonce, aad)
        val out = ByteArray(aead.getOutputSize(plain.size))
        val written = aead.processBytes(plain, 0, plain.size, out, 0)
        aead.doFinal(out, written)
        return out
    }

    /** null, если код подлинности не сошёлся. */
    fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray? = null): ByteArray? {
        val aead = cipher(false, key, nonce, aad)
        val out = ByteArray(aead.getOutputSize(sealed.size))
        return try {
            val written = aead.processBytes(sealed, 0, sealed.size, out, 0)
            aead.doFinal(out, written)
            out
        } catch (e: org.bouncycastle.crypto.InvalidCipherTextException) {
            null
        }
    }

    fun ed25519Public(seed: ByteArray): ByteArray =
        Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded

    fun ed25519Sign(seed: ByteArray, message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(seed, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun ed25519Verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        signer.update(message, 0, message.size)
        return signer.verifySignature(signature)
    }

    class Curve {
        private val secret = X25519PrivateKeyParameters(random)
        val publicKey: ByteArray = secret.generatePublicKey().encoded

        fun shared(peer: ByteArray): ByteArray {
            val agreement = X25519Agreement()
            agreement.init(secret)
            return ByteArray(32).also {
                agreement.calculateAgreement(X25519PublicKeyParameters(peer, 0), it, 0)
            }
        }
    }
}

/** SRP-6a (3072 бит, SHA-512). Паддинг как у iPhone: k и u дополняются до длины N, остальное без нулей. */
internal class SrpServer(pin: String) {
    val salt = HapCrypto.randomBytes(16)
    private val secret = BigInteger(1, HapCrypto.randomBytes(32))
    private val verifier: BigInteger
    private val serverPublic: BigInteger
    private var clientPublic = BigInteger.ZERO
    private var clientProof = ByteArray(0)
    var sessionKey = ByteArray(0)
        private set

    init {
        val inner = HapCrypto.sha512("Pair-Setup:$pin".toByteArray())
        val x = BigInteger(1, HapCrypto.sha512(salt, inner))
        val k = BigInteger(1, HapCrypto.sha512(trim(N), pad(G)))
        verifier = G.modPow(x, N)
        serverPublic = k.multiply(verifier).add(G.modPow(secret, N)).mod(N)
    }

    val publicKey: ByteArray get() = trim(serverPublic)

    fun verifyClient(publicKey: ByteArray, proof: ByteArray): Boolean {
        val a = BigInteger(1, publicKey)
        if (a.mod(N).signum() == 0) return false
        val u = BigInteger(1, HapCrypto.sha512(pad(a), pad(serverPublic)))
        val premaster = a.multiply(verifier.modPow(u, N)).mod(N).modPow(secret, N)
        sessionKey = HapCrypto.sha512(trim(premaster))
        val hn = BigInteger(1, HapCrypto.sha512(trim(N)))
        val hg = BigInteger(1, HapCrypto.sha512(trim(G)))
        val expected = HapCrypto.sha512(
            trim(hn.xor(hg)),
            HapCrypto.sha512("Pair-Setup".toByteArray()),
            salt,
            trim(a),
            trim(serverPublic),
            sessionKey,
        )
        clientPublic = a
        clientProof = expected
        return MessageDigestCompare.equal(expected, proof)
    }

    fun serverProof() = HapCrypto.sha512(trim(clientPublic), clientProof, sessionKey)

    private fun pad(value: BigInteger): ByteArray {
        val bytes = trim(value)
        return ByteArray(LENGTH - bytes.size) + bytes
    }

    private fun trim(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return if (bytes.size > 1 && bytes[0].toInt() == 0) bytes.copyOfRange(1, bytes.size) else bytes
    }

    private companion object {
        const val LENGTH = 384
        val G: BigInteger = BigInteger.valueOf(5)
        val N = BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA6" +
                "3B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F2411" +
                "7C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
                "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08" +
                "CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
                "DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D" +
                "04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
                "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D8760273" +
                "3EC86A64521F2B18177B200CBBE117577A615D6C770988C0BAD946E208E24FA074E5AB31" +
                "43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF",
            16,
        )
    }
}

internal object MessageDigestCompare {
    fun equal(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)
}
