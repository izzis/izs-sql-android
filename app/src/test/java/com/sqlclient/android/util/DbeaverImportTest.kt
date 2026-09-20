package com.sqlclient.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class DbeaverImportTest {

    // Same key as DBeaver (BaseProjectImpl.LOCAL_KEY_CACHE) — fixture only.
    private val key = byteArrayOf(-70, -69, 74, -97, 119, 74, -72, 83, -55, 108, 45, 101, 61, -2, 84, 74)

    private fun encryptCreds(json: String): ByteArray {
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return iv + cipher.doFinal(json.toByteArray(Charsets.UTF_8))
    }

    private fun dataSourcesJson(): String = """
    {
      "folders": {},
      "connections": {
        "mysql-abc": {
          "provider": "mysql", "driver": "mysql8", "name": "Prod DB",
          "save-password": true, "read-only": true,
          "configuration": {
            "host": "db.example.com", "port": "3307", "database": "shop",
            "configurationType": "MANUAL", "type": "dev", "auth-model": "native"
          }
        },
        "pg-1": {
          "provider": "postgresql", "driver": "postgres-jdbc", "name": "PG",
          "configuration": {"host": "pg.local", "port": "5432", "database": "app"}
        },
        "mysql-url": {
          "provider": "mysql", "driver": "mysql8", "name": "URL DB",
          "configuration": {"configurationType": "URL", "url": "jdbc:mysql://urlhost:3310/url_db?useSSL=false"}
        },
        "mysql-ssh": {
          "provider": "mysql", "driver": "mysql8", "name": "SSH DB",
          "configuration": {
            "host": "10.0.0.5", "port": "3306", "database": "x",
            "handlers": {"ssh_tunnel": {"enabled": true, "save-password": true,
              "properties": {"host": "bastion.example.com", "port": 22, "user": "deploy", "authType": "PASSWORD"}}}
          }
        }
      }
    }
    """.trimIndent()

    private fun credsJson(): String = """
    {
      "mysql-abc": {"#connection": {"user": "shop_admin", "password": "s3cret"}},
      "mysql-url": {"#connection": {"user": "urluser"}},
      "mysql-ssh": {"#connection": {"user": "app", "password": "apppw"},
                    "network/ssh_tunnel": {"user": "deploy", "password": "sshpw"}}
    }
    """.trimIndent()

    private fun dbpBytes(sources: String, creds: ByteArray?): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("meta.xml"))
            zip.write("<archive/>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("projects/General/"))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("projects/General/.dbeaver/data-sources.json"))
            zip.write(sources.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            if (creds != null) {
                zip.putNextEntry(ZipEntry("projects/General/.dbeaver/credentials-config.json"))
                zip.write(creds)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun decryptRoundTrip() {
        val plain = """{"a":{"#connection":{"user":"u","password":"p"}}}"""
        val enc = encryptCreds(plain)
        // IV 16 byte di depan ciphertext
        assertEquals(plain, DbeaverImport.decryptCredentials(enc))
        assertNull(DbeaverImport.decryptCredentials(ByteArray(10)))
        assertNull(DbeaverImport.decryptCredentials("bukan-cipher".toByteArray()))
    }

    @Test
    fun parseRawJsonWithoutCreds() {
        // Raw JSON without credentials-config: DBeaver keeps username/password in its
        // secure storage, so profiles come in with empty credentials to be completed manually.
        val res = DbeaverImport.parse(dataSourcesJson().toByteArray(Charsets.UTF_8))
        assertEquals(3, res.profiles.size)
        assertEquals(1, res.skippedNonMysql)
        assertEquals(0, res.skippedInvalid)
        assertFalse(res.credentialsLocked)
        assertTrue(res.profiles.all { it.username.isEmpty() && it.password.isEmpty() })

        val prod = res.profiles.first { it.name == "Prod DB" }
        assertEquals("db.example.com", prod.host)
        assertEquals(3307, prod.port)
        assertEquals("shop", prod.database)
        assertTrue(prod.isReadonly)
    }

    @Test
    fun parseDbpZipWithPasswords() {
        val res = DbeaverImport.parse(dbpBytes(dataSourcesJson(), encryptCreds(credsJson())))
        assertEquals(3, res.profiles.size)
        assertEquals(1, res.skippedNonMysql)
        assertFalse(res.credentialsLocked)

        val prod = res.profiles.first { it.name == "Prod DB" }
        assertEquals("shop_admin", prod.username)
        assertEquals("s3cret", prod.password)

        val urlDb = res.profiles.first { it.name == "URL DB" }
        assertEquals("urlhost", urlDb.host)
        assertEquals(3310, urlDb.port)
        assertEquals("url_db", urlDb.database)
        assertEquals("urluser", urlDb.username)
        assertEquals("", urlDb.password)

        val ssh = res.profiles.first { it.name == "SSH DB" }
        assertTrue(ssh.useSshTunnel)
        assertEquals("bastion.example.com", ssh.sshHost)
        assertEquals(22, ssh.sshPort)
        assertEquals("deploy", ssh.sshUsername)
        assertEquals("sshpw", ssh.sshPassword)
    }

    @Test
    fun parseDbpZipLockedCreds() {
        val res = DbeaverImport.parse(dbpBytes(dataSourcesJson(), "acak-tak-terdekripsi".toByteArray()))
        assertTrue(res.credentialsLocked)
        // Profiles still come in without passwords
        assertTrue(res.profiles.all { it.password.isEmpty() && it.sshPassword.isEmpty() })
    }

    @Test
    fun rejectsGarbage() {
        try {
            DbeaverImport.parse("hello world".toByteArray())
            fail("must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("DBeaver"))
        }
        try {
            DbeaverImport.parse(ByteArray(0))
            fail("must throw")
        } catch (_: IllegalArgumentException) {
        }
        assertFalse(DbeaverImport.looksLikeDbeaver("hello".toByteArray(), "backup.enc"))
        assertTrue(DbeaverImport.looksLikeDbeaver("hello".toByteArray(), "General.dbp"))
        assertTrue(
            DbeaverImport.looksLikeDbeaver(
                dataSourcesJson().toByteArray(Charsets.UTF_8),
                "data-sources.json"
            )
        )
    }
}
