package com.sqlclient.android.util

import org.json.JSONObject
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Import MySQL/MariaDB connections from DBeaver.
 *
 * Accepts a `.dbp` file (File → Export → DBeaver → Project, a ZIP containing
 * `projects/<name>/.dbeaver/data-sources.json` + `credentials-config.json`)
 * or a raw `data-sources.json` file.
 *
 * Format mapped from DBeaver's open source (Apache-2.0):
 * `DataSourceSerializerModern`/`DataSourceParser` (JSON structure),
 * `DefaultValueEncryptor` (AES-128-CBC, 16-byte IV prepended to ciphertext),
 * `BaseProjectImpl.LOCAL_KEY_CACHE` (fixed key that is public in the source).
 *
 * Passwords are decrypted too, unless the project uses a Master/Project password —
 * DBeaver omits credentials on export in that case
 * (see DBeaver's "Project security" docs).
 */
object DbeaverImport {

    /** BaseProjectImpl.LOCAL_KEY_CACHE — public key in DBeaver's source. */
    private val FIXED_KEY = byteArrayOf(-70, -69, 74, -97, 119, 74, -72, 83, -55, 108, 45, 101, 61, -2, 84, 74)

    private const val MAX_INPUT_BYTES = 64 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    private const val MAX_ZIP_ENTRIES_SCAN = 2000

    data class DbeaverProfile(
        val name: String,
        val host: String,
        val port: Int,
        val database: String?,
        val username: String,
        val password: String,
        val isReadonly: Boolean,
        val useSshTunnel: Boolean,
        val sshHost: String?,
        val sshPort: Int,
        val sshUsername: String?,
        val sshPassword: String
    )

    data class ImportResult(
        val profiles: List<DbeaverProfile>,
        val skippedNonMysql: Int,
        val skippedInvalid: Int,
        /** true when a credentials file exists but fails to decrypt (Project/Master password). */
        val credentialsLocked: Boolean
    )

    /** Quick file-type guess without throwing. */
    fun looksLikeDbeaver(bytes: ByteArray, displayName: String?): Boolean {
        val lower = (displayName ?: "").lowercase()
        if (lower.endsWith(".dbp") || lower.endsWith(".zip")) return true
        if (isZip(bytes)) return zipHasDataSources(bytes)
        if (bytes.size > MAX_ENTRY_BYTES) return false
        val text = bytes.toString(Charsets.UTF_8).trimStart()
        if (!text.startsWith("{")) return false
        return try {
            JSONObject(text).has("connections")
        } catch (_: Exception) {
            false
        }
    }

    fun parse(fileBytes: ByteArray): ImportResult {
        if (fileBytes.size > MAX_INPUT_BYTES) throw IllegalArgumentException("File too large (>64MB)")
        if (fileBytes.isEmpty()) throw IllegalArgumentException("Empty file")
        val sources: List<SourceFile> = if (isZip(fileBytes)) {
            extractFromZip(fileBytes)
        } else {
            val json = try {
                JSONObject(fileBytes.toString(Charsets.UTF_8))
            } catch (_: Exception) {
                throw IllegalArgumentException("Not a valid DBeaver file (.dbp or data-sources.json)")
            }
            if (!json.has("connections")) throw IllegalArgumentException("Not a valid DBeaver file (.dbp or data-sources.json)")
            listOf(SourceFile(json, null, hasCredsFile = false))
        }
        if (sources.isEmpty()) throw IllegalArgumentException("No data-sources.json found in this archive")

        val profiles = mutableListOf<DbeaverProfile>()
        var skippedNonMysql = 0
        var skippedInvalid = 0
        var credentialsLocked = false
        for (src in sources) {
            val r = parseDataSources(src.json, src.creds)
            profiles += r.profiles
            skippedNonMysql += r.skippedNonMysql
            skippedInvalid += r.skippedInvalid
            // Credentials file exists but failed to decrypt (Project/Master password) → passwords skipped.
            credentialsLocked = credentialsLocked || (src.hasCredsFile && src.creds == null)
        }
        return ImportResult(profiles, skippedNonMysql, skippedInvalid, credentialsLocked)
    }

    private fun isZip(bytes: ByteArray): Boolean {
        return bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
    }

    private fun zipHasDataSources(bytes: ByteArray): Boolean {
        try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var count = 0
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > MAX_ZIP_ENTRIES_SCAN) break
                    val name = entry.name.lowercase()
                    val base = name.substringAfterLast("/")
                    if (base.startsWith("data-sources") && base.endsWith(".json")) {
                        return true
                    }
                    zip.closeEntry()
                }
            }
        } catch (_: Exception) {
        }
        return false
    }

    private fun collectZipSources(
        bytes: ByteArray,
        strictDir: Boolean
    ): Pair<Map<String, JSONObject>, Map<String, ByteArray>> {
        val dataSources = mutableMapOf<String, JSONObject>()
        val creds = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > MAX_ZIP_ENTRIES_SCAN) break
                if (!entry.isDirectory) {
                    val lower = entry.name.lowercase()
                    val base = lower.substringAfterLast("/")
                    val dir = lower.substringBeforeLast("/", "")
                    try {
                        if (base.startsWith("data-sources") && base.endsWith(".json") &&
                            (!strictDir || ".dbeaver/" in lower)
                        ) {
                            dataSources[dir] = JSONObject(readEntry(zip).toString(Charsets.UTF_8))
                        } else if (base.startsWith("credentials-config") && base.endsWith(".json")) {
                            creds[dir] = readEntry(zip)
                        } else {
                            zip.closeEntry()
                        }
                    } catch (e: IllegalArgumentException) {
                        throw e
                    } catch (_: Exception) {
                        zip.closeEntry()
                    }
                }
            }
        }
        return dataSources to creds
    }

    private fun readEntry(zip: ZipInputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw IllegalArgumentException("Archive entry too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private data class SourceFile(
        val json: JSONObject,
        val creds: JSONObject?,
        val hasCredsFile: Boolean
    )

    private fun extractFromZip(bytes: ByteArray): List<SourceFile> {
        // Pass 1: layout resmi (.dbeaver/); pass 2: longgar (zip manual berisi data-sources.json).
        val strict = collectZipSources(bytes, strictDir = true)
        val (dataSources, creds) = if (strict.first.isNotEmpty()) strict else collectZipSources(bytes, strictDir = false)
        // Pair per directory; fallback: the single credentials file applies to all.
        val singleCreds = if (creds.size == 1) creds.values.first() else null
        return dataSources.map { (dir, json) ->
            val raw = creds[dir] ?: singleCreds
            val decrypted = raw?.let { decryptCredentials(it) }?.let {
                try {
                    JSONObject(it)
                } catch (_: Exception) {
                    null
                }
            }
            SourceFile(json, decrypted, hasCredsFile = raw != null)
        }
    }

    /**
     * Decrypt `credentials-config.json`. Returns null on failure
     * (project uses a Project/Master password).
     */
    fun decryptCredentials(encrypted: ByteArray): String? {
        if (encrypted.size <= 16) return null
        return try {
            val iv = encrypted.copyOfRange(0, 16)
            val cipherBytes = encrypted.copyOfRange(16, encrypted.size)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(FIXED_KEY, "AES"), IvParameterSpec(iv))
            cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDataSources(dataSources: JSONObject, creds: JSONObject?): ImportResult {
        val connections = dataSources.optJSONObject("connections") ?: return ImportResult(emptyList(), 0, 0, false)
        val profiles = mutableListOf<DbeaverProfile>()
        var skippedNonMysql = 0
        var skippedInvalid = 0
        for (id in connections.keys()) {
            val conn = connections.optJSONObject(id) ?: continue
            val provider = conn.optString("provider", "")
            val driver = conn.optString("driver", "")
            val cfg = conn.optJSONObject("configuration") ?: JSONObject()
            val url = cfg.optString("url", "")
            val isMysql = provider.equals("mysql", true) || provider.equals("mariadb", true) ||
                driver.contains("mysql", true) || driver.contains("maria", true) ||
                url.startsWith("jdbc:mysql:", true) || url.startsWith("jdbc:mariadb:", true)
            if (!isMysql) {
                skippedNonMysql++
                continue
            }

            var host = cfg.optString("host", "")
            var port = cfg.optString("port", "").toIntOrNull()
            var database = cfg.optString("database", "").takeIf { it.isNotEmpty() }
            val useUrl = cfg.optString("configurationType", "MANUAL").equals("URL", true) || host.isBlank()
            if (useUrl && url.isNotBlank()) {
                val parsed = parseJdbcUrl(url)
                if (parsed != null) {
                    if (host.isBlank()) host = parsed.host
                    if (port == null) port = parsed.port
                    if (database == null) database = parsed.database
                }
            }

            val secureConn = creds?.optJSONObject(id)?.optJSONObject("#connection")
            val authProps = cfg.optJSONObject("auth-properties")
            val username = secureConn?.optString("user", "").orEmpty().ifEmpty {
                cfg.optString("user", "").ifEmpty {
                    authProps?.optString("user", "").orEmpty().ifEmpty {
                        authProps?.optString("name", "").orEmpty()
                    }
                }
            }
            val password = secureConn?.optString("password", "").orEmpty().ifEmpty {
                cfg.optString("password", "").ifEmpty {
                    authProps?.optString("password", "").orEmpty()
                }
            }
            if (host.isBlank()) {
                skippedInvalid++
                continue
            }

            val ssh = cfg.optJSONObject("handlers")?.optJSONObject("ssh_tunnel")
            val sshEnabled = ssh?.optBoolean("enabled", false) == true
            val sshProps = ssh?.optJSONObject("properties")
            val sshHost = sshProps?.optString("host", "").orEmpty()
            val netSec = creds?.optJSONObject(id)?.optJSONObject("network/ssh_tunnel")
            val sshUser = netSec?.optString("user", "").orEmpty().ifEmpty {
                ssh?.optString("user", "").orEmpty().ifEmpty { sshProps?.optString("user", "").orEmpty() }
            }
            val sshPass = netSec?.optString("password", "").orEmpty().ifEmpty {
                ssh?.optString("password", "").orEmpty()
            }

            val name = conn.optString("name", "").ifBlank {
                if (username.isNotBlank()) "$username@$host" else host
            }
            profiles.add(
                DbeaverProfile(
                    name = name,
                    host = host,
                    port = port ?: 3306,
                    database = database,
                    username = username,
                    password = password,
                    isReadonly = conn.optBoolean("read-only", false),
                    useSshTunnel = sshEnabled && sshHost.isNotBlank(),
                    sshHost = sshHost.takeIf { it.isNotBlank() },
                    sshPort = sshProps?.optString("port", "").orEmpty().toIntOrNull() ?: 22,
                    sshUsername = sshUser.takeIf { it.isNotBlank() },
                    sshPassword = sshPass
                )
            )
        }
        return ImportResult(profiles, skippedNonMysql, skippedInvalid, false)
    }

    private data class JdbcParts(val host: String, val port: Int?, val database: String?)

    private val JDBC_URL = Regex("^jdbc:(?:mysql|mariadb)://([^/:?]+)(?::(\\d+))?/?([^?]*)?", RegexOption.IGNORE_CASE)

    private fun parseJdbcUrl(url: String): JdbcParts? {
        val m = JDBC_URL.find(url.trim()) ?: return null
        val host = m.groupValues[1]
        if (host.isBlank()) return null
        return JdbcParts(
            host = host,
            port = m.groupValues[2].toIntOrNull(),
            database = m.groupValues[3].substringBefore("?").takeIf { it.isNotEmpty() }
        )
    }
}
