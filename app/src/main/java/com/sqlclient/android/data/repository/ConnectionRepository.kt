package com.sqlclient.android.data.repository

import android.content.Context
import android.net.Uri
import com.sqlclient.android.data.local.dao.ConnectionProfileDao
import com.sqlclient.android.data.local.entity.ConnectionProfileEntity
import com.sqlclient.android.data.remote.ConnectionResult
import com.sqlclient.android.data.remote.MariaDbConnectionManager
import com.sqlclient.android.util.CredentialStore
import com.sqlclient.android.util.DbeaverImport
import com.sqlclient.android.util.ProfileCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val profileDao: ConnectionProfileDao,
    private val connectionManager: MariaDbConnectionManager,
    private val credentialStore: CredentialStore
) {
    fun getAllProfiles(): Flow<List<ConnectionProfileEntity>> = profileDao.getAllProfiles()

    suspend fun getProfileById(id: Long): ConnectionProfileEntity? = profileDao.getProfileById(id)

    fun getProfileByIdFlow(id: Long): Flow<ConnectionProfileEntity?> = profileDao.getProfileByIdFlow(id)

    suspend fun saveProfile(
        profile: ConnectionProfileEntity,
        password: String,
        sshPassword: String? = null,
        sshPassphrase: String? = null
    ): Long {
        val id = profileDao.insertProfile(profile)
        val profileId = if (id == 0L) profile.id else id

        if (password.isNotBlank()) {
            credentialStore.savePassword(profileId, password)
        }

        if (profile.useSshTunnel) {
            sshPassword?.let { credentialStore.saveSshPassword(profileId, it) }
            sshPassphrase?.let { credentialStore.saveSshPassphrase(profileId, it) }
        }

        return profileId
    }

    suspend fun updateProfile(
        profile: ConnectionProfileEntity,
        password: String? = null,
        sshPassword: String? = null,
        sshPassphrase: String? = null
    ) {
        profileDao.updateProfile(profile)

        // Only save password if non-null and non-blank — preserves stored value when user leaves blank
        if (!password.isNullOrBlank()) {
            credentialStore.savePassword(profile.id, password)
        }

        if (profile.useSshTunnel) {
            if (!sshPassword.isNullOrBlank()) {
                credentialStore.saveSshPassword(profile.id, sshPassword)
            }
            if (!sshPassphrase.isNullOrBlank()) {
                credentialStore.saveSshPassphrase(profile.id, sshPassphrase)
            }
        }
    }

    suspend fun deleteProfile(profile: ConnectionProfileEntity) {
        credentialStore.deleteAllCredentials(profile.id)
        profileDao.deleteProfile(profile)
    }

    suspend fun testConnection(profile: ConnectionProfileEntity, password: String, sshPassword: String? = null, sshPassphrase: String? = null): ConnectionResult {
        val actualPassword = if (password.isBlank() && profile.id > 0) {
            credentialStore.getPassword(profile.id) ?: ""
        } else {
            password
        }
        if (actualPassword.isBlank()) {
            return ConnectionResult.Error("Password is required")
        }
        val testProfile = profile.copy(id = 999999)
        credentialStore.savePassword(999999, actualPassword)
        // Snapshot SSH creds for tunnel (form values take precedence over stored)
        if (profile.useSshTunnel) {
            val sshPwd = sshPassword ?: credentialStore.getSshPassword(profile.id)
            val sshPhrase = sshPassphrase ?: credentialStore.getSshPassphrase(profile.id)
            sshPwd?.let { credentialStore.saveSshPassword(999999, it) }
            sshPhrase?.let { credentialStore.saveSshPassphrase(999999, it) }
        }
        val result = connectionManager.connect(testProfile)
        credentialStore.deletePassword(999999)
        credentialStore.deleteSshPassword(999999)
        credentialStore.deleteSshPassphrase(999999)
        connectionManager.disconnect()
        return result
    }

    suspend fun connect(profile: ConnectionProfileEntity, onStatus: ((String) -> Unit)? = null): ConnectionResult {
        // If no password stored yet, error
        val password = credentialStore.getPassword(profile.id)
            ?: return ConnectionResult.Error("Password not found. Please save the connection first.")
        return connectionManager.connect(profile, onStatus)
    }

    fun disconnect() {
        connectionManager.disconnect()
    }

    fun isConnected(): Boolean = connectionManager.isConnected()

    suspend fun ping(): Boolean = connectionManager.ping()

    suspend fun getProfileCount(): Int = profileDao.getProfileCount()

    // Encrypted export/import — master password not stored, file is the SSH-like key
    // Export now includes id for Replace/Skip/Insert + backward compat (file without id → insert)
    suspend fun exportProfilesEncrypted(context: Context, uri: Uri, masterPassword: String): Int = withContext(Dispatchers.IO) {
        if (masterPassword.length < 8) throw IllegalArgumentException("Master password must be at least 8 characters")
        val profiles = profileDao.getAllProfiles().first()
        val arr = JSONArray()
        for (p in profiles) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("host", p.host)
            obj.put("port", p.port)
            obj.put("username", p.username)
            obj.put("password", credentialStore.getPassword(p.id) ?: "")
            obj.put("database", p.database)
            obj.put("color", p.color)
            obj.put("isReadonly", p.isReadonly)
            obj.put("useSshTunnel", p.useSshTunnel)
            obj.put("sshHost", p.sshHost)
            obj.put("sshPort", p.sshPort)
            obj.put("sshUsername", p.sshUsername)
            obj.put("sshPassword", credentialStore.getSshPassword(p.id) ?: "")
            obj.put("sshKeyPath", p.sshKeyPath)
            obj.put("sshPassphrase", credentialStore.getSshPassphrase(p.id) ?: "")
            obj.put("useSsl", p.useSsl)
            arr.put(obj)
        }
        val root = JSONObject()
        root.put("version", 1)
        root.put("exported_at", System.currentTimeMillis())
        root.put("profiles", arr)
        val plain = root.toString().toByteArray(Charsets.UTF_8)
        val enc = ProfileCrypto.encrypt(plain, masterPassword)
        context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(enc) }
        profiles.size
    }

    enum class ImportAction { REPLACE, SKIP, INSERT_AS_NEW }

    data class ParsedProfile(
        val profile: ConnectionProfileEntity,
        val password: String,
        val sshPassword: String,
        val sshPassphrase: String
    )

    data class ImportResult(val imported: Int, val replaced: Int, val skipped: Int)

    data class DbeaverParseOutcome(
        val profiles: List<ParsedProfile>,
        val skipped: Int,
        val withoutPassword: Int,
        val withoutUsername: Int,
        val credentialsLocked: Boolean
    )

    /**
     * Parse MySQL/MariaDB connections from a DBeaver file (`.dbp` or `data-sources.json`).
     * Passwords are included when decryptable (no Master/Project password on the DBeaver side).
     */
    suspend fun parseDbeaverProfiles(context: Context, uri: Uri): DbeaverParseOutcome = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Cannot read file")
        val res = try {
            DbeaverImport.parse(bytes)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a valid DBeaver file: ${e.message}")
        }
        if (res.profiles.isEmpty()) {
            throw IllegalArgumentException(
                if (res.skippedNonMysql > 0) "No MySQL/MariaDB connections in this file (${res.skippedNonMysql} other connections skipped)"
                else "No importable connections in this file"
            )
        }
        val parsed = res.profiles.map { d ->
            ParsedProfile(
                ConnectionProfileEntity(
                    id = 0,
                    name = d.name,
                    host = d.host,
                    port = d.port,
                    database = d.database,
                    username = d.username,
                    isReadonly = d.isReadonly,
                    color = d.color,
                    useSshTunnel = d.useSshTunnel,
                    sshHost = d.sshHost,
                    sshPort = d.sshPort,
                    sshUsername = d.sshUsername
                ),
                d.password,
                d.sshPassword,
                ""
            )
        }
        DbeaverParseOutcome(
            parsed,
            res.skippedNonMysql + res.skippedInvalid,
            parsed.count { it.password.isEmpty() },
            parsed.count { it.profile.username.isEmpty() },
            res.credentialsLocked
        )
    }

    suspend fun decryptAndParseProfiles(context: Context, uri: Uri, masterPassword: String): List<ParsedProfile> = withContext(Dispatchers.IO) {
        if (masterPassword.length < 8) throw IllegalArgumentException("Master password must be at least 8 characters")
        val data = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val plain = try {
            ProfileCrypto.decrypt(data, masterPassword)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("Wrong master password") || msg.contains("corrupt") || msg.contains("BadPadding") || e is javax.crypto.AEADBadTagException) {
                throw IllegalArgumentException("Wrong master password or corrupt file")
            }
            throw IllegalArgumentException(e.message ?: "Decrypt failed")
        }
        val root = JSONObject(String(plain, Charsets.UTF_8))
        val arr = root.optJSONArray("profiles") ?: JSONArray()
        val out = mutableListOf<ParsedProfile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val rawId = if (o.has("id")) o.optLong("id", 0) else 0L
            val profile = ConnectionProfileEntity(
                id = rawId,
                name = o.optString("name", "Imported"),
                host = o.optString("host", ""),
                port = o.optInt("port", 3306),
                username = o.optString("username", ""),
                database = o.optString("database", "").takeIf { it.isNotEmpty() },
                color = o.optString("color", "#2196F3"),
                isReadonly = o.optBoolean("isReadonly", false),
                useSshTunnel = o.optBoolean("useSshTunnel", false),
                sshHost = o.optString("sshHost", "").takeIf { it.isNotEmpty() },
                sshPort = o.optInt("sshPort", 22),
                sshUsername = o.optString("sshUsername", "").takeIf { it.isNotEmpty() },
                sshKeyPath = o.optString("sshKeyPath", "").takeIf { it.isNotEmpty() },
                useSsl = o.optBoolean("useSsl", false)
            )
            if (profile.host.isBlank() || profile.username.isBlank()) continue
            out.add(ParsedProfile(profile, o.optString("password", ""), o.optString("sshPassword", ""), o.optString("sshPassphrase", "")))
        }
        out
    }

    suspend fun applyImportDecision(parsed: ParsedProfile, action: ImportAction): Boolean = withContext(Dispatchers.IO) {
        val p = parsed.profile
        val existing = if (p.id != 0L) profileDao.getProfileById(p.id) else null
        when {
            existing == null -> {
                // No conflict (id missing or not found) → always insert new (backward compat)
                val newId = profileDao.insertProfile(p.copy(id = 0))
                if (parsed.password.isNotEmpty()) credentialStore.savePassword(newId, parsed.password)
                if (parsed.sshPassword.isNotEmpty()) credentialStore.saveSshPassword(newId, parsed.sshPassword)
                if (parsed.sshPassphrase.isNotEmpty()) credentialStore.saveSshPassphrase(newId, parsed.sshPassphrase)
                true
            }
            action == ImportAction.SKIP -> false
            action == ImportAction.REPLACE -> {
                profileDao.insertProfile(p) // REPLACE on PK
                if (parsed.password.isNotEmpty()) credentialStore.savePassword(p.id, parsed.password) else credentialStore.deletePassword(p.id)
                if (parsed.sshPassword.isNotEmpty()) credentialStore.saveSshPassword(p.id, parsed.sshPassword)
                if (parsed.sshPassphrase.isNotEmpty()) credentialStore.saveSshPassphrase(p.id, parsed.sshPassphrase)
                true
            }
            action == ImportAction.INSERT_AS_NEW -> {
                val newId = profileDao.insertProfile(p.copy(id = 0))
                if (parsed.password.isNotEmpty()) credentialStore.savePassword(newId, parsed.password)
                if (parsed.sshPassword.isNotEmpty()) credentialStore.saveSshPassword(newId, parsed.sshPassword)
                if (parsed.sshPassphrase.isNotEmpty()) credentialStore.saveSshPassphrase(newId, parsed.sshPassphrase)
                true
            }
            else -> false
        }
    }

    // Legacy one-shot import keeps behavior = always insert (used if needed elsewhere)
    suspend fun importProfilesEncrypted(context: Context, uri: Uri, masterPassword: String): Int = withContext(Dispatchers.IO) {
        val list = decryptAndParseProfiles(context, uri, masterPassword)
        var imported = 0
        for (parsed in list) {
            val existing = if (parsed.profile.id != 0L) profileDao.getProfileById(parsed.profile.id) else null
            if (existing != null) {
                // Old path had no conflict UI → treat as insert new to avoid surprise overwrite
                val newId = profileDao.insertProfile(parsed.profile.copy(id = 0))
                if (parsed.password.isNotEmpty()) credentialStore.savePassword(newId, parsed.password)
                if (parsed.sshPassword.isNotEmpty()) credentialStore.saveSshPassword(newId, parsed.sshPassword)
                if (parsed.sshPassphrase.isNotEmpty()) credentialStore.saveSshPassphrase(newId, parsed.sshPassphrase)
                imported++
            } else {
                if (applyImportDecision(parsed, ImportAction.INSERT_AS_NEW)) imported++
            }
        }
        imported
    }
}
