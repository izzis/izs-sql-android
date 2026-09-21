package id.web.izs.sqlclient.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Openssl-compatible format: "Salted__" + 8B salt + AES-256-CBC ciphertext (PKCS5).
 * Decryptable via: openssl enc -d -aes-256-cbc -pbkdf2 -iter 120000 -in file.enc -pass pass:xxx
 * Uses PBKDF2WithHmacSHA256 to derive 48B (32 key + 16 iv), same as openssl enc -pbkdf2.
 */
object ProfileCrypto {
    private const val ITER = 120_000
    private const val SALT_LEN = 8
    private val MAGIC = "Salted__".toByteArray(Charsets.US_ASCII)

    fun encrypt(plain: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val keyIv = deriveKeyIv(password, salt)
        val key = keyIv.copyOfRange(0, 32)
        val iv = keyIv.copyOfRange(32, 48)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val ct = cipher.doFinal(plain)
        val out = ByteArray(MAGIC.size + SALT_LEN + ct.size)
        MAGIC.copyInto(out, 0)
        salt.copyInto(out, MAGIC.size)
        ct.copyInto(out, MAGIC.size + SALT_LEN)
        return out
    }

    fun decrypt(data: ByteArray, password: String): ByteArray {
        if (data.size < MAGIC.size + SALT_LEN + 16) throw IllegalArgumentException("Invalid file")
        val magic = data.copyOfRange(0, MAGIC.size)
        if (!magic.contentEquals(MAGIC)) throw IllegalArgumentException("Invalid file (bad magic, expected Salted__)")
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
        val ct = data.copyOfRange(MAGIC.size + SALT_LEN, data.size)
        val keyIv = deriveKeyIv(password, salt)
        val key = keyIv.copyOfRange(0, 32)
        val iv = keyIv.copyOfRange(32, 48)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        try {
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            return cipher.doFinal(ct)
        } catch (e: Exception) {
            throw IllegalArgumentException("Wrong master password or corrupt file: ${e.message}")
        }
    }

    private fun deriveKeyIv(password: String, salt: ByteArray): ByteArray {
        // 48B = 32 key + 16 iv for AES-256-CBC, same as openssl enc -pbkdf2
        val spec = PBEKeySpec(password.toCharArray(), salt, ITER, 384)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}
