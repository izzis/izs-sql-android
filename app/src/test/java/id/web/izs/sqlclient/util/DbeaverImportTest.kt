package id.web.izs.sqlclient.util

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
      "connection-types": {
        "dev": {"name": "Development", "color": "214,250,207"},
        "prod": {"name": "Production", "color": "250,207,207", "colorDark": "97,61,63"}
      },
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
            "host": "10.0.0.5", "port": "3306", "database": "x", "type": "dev", "color": "#00ff00",
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
    fun resolvesColorsFromTypeAndOverride() {
        val res = DbeaverImport.parse(dataSourcesJson().toByteArray(Charsets.UTF_8))
        // "type": "dev" + connection-types dev "214,250,207" -> #D6FACF
        assertEquals("#D6FACF", res.profiles.first { it.name == "Prod DB" }.color)
        // Per-connection "color" override wins over the type color
        assertEquals("#00FF00", res.profiles.first { it.name == "SSH DB" }.color)
        // No type and no override -> default
        assertEquals(DbeaverImport.DEFAULT_COLOR, res.profiles.first { it.name == "URL DB" }.color)
    }

    @Test
    fun stockTypeColorsBecomePresets() {
        // DBeaver SYSTEM_TYPES stock colors collapse to app preset badges
        // (raw tints would wash out the white badge icon).
        val json = """
        {
          "connection-types": {
            "dev": {"name": "Development", "color": "255,255,255"},
            "test": {"name": "Test", "color": "214,250,207"},
            "prod": {"name": "Production", "color": "250,207,207"}
          },
          "connections": {
            "d": {"provider": "mysql", "name": "Dev", "configuration": {"host": "h1", "type": "dev"}},
            "t": {"provider": "mysql", "name": "Test", "configuration": {"host": "h2", "type": "test"}},
            "p": {"provider": "mysql", "name": "Prod", "configuration": {"host": "h3", "type": "prod"}}
          }
        }
        """.trimIndent()
        val res = DbeaverImport.parse(json.toByteArray(Charsets.UTF_8))
        assertEquals("#5C6BC0", res.profiles.first { it.name == "Dev" }.color)
        assertEquals("#66BB6A", res.profiles.first { it.name == "Test" }.color)
        assertEquals("#EF5350", res.profiles.first { it.name == "Prod" }.color)
    }

    @Test
    fun customColorsPassThrough() {
        // A customized type color or an explicit override is kept verbatim.
        val json = """
        {
          "connection-types": {
            "test": {"name": "Test", "color": "128,0,128"}
          },
          "connections": {
            "custom-type": {"provider": "mysql", "name": "CustomType",
              "configuration": {"host": "h1", "type": "test"}},
            "custom-override": {"provider": "mysql", "name": "CustomOverride",
              "configuration": {"host": "h2", "type": "prod", "color": "#123456"}}
          }
        }
        """.trimIndent()
        val res = DbeaverImport.parse(json.toByteArray(Charsets.UTF_8))
        assertEquals("#800080", res.profiles.first { it.name == "CustomType" }.color)
        assertEquals("#123456", res.profiles.first { it.name == "CustomOverride" }.color)
    }

    @Test
    fun invalidColorsFallBackToDefault() {
        val json = """
        {
          "connections": {
            "bad-triplet": {
              "provider": "mysql", "driver": "mysql8", "name": "Bad",
              "configuration": {"host": "h1", "type": "nope", "color": "999,0,0"}
            },
            "bare-hex": {
              "provider": "mysql", "driver": "mysql8", "name": "Bare",
              "configuration": {"host": "h2", "color": "ff8800"}
            },
            "spaced-triplet": {
              "provider": "mysql", "driver": "mysql8", "name": "Spaced",
              "configuration": {"host": "h3", "color": " 16 , 32 , 48 "}
            }
          }
        }
        """.trimIndent()
        val res = DbeaverImport.parse(json.toByteArray(Charsets.UTF_8))
        assertEquals(DbeaverImport.DEFAULT_COLOR, res.profiles.first { it.name == "Bad" }.color)
        assertEquals("#FF8800", res.profiles.first { it.name == "Bare" }.color)
        assertEquals("#102030", res.profiles.first { it.name == "Spaced" }.color)
    }

    @Test
    fun nearWhiteColorsWithoutTypeFallBackToDefault() {
        // Stock dev white resolves to the dev preset; a near-white color with
        // no standard type behind it still falls back to default.
        val json = """
        {
          "connection-types": {
            "dev": {"name": "Development", "color": "255,255,255"}
          },
          "connections": {
            "white-type": {
              "provider": "mysql", "driver": "mysql8", "name": "WhiteType",
              "configuration": {"host": "h1", "type": "dev"}
            },
            "white-override": {
              "provider": "mysql", "driver": "mysql8", "name": "WhiteOverride",
              "configuration": {"host": "h2", "type": "dev", "color": "#FFFFFF"}
            },
            "near-white": {
              "provider": "mysql", "driver": "mysql8", "name": "NearWhite",
              "configuration": {"host": "h3", "color": "#FEFEFE"}
            }
          }
        }
        """.trimIndent()
        val res = DbeaverImport.parse(json.toByteArray(Charsets.UTF_8))
        assertEquals("#5C6BC0", res.profiles.first { it.name == "WhiteType" }.color)
        assertEquals("#5C6BC0", res.profiles.first { it.name == "WhiteOverride" }.color)
        assertEquals(DbeaverImport.DEFAULT_COLOR, res.profiles.first { it.name == "NearWhite" }.color)
    }

    @Test
    fun rejectsGarbage() {        try {
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
