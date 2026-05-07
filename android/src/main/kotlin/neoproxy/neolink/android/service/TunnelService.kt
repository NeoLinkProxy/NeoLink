package neoproxy.neolink.android.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import neoproxy.neolink.android.BuildConfig
import neoproxy.neolink.android.MainActivity
import neoproxy.neolink.android.NeoLinkApp
import neoproxy.neolink.android.R
import top.ceroxe.api.neolink.NeoLinkAPI
import top.ceroxe.api.neolink.NeoLinkCfg
import top.ceroxe.api.neolink.NeoLinkState
import java.net.InetSocketAddress

/**
 * 隧道前台 Service。
 *
 * 采用 Bound + Started 混合模式：
 * - Started: 保证 Service 生命周期独立于 Activity，隧道不因 Activity 销毁而中断
 * - Bound: Activity 可直接访问 Service 实例获取实时状态流
 */
class TunnelService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EXTRA_REMOTE_DOMAIN = "remote_domain"
        const val EXTRA_HOOK_PORT = "hook_port"
        const val EXTRA_CONNECT_PORT = "connect_port"
        const val EXTRA_KEY = "key"
        const val EXTRA_LOCAL_PORT = "local_port"
        const val EXTRA_TCP_ENABLED = "tcp_enabled"
        const val EXTRA_UDP_ENABLED = "udp_enabled"
        const val EXTRA_PPV2_ENABLED = "ppv2_enabled"
        const val EXTRA_AUTO_RECONNECT = "auto_reconnect"
        const val EXTRA_HEARTBEAT_DELAY = "heartbeat_delay"

        fun createStartIntent(context: Context, cfg: TunnelConfig): Intent {
            return Intent(context, TunnelService::class.java).apply {
                action = "START"
                putExtra(EXTRA_REMOTE_DOMAIN, cfg.remoteDomain)
                putExtra(EXTRA_HOOK_PORT, cfg.hookPort)
                putExtra(EXTRA_CONNECT_PORT, cfg.connectPort)
                putExtra(EXTRA_KEY, cfg.key)
                putExtra(EXTRA_LOCAL_PORT, cfg.localPort)
                putExtra(EXTRA_TCP_ENABLED, cfg.tcpEnabled)
                putExtra(EXTRA_UDP_ENABLED, cfg.udpEnabled)
                putExtra(EXTRA_PPV2_ENABLED, cfg.ppv2Enabled)
                putExtra(EXTRA_AUTO_RECONNECT, cfg.autoReconnect)
                putExtra(EXTRA_HEARTBEAT_DELAY, cfg.heartbeatDelay)
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, TunnelService::class.java).apply { action = "STOP" }
        }
    }

    private val binder = TunnelBinder()
    private val apiLock = Any()
    private var api: NeoLinkAPI? = null
    private var tunnelJob: Job? = null
    private var reconnectRequested = false
    // SupervisorJob 保证子协程异常不会取消整个 scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(NeoLinkState.STOPPED)
    val state: StateFlow<NeoLinkState> = _state

    private val _tunnelAddress = MutableStateFlow<String?>(null)
    val tunnelAddress: StateFlow<String?> = _tunnelAddress

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    inner class TunnelBinder : Binder() {
        val service: TunnelService get() = this@TunnelService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                val cfg = extractConfig(intent)
                startTunnel(cfg)
            }
            "STOP" -> stopTunnel()
        }
        return START_NOT_STICKY
    }

    private fun extractConfig(intent: Intent): TunnelConfig {
        return TunnelConfig(
            intent.getStringExtra(EXTRA_REMOTE_DOMAIN) ?: "",
            intent.getIntExtra(EXTRA_HOOK_PORT, 0),
            intent.getIntExtra(EXTRA_CONNECT_PORT, 0),
            intent.getStringExtra(EXTRA_KEY) ?: "",
            intent.getIntExtra(EXTRA_LOCAL_PORT, 0),
            intent.getBooleanExtra(EXTRA_TCP_ENABLED, true),
            intent.getBooleanExtra(EXTRA_UDP_ENABLED, true),
            intent.getBooleanExtra(EXTRA_PPV2_ENABLED, false),
            intent.getBooleanExtra(EXTRA_AUTO_RECONNECT, true),
            intent.getIntExtra(EXTRA_HEARTBEAT_DELAY, 1000)
        )
    }

    private fun startTunnel(config: TunnelConfig) {
        if (tunnelJob?.isActive == true || api != null) {
            appendLog("[INFO] 隧道已在运行或连接中，忽略重复启动请求")
            return
        }
        reconnectRequested = config.autoReconnect
        _lastError.value = null
        try {
            startForeground(NOTIFICATION_ID, buildNotification("正在连接..."))
        } catch (e: Exception) {
            val message = serviceError("启动前台服务失败", e)
            appendLog("[FATAL] $message")
            _lastError.value = message
            _state.value = NeoLinkState.FAILED
            stopSelf()
            return
        }

        tunnelJob = scope.launch {
            do {
                val shouldRetry = runTunnelOnce(config)
                if (shouldRetry) {
                    appendLog("[INFO] 5 秒后自动重连")
                    delay(5000)
                }
            } while (shouldRetry && reconnectRequested)

            if (api == null && _state.value != NeoLinkState.FAILED) {
                _state.value = NeoLinkState.STOPPED
            }
        }
    }

    private suspend fun runTunnelOnce(config: TunnelConfig): Boolean {
        return try {
            val tunnel = NeoLinkAPI(config.toNeoLinkCfg()).bindAndroidLog("NeoLink")
            synchronized(apiLock) {
                api = tunnel
            }

            tunnel.setOnStateChanged { newState ->
                _state.value = newState
                updateNotification(newState.name)
            }
            tunnel.setOnError { msg, _ ->
                appendLog("[ERROR] $msg")
                _lastError.value = msg
            }
            tunnel.setOnServerMessage { msg ->
                appendLog("[SERVER] $msg")
            }
            tunnel.setOnConnect { proto, src, _ ->
                appendLog("[+] ${protocolName(proto)} ${formatAddress(src)}")
            }
            tunnel.setOnDisconnect { proto, src, _ ->
                appendLog("[-] ${protocolName(proto)} ${formatAddress(src)}")
            }

            tunnel.start()

            // getTunAddr() 会阻塞直到隧道就绪并返回映射地址
            val addr = tunnel.getTunAddr()
            _tunnelAddress.value = addr
            appendLog("[INFO] 隧道地址: $addr")
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            appendLog("[FATAL] $message")
            _lastError.value = message
            _state.value = NeoLinkState.FAILED
            updateNotification("连接失败: $message")
            closeApiQuietly()
            _tunnelAddress.value = null
            config.autoReconnect && reconnectRequested
        }
    }

    private fun stopTunnel() {
        reconnectRequested = false
        tunnelJob?.cancel()
        tunnelJob = null
        scope.launch {
            closeApiQuietly()
            _state.value = NeoLinkState.STOPPED
            _tunnelAddress.value = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 运行时热切换协议标志，无需重连 */
    fun updateProtocolFlags(tcp: Boolean, udp: Boolean): String? {
        return try {
            synchronized(apiLock) {
                api?.updateRuntimeProtocolFlags(tcp, udp)
            }
            null
        } catch (e: Exception) {
            val message = serviceError("运行时协议更新失败", e)
            appendLog("[ERROR] $message")
            _lastError.value = message
            message
        }
    }

    /** 运行时热切换 PPv2 透传；NeoLinkAPI 7.1.12 会更新运行期配置，影响之后新建的 TCP 连接。 */
    fun updatePpv2(enabled: Boolean): String? {
        return try {
            synchronized(apiLock) {
                val activeApi = api ?: return "运行时 PPv2 更新失败: NeoLinkAPI 实例不可用"
                activeApi.setPPV2Enabled(enabled)
            }
            null
        } catch (e: Exception) {
            val message = serviceError("运行时 PPv2 更新失败", e)
            appendLog("[ERROR] $message")
            _lastError.value = message
            message
        }
    }

    private fun appendLog(message: String) {
        val current = _logs.value
        // 限制 500 条防止内存无限增长，超限时丢弃最旧的 100 条
        val updated = if (current.size >= 500) {
            current.drop(100) + message
        } else {
            current + message
        }
        _logs.value = updated
    }

    private fun updateNotification(status: String) {
        try {
            val notification = buildNotification(status)
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            val message = serviceError("更新前台通知失败", e)
            appendLog("[ERROR] $message")
            _lastError.value = message
        }
    }

    private fun closeApiQuietly() {
        synchronized(apiLock) {
            try {
                api?.close()
            } catch (_: Exception) {
                // close 失败不影响停止/失败收尾；继续清空引用，避免后续点击误判仍在运行。
            } finally {
                api = null
            }
        }
    }

    private fun protocolName(protocol: NeoLinkAPI.TransportProtocol?): String {
        return protocol?.name ?: "UNKNOWN"
    }

    private fun formatAddress(address: InetSocketAddress?): String {
        if (address == null) {
            return "unknown"
        }
        val host = address.address?.hostAddress ?: address.hostString ?: "unknown"
        return "$host:${address.port}"
    }

    private fun serviceError(action: String, e: Exception): String {
        return "$action: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NeoLinkApp.CHANNEL_TUNNEL)
            .setContentTitle("NeoLink")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_tunnel)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        reconnectRequested = false
        scope.cancel()
        closeApiQuietly()
        super.onDestroy()
    }
}

/**
 * 简洁的隧道配置数据类，用于 Intent 参数传递。
 * 与 NeoLinkCfg 解耦——NeoLinkCfg 是 API 内部类型，不应暴露给 UI 层。
 */
data class TunnelConfig(
    val remoteDomain: String,
    val hookPort: Int,
    val connectPort: Int,
    val key: String,
    val localPort: Int,
    val tcpEnabled: Boolean = true,
    val udpEnabled: Boolean = true,
    val ppv2Enabled: Boolean = false,
    val autoReconnect: Boolean = true,
    val heartbeatDelay: Int = 1000
) {
    fun toNeoLinkCfg(): NeoLinkCfg {
        val cfg = NeoLinkCfg(remoteDomain, hookPort, connectPort, key, localPort)
        cfg.setTCPEnabled(tcpEnabled)
        cfg.setUDPEnabled(udpEnabled)
        cfg.setPPV2Enabled(ppv2Enabled)
        cfg.setHeartBeatPacketDelay(heartbeatDelay)
        cfg.setClientVersion(BuildConfig.VERSION_NAME)
        return cfg
    }
}
