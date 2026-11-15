package com.example.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class IdentityKeyPairData(
    val publicKeyBase64: String,
    val privateKeyBase64: String
)

data class EncryptedPayload(
    val ivBase64: String,
    val cipherBase64: String
)

data class SafetyFingerprint(
    val shortHex: String,
    val safetyNumber: String,
    val emojis: List<String>
)

class CryptoManager {
    private val secureRandom = SecureRandom()

    fun generateIdentityKeyPair(): IdentityKeyPairData {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val pair = generator.generateKeyPair()
        return IdentityKeyPairData(
            publicKeyBase64 = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP),
            privateKeyBase64 = Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP)
        )
    }

    fun decodePublicKey(base64: String): PublicKey {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    fun decodePrivateKey(base64: String): PrivateKey {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    fun deriveSharedKey(myPrivateKeyBase64: String, peerPublicKeyBase64: String): ByteArray {
        val privateKey = decodePrivateKey(myPrivateKeyBase64)
        val publicKey = decodePublicKey(peerPublicKeyBase64)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        val sharedSecret = agreement.generateSecret()
        return hkdfSha256(
            ikm = sharedSecret,
            salt = "HISN-ECDH-SALT-V1".toByteArray(Charsets.UTF_8),
            info = "HISN-AES-256-GCM-SESSION".toByteArray(Charsets.UTF_8),
            length = 32
        )
    }

    fun deriveCallKey(sharedKey: ByteArray, callId: String): ByteArray {
        return hkdfSha256(
            ikm = sharedKey,
            salt = callId.toByteArray(Charsets.UTF_8),
            info = "HISN-VOICE-UDP-FRAME-KEY".toByteArray(Charsets.UTF_8),
            length = 32
        )
    }

    fun encryptText(plainText: String, key: ByteArray, aad: String): EncryptedPayload {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            cipherBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        )
    }

    fun decryptText(ivBase64: String, cipherBase64: String, key: ByteArray, aad: String): String {
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val cipherBytes = Base64.decode(cipherBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    fun encryptBytes(plainBytes: ByteArray, key: ByteArray, aad: ByteArray): ByteArray {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(plainBytes)
        return ByteBuffer.allocate(12 + encrypted.size)
            .put(iv)
            .put(encrypted)
            .array()
    }

    fun decryptBytes(packet: ByteArray, key: ByteArray, aad: ByteArray): ByteArray {
        require(packet.size > 28) { "Invalid encrypted frame size" }
        val iv = packet.copyOfRange(0, 12)
        val encrypted = packet.copyOfRange(12, packet.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(encrypted)
    }

    fun computePublicKeyShortHex(publicKeyBase64: String): String {
        if (publicKeyBase64.isBlank()) return "--------"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(publicKeyBase64.toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02X".format(it) }
    }

    fun computeSafetyFingerprint(myPubB64: String, peerPubB64: String): SafetyFingerprint {
        if (myPubB64.isBlank() || peerPubB64.isBlank()) {
            return SafetyFingerprint("--------", "00000 00000 00000 00000", listOf("🔐", "🛡️", "🔑", "📡", "⚡", "🌐"))
        }
        val ordered = listOf(myPubB64, peerPubB64).sorted()
        val combined = (ordered[0] + "|" + ordered[1]).toByteArray(Charsets.UTF_8)
        var hash = MessageDigest.getInstance("SHA-256").digest(combined)
        // Double round for 60-digit expansion
        val hash2 = MessageDigest.getInstance("SHA-256").digest(hash + combined)
        val fullBytes = hash + hash2

        val shortHex = hash.take(4).joinToString("") { "%02X".format(it) }

        val sb = StringBuilder()
        for (i in 0 until 12) {
            val idx = i * 4
            val num = (((fullBytes[idx].toInt() and 0xFF) shl 24) or
                ((fullBytes[idx + 1].toInt() and 0xFF) shl 16) or
                ((fullBytes[idx + 2].toInt() and 0xFF) shl 8) or
                (fullBytes[idx + 3].toInt() and 0xFF)).toLong() and 0xFFFFFFFFL
            val fiveDigits = (num % 100000L).toString().padStart(5, '0')
            sb.append(fiveDigits)
            if (i < 11) {
                sb.append(if ((i + 1) % 4 == 0) "\n" else " ")
            }
        }

        val emojis = (0 until 6).map { i ->
            val index = (hash[i].toInt() and 0xFF) % VERIFICATION_EMOJIS.size
            VERIFICATION_EMOJIS[index]
        }

        return SafetyFingerprint(
            shortHex = shortHex,
            safetyNumber = sb.toString(),
            emojis = emojis
        )
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, copyLen)
            offset += copyLen
            counter++
        }
        return result
    }

    companion object {
        private val VERIFICATION_EMOJIS = listOf(
            "🛡️", "🔐", "🔑", "📡", "⚡", "🌐", "🧭", "💎",
            "🛰️", "🔒", "🧩", "🌊", "🦅", "🏔️", "🔥", "🌙",
            "⭐", "🎯", "🧿", "🛸", "🔔", "🌿", "🧬", "🪙"
        )
    }
}
