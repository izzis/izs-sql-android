package id.web.izs.sqlclient.data.remote

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshTunnelManager @Inject constructor() {
    private var session: Session? = null
    private var assignedLocalPort: Int = 0
    private var remoteHost: String = ""
    private var remotePort: Int = 0

    data class TunnelInfo(val localPort: Int)

    sealed class SshTestResult {
        data object Success : SshTestResult()
        data class Error(val message: String) : SshTestResult()
    }

    suspend fun testSshConnection(
        sshHost: String,
        sshPort: Int,
        sshUsername: String,
        sshPassword: String?,
        sshKeyPath: String?,
        sshPassphrase: String?
    ): SshTestResult = withContext(Dispatchers.IO) {
        var testSession: Session? = null
        try {
            val jsch = JSch()

            if (!sshKeyPath.isNullOrBlank()) {
                val keyFile = File(sshKeyPath)
                if (!keyFile.exists()) {
                    return@withContext SshTestResult.Error("SSH key file not found: $sshKeyPath")
                }
                if (!sshPassphrase.isNullOrBlank()) {
                    jsch.addIdentity(sshKeyPath, sshPassphrase.toByteArray())
                } else {
                    jsch.addIdentity(sshKeyPath)
                }
            }

            testSession = jsch.getSession(sshUsername, sshHost, sshPort)

            if (!sshPassword.isNullOrBlank()) {
                testSession.setPassword(sshPassword.toByteArray())
            }

            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("PreferredAuthentications", "publickey,password")
            }
            testSession.setConfig(config)
            testSession.connect(10000)

            SshTestResult.Success
        } catch (e: Exception) {
            SshTestResult.Error(e.message ?: "Unknown SSH connection error")
        } finally {
            try {
                testSession?.disconnect()
            } catch (_: Exception) {}
        }
    }

    suspend fun openTunnel(
        sshHost: String,
        sshPort: Int,
        sshUsername: String,
        sshPassword: String?,
        sshKeyPath: String?,
        sshPassphrase: String?,
        remoteHost: String,
        remotePort: Int
    ): TunnelInfo = withContext(Dispatchers.IO) {
        closeTunnel()

        val jsch = JSch()

        if (!sshKeyPath.isNullOrBlank()) {
            val keyFile = File(sshKeyPath)
            if (!keyFile.exists()) {
                throw IllegalArgumentException("SSH key file not found: $sshKeyPath")
            }
            if (!sshPassphrase.isNullOrBlank()) {
                jsch.addIdentity(sshKeyPath, sshPassphrase.toByteArray())
            } else {
                jsch.addIdentity(sshKeyPath)
            }
        }

        val sess = jsch.getSession(sshUsername, sshHost, sshPort)

        if (!sshPassword.isNullOrBlank()) {
            sess.setPassword(sshPassword.toByteArray())
        }

        val config = Properties().apply {
            put("StrictHostKeyChecking", "no")
            put("PreferredAuthentications", "publickey,password")
            put("ForwardX11", "no")
            // Keepalive every 15s: stops sshd / NAT / firewall from killing idle tunnels.
            // Without it an idle tunnel gets dropped (~30s on many servers) and JDBC dies with it.
            // JSch reads config values as String, so use "15000" not a number.
            put("ServerAliveInterval", "15000")
            put("ServerAliveCountMax", "3")
        }
        sess.setConfig(config)
        sess.connect(10000)

        val localPort = findFreePort()

        sess.setPortForwardingL("127.0.0.1", localPort, remoteHost, remotePort)

        this@SshTunnelManager.session = sess
        this@SshTunnelManager.assignedLocalPort = localPort
        this@SshTunnelManager.remoteHost = remoteHost
        this@SshTunnelManager.remotePort = remotePort

        TunnelInfo(localPort = localPort)
    }

    fun closeTunnel() {
        try {
            if (assignedLocalPort > 0) {
                session?.delPortForwardingL("127.0.0.1", assignedLocalPort)
            }
        } catch (_: Exception) {}
        try {
            session?.disconnect()
        } catch (_: Exception) {}
        session = null
        assignedLocalPort = 0
    }

    fun isConnected(): Boolean {
        return try {
            session?.isConnected == true
        } catch (_: Exception) {
            false
        }
    }

    private fun findFreePort(): Int {
        val serverSocket = java.net.ServerSocket(0)
        val port = serverSocket.localPort
        serverSocket.close()
        return port
    }
}
