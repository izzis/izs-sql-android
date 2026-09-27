package id.web.izs.sqlclient.util

import id.web.izs.sqlclient.data.local.entity.ConnectionProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ImportDedupeTest {

    private fun profile(
        id: Long = 0,
        name: String = "Local",
        host: String = "db.example.com",
        port: Int = 3306,
        username: String = "root",
        database: String? = null,
        useSshTunnel: Boolean = false,
        sshHost: String? = null,
        sshPort: Int = 22,
        sshUsername: String? = null
    ) = ConnectionProfileEntity(
        id = id, name = name, host = host, port = port, username = username, database = database,
        useSshTunnel = useSshTunnel, sshHost = sshHost, sshPort = sshPort, sshUsername = sshUsername
    )

    @Test
    fun `same identity matches regardless of name and host case or padding`() {
        val a = profile(name = "  App3 ", host = "DB.Example.COM")
        val b = profile(name = "app3", host = "db.example.com")
        assertEquals(ImportDedupe.keyOf(a), ImportDedupe.keyOf(b))
    }

    @Test
    fun `different names on the same server are not duplicates`() {
        // Regression: a DBeaver project often has app1/app2/app3 → same
        // host:port:user, empty database; they must not collide.
        assertNotEquals(
            ImportDedupe.keyOf(profile(name = "app1")),
            ImportDedupe.keyOf(profile(name = "app2"))
        )
    }

    @Test
    fun `direct and tunnelled profile to the same database do not collide`() {
        val direct = profile(name = "prod")
        val tunneled = profile(name = "prod", useSshTunnel = true, sshHost = "jump.example.com", sshUsername = "deploy")
        assertNotEquals(ImportDedupe.keyOf(direct), ImportDedupe.keyOf(tunneled))
    }

    @Test
    fun `different ssh jump hosts do not collide`() {
        val a = profile(name = "prod", useSshTunnel = true, sshHost = "jump-a.example.com", sshUsername = "deploy")
        val b = profile(name = "prod", useSshTunnel = true, sshHost = "jump-b.example.com", sshUsername = "deploy")
        assertNotEquals(ImportDedupe.keyOf(a), ImportDedupe.keyOf(b))
    }

    @Test
    fun `port username and database distinguish keys`() {
        val base = profile()
        assertNotEquals(ImportDedupe.keyOf(base), ImportDedupe.keyOf(profile(port = 3307)))
        assertNotEquals(ImportDedupe.keyOf(base), ImportDedupe.keyOf(profile(username = "admin")))
        assertNotEquals(ImportDedupe.keyOf(base), ImportDedupe.keyOf(profile(database = "appdb")))
        // username/database keep case (MySQL semantics)
        assertNotEquals(ImportDedupe.keyOf(profile(username = "root")), ImportDedupe.keyOf(profile(username = "Root")))
    }

    @Test
    fun `null database equals blank database`() {
        assertEquals(ImportDedupe.keyOf(profile(database = null)), ImportDedupe.keyOf(profile(database = "  ")))
    }

    @Test
    fun `findExisting prefers key match over id`() {
        val incoming = profile(id = 99, name = "Renamed")
        val byKey = mapOf(ImportDedupe.keyOf(incoming) to profile(id = 7, name = "Renamed", host = "other.example.com"))
        val byId = mapOf(99L to profile(id = 99, name = "Renamed"))
        assertSame(byKey.values.first(), ImportDedupe.findExisting(incoming, byKey, byId))
    }

    @Test
    fun `findExisting falls back to id when profile was renamed`() {
        val incoming = profile(id = 42, name = "New name")
        val byId = mapOf(42L to profile(id = 42, name = "Old name"))
        assertSame(byId.getValue(42L), ImportDedupe.findExisting(incoming, emptyMap(), byId))
    }

    @Test
    fun `DBeaver profile with id 0 matches existing by key`() {
        val existing = profile(id = 5, name = "Prod")
        val incoming = profile(id = 0, name = "Prod")
        assertSame(
            existing,
            ImportDedupe.findExisting(incoming, mapOf(ImportDedupe.keyOf(existing) to existing), emptyMap())
        )
    }

    @Test
    fun `id 0 never matches the id map`() {
        val byId = mapOf(0L to profile(id = 0))
        assertNull(ImportDedupe.findExisting(profile(id = 0), emptyMap(), byId))
    }

    @Test
    fun `fresh import into empty snapshot finds nothing`() {
        assertNull(ImportDedupe.findExisting(profile(id = 3), emptyMap(), emptyMap()))
    }
}
