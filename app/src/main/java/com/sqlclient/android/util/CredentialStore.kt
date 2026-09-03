package com.sqlclient.android.util

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.InvalidKeyException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted credential storage backed by the Android Keystore (AES-256-GCM,
 * see [KeystoreKeyProvider]) with ciphertexts in a plain SharedPreferences
 * file. No legacy migration: fresh installs start from an empty store.
 */
@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        const val PREFS_FILE = "sql_client_secure_prefs_v2"
    }

    private val crypto = CredentialCrypto()
    private val keyProvider = KeystoreKeyProvider()

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private fun encryptValue(plaintext: String): String {
        return try {
            crypto.encrypt(keyProvider.getOrCreateKey(), plaintext)
        } catch (_: InvalidKeyException) {
            // Key permanently invalidated (rare) — recreate once and retry.
            keyProvider.invalidate()
            crypto.encrypt(keyProvider.getOrCreateKey(), plaintext)
        }
    }

    private fun decryptValue(payload: String?): String? {
        if (payload.isNullOrEmpty()) return null
        return try {
            crypto.decrypt(keyProvider.getOrCreateKey(), payload)
        } catch (_: Exception) {
            // Missing, tampered or undecryptable entry — treat as absent.
            null
        }
    }

    fun savePassword(profileId: Long, password: String) {
        prefs.edit().putString("password_$profileId", encryptValue(password)).apply()
    }

    fun getPassword(profileId: Long): String? {
        return decryptValue(prefs.getString("password_$profileId", null))
    }

    fun deletePassword(profileId: Long) {
        prefs.edit().remove("password_$profileId").apply()
    }

    fun saveSshPassword(profileId: Long, password: String) {
        prefs.edit().putString("ssh_password_$profileId", encryptValue(password)).apply()
    }

    fun getSshPassword(profileId: Long): String? {
        return decryptValue(prefs.getString("ssh_password_$profileId", null))
    }

    fun deleteSshPassword(profileId: Long) {
        prefs.edit().remove("ssh_password_$profileId").apply()
    }

    fun saveSshPassphrase(profileId: Long, passphrase: String) {
        prefs.edit().putString("ssh_passphrase_$profileId", encryptValue(passphrase)).apply()
    }

    fun getSshPassphrase(profileId: Long): String? {
        return decryptValue(prefs.getString("ssh_passphrase_$profileId", null))
    }

    fun deleteSshPassphrase(profileId: Long) {
        prefs.edit().remove("ssh_passphrase_$profileId").apply()
    }

    fun deleteAllCredentials(profileId: Long) {
        deletePassword(profileId)
        deleteSshPassword(profileId)
        deleteSshPassphrase(profileId)
    }

    fun hasPassword(profileId: Long): Boolean {
        return !getPassword(profileId).isNullOrBlank()
    }

    fun hasSshPassword(profileId: Long): Boolean {
        return !getSshPassword(profileId).isNullOrBlank()
    }

    fun hasSshPassphrase(profileId: Long): Boolean {
        return !getSshPassphrase(profileId).isNullOrBlank()
    }
}
