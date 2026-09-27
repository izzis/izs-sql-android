package id.web.izs.sqlclient.util

import id.web.izs.sqlclient.data.local.entity.ConnectionProfileEntity

/**
 * Duplicate detection for profile import (DBeaver + .enc backup).
 *
 * DBeaver profiles are imported with `id = 0`, and a fresh device assigns new
 * auto ids while an .enc import is running, so a live `getProfileById(fileId)`
 * check can neither see DBeaver duplicates nor trust file ids (it can even
 * report a fake conflict against a row this same import just created).
 *
 * Instead, duplicates are matched by profile identity —
 * `name:host:port:user:db` + SSH endpoint (several DBeaver connections often
 * share the same server/credentials and differ only by name or by direct vs
 * tunnelled access) — against a snapshot of the table taken before the import
 * writes anything. The file id is only a fallback against that same snapshot
 * (catches a profile whose endpoint changed since export).
 */
object ImportDedupe {

    /**
     * Profile identity: name/host trimmed + case-insensitive (labels), port,
     * user/db case-sensitive (MySQL semantics), db null == blank, plus the
     * access path — `direct` or `ssh:host:port:user` — so a tunnelled profile
     * never collides with a direct one to the same database.
     */
    fun keyOf(p: ConnectionProfileEntity): String {
        val ssh = if (p.useSshTunnel) {
            "ssh:${p.sshHost.orEmpty().trim().lowercase()}:${p.sshPort}:${p.sshUsername.orEmpty().trim()}"
        } else {
            "direct"
        }
        return "${p.name.trim().lowercase()}:${p.host.trim().lowercase()}:${p.port}:${p.username.trim()}:${p.database.orEmpty().trim()}:$ssh"
    }

    fun findExisting(
        incoming: ConnectionProfileEntity,
        byKey: Map<String, ConnectionProfileEntity>,
        byId: Map<Long, ConnectionProfileEntity>
    ): ConnectionProfileEntity? = byKey[keyOf(incoming)]
        ?: if (incoming.id != 0L) byId[incoming.id] else null
}
