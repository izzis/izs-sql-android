package com.sqlclient.android.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "sql_client_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun savePassword(profileId: Long, password: String) {
        encryptedPrefs.edit().putString("password_$profileId", password).apply()
    }

    fun getPassword(profileId: Long): String? {
        return encryptedPrefs.getString("password_$profileId", null)
    }

    fun deletePassword(profileId: Long) {
        encryptedPrefs.edit().remove("password_$profileId").apply()
    }

    fun saveSshPassword(profileId: Long, password: String) {
        encryptedPrefs.edit().putString("ssh_password_$profileId", password).apply()
    }

    fun getSshPassword(profileId: Long): String? {
        return encryptedPrefs.getString("ssh_password_$profileId", null)
    }

    fun deleteSshPassword(profileId: Long) {
        encryptedPrefs.edit().remove("ssh_password_$profileId").apply()
    }

    fun saveSshPassphrase(profileId: Long, passphrase: String) {
        encryptedPrefs.edit().putString("ssh_passphrase_$profileId", passphrase).apply()
    }

    fun getSshPassphrase(profileId: Long): String? {
        return encryptedPrefs.getString("ssh_passphrase_$profileId", null)
    }

    fun deleteSshPassphrase(profileId: Long) {
        encryptedPrefs.edit().remove("ssh_passphrase_$profileId").apply()
    }

    fun deleteAllCredentials(profileId: Long) {
        deletePassword(profileId)
        deleteSshPassword(profileId)
        deleteSshPassphrase(profileId)
    }

    fun hasPassword(profileId: Long): Boolean {
        return !getPassword(profileId).isNullOrBlank()
    }
}
