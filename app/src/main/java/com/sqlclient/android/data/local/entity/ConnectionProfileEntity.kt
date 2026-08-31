package com.sqlclient.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_profiles")
data class ConnectionProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "host")
    val host: String,

    @ColumnInfo(name = "port")
    val port: Int = 3306,

    @ColumnInfo(name = "database")
    val database: String? = null,

    @ColumnInfo(name = "username")
    val username: String,

    @ColumnInfo(name = "is_readonly")
    val isReadonly: Boolean = false,

    @ColumnInfo(name = "color")
    val color: String = "#2196F3",

    @ColumnInfo(name = "use_ssh_tunnel")
    val useSshTunnel: Boolean = false,

    @ColumnInfo(name = "ssh_host")
    val sshHost: String? = null,

    @ColumnInfo(name = "ssh_port")
    val sshPort: Int = 22,

    @ColumnInfo(name = "ssh_username")
    val sshUsername: String? = null,

    @ColumnInfo(name = "ssh_key_path")
    val sshKeyPath: String? = null,

    @ColumnInfo(name = "use_ssl")
    val useSsl: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
