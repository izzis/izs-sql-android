package com.sqlclient.android.util

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator

class CredentialCryptoTest {

    private val crypto = CredentialCrypto()

    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun roundtrip_typicalPassword() {
        val key = newKey()
        val plain = "S3cr3t!@#-\$%"
        assertEquals(plain, crypto.decrypt(key, crypto.encrypt(key, plain)))
    }

    @Test fun roundtrip_emptyAndUnicode() {
        val key = newKey()
        assertEquals("", crypto.decrypt(key, crypto.encrypt(key, "")))
        val unicode = "pässwörd-日本語-🔑"
        assertEquals(unicode, crypto.decrypt(key, crypto.encrypt(key, unicode)))
    }

    @Test fun encrypt_randomizedIv() {
        val key = newKey()
        val a = crypto.encrypt(key, "same")
        val b = crypto.encrypt(key, "same")
        assertNotEquals(a, b)
        assertEquals("same", crypto.decrypt(key, a))
        assertEquals("same", crypto.decrypt(key, b))
    }

    @Test fun payloadFormat_ivPlusCiphertext() {
        val key = newKey()
        val raw = Base64.getDecoder().decode(crypto.encrypt(key, "abc"))
        // 12-byte IV + at least 3 bytes plaintext + 16-byte GCM tag
        assertTrue(raw.size >= CredentialCrypto.IV_BYTES + 3 + 16)
    }

    @Test(expected = Exception::class)
    fun decrypt_wrongKeyFails() {
        val payload = crypto.encrypt(newKey(), "secret")
        crypto.decrypt(newKey(), payload)
    }

    @Test(expected = Exception::class)
    fun decrypt_tamperedPayloadFails() {
        val key = newKey()
        val raw = Base64.getDecoder().decode(crypto.encrypt(key, "secret"))
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0xFF).toByte()
        crypto.decrypt(key, Base64.getEncoder().encodeToString(raw))
    }

    @Test(expected = Exception::class)
    fun decrypt_garbageFails() {
        crypto.decrypt(newKey(), "!!!not-base64!!!")
    }
}
