package com.example

import com.example.crypto.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.AEADBadTagException

@RunWith(RobolectricTestRunner::class)
class CryptoManagerTest {

    @Test
    fun ecdhKeyAgreementAndAesGcmRoundtripWorks() {
        val crypto = CryptoManager()
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()

        val aliceShared = crypto.deriveSharedKey(alice.privateKeyBase64, bob.publicKeyBase64)
        val bobShared = crypto.deriveSharedKey(bob.privateKeyBase64, alice.publicKeyBase64)

        assertArrayEquals(aliceShared, bobShared)
        assertEquals(32, aliceShared.size)

        val aad = "alice:bob:msg1"
        val secretMsg = "مرحباً! هذه رسالة مشفرة بالكامل على الشبكة المحلية 🔐"
        val encrypted = crypto.encryptText(secretMsg, aliceShared, aad)
        assertNotEquals(secretMsg, encrypted.cipherBase64)

        val decrypted = crypto.decryptText(encrypted.ivBase64, encrypted.cipherBase64, bobShared, aad)
        assertEquals(secretMsg, decrypted)

        // Tampered AAD must fail GCM tag verification
        assertThrows(AEADBadTagException::class.java) {
            crypto.decryptText(encrypted.ivBase64, encrypted.cipherBase64, bobShared, "alice:eve:msg1")
        }
    }

    @Test
    fun voiceFrameBytesEncryptAndDecrypt() {
        val crypto = CryptoManager()
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()
        val shared = crypto.deriveSharedKey(alice.privateKeyBase64, bob.publicKeyBase64)
        val callKey = crypto.deriveCallKey(shared, "call_1234")

        val pcmFrame = ByteArray(640) { (it % 127).toByte() }
        val aad = "seq1".toByteArray()
        val packet = crypto.encryptBytes(pcmFrame, callKey, aad)
        val restored = crypto.decryptBytes(packet, callKey, aad)

        assertArrayEquals(pcmFrame, restored)
    }

    @Test
    fun safetyFingerprintIsSymmetricForBothPeers() {
        val crypto = CryptoManager()
        val alice = crypto.generateIdentityKeyPair()
        val bob = crypto.generateIdentityKeyPair()

        val fpAlice = crypto.computeSafetyFingerprint(alice.publicKeyBase64, bob.publicKeyBase64)
        val fpBob = crypto.computeSafetyFingerprint(bob.publicKeyBase64, alice.publicKeyBase64)

        assertEquals(fpAlice.shortHex, fpBob.shortHex)
        assertEquals(fpAlice.safetyNumber, fpBob.safetyNumber)
        assertEquals(fpAlice.emojis, fpBob.emojis)
    }
}
