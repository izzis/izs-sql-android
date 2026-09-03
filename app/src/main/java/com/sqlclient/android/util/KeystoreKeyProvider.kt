package com.sqlclient.android.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Provides the AES-256-GCM key stored in the Android Keystore, creating it on
 * first use. No user authentication is required (same behaviour as the former
 * MasterKey setup) so credentials stay accessible without biometric prompt.
 */
class KeystoreKeyProvider(
    private val alias: String = DEFAULT_ALIAS
) {
    companion object {
        const val DEFAULT_ALIAS = "sql_client_cred_key"
        private const val PROVIDER = "AndroidKeyStore"
    }

    fun getOrCreateKey(): SecretKey {
        (getExistingKey() ?: createKey()).also { return it }
    }

    /** Deletes the entry so the next [getOrCreateKey] generates a fresh key. */
    fun invalidate() {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
    }

    private fun getExistingKey(): SecretKey? {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        if (!ks.containsAlias(alias)) return null
        return (ks.getKey(alias, null) as? SecretKey) ?: run {
            ks.deleteEntry(alias)
            null
        }
    }

    private fun createKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(spec)
        }.generateKey()
    }
}
