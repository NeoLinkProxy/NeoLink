package neoproxy.neolink.android.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neoproxy.neolink.android.service.TunnelService
import neoproxy.neolink.config.NodeConfig
import top.ceroxe.api.neolink.NeoNode
import top.ceroxe.api.neolink.NodeFetcher
import top.ceroxe.api.neolink.NeoLinkState
import java.io.File
import java.util.Properties

/**
 * 隧道 ViewModel：桥接 UI 与 Service。
 *
 * 使用 AndroidViewModel 而非普通 ViewModel，因为需要 Application context
 * 来绑定/启动 Service（避免持有 Activity 引用导致泄漏）。
 */
class TunnelViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "neolink_tunnel_settings"
        private const val KEY_REMOTE_DOMAIN = "remote_domain"
        private const val KEY_HOOK_PORT = "hook_port"
        private const val KEY_CONNECT_PORT = "connect_port"
        private const val KEY_ACCESS_KEY = "access_key"
        private const val KEY_LOCAL_PORT = "local_port"
        private const val KEY_TCP_ENABLED = "tcp_enabled"
        private const val KEY_UDP_ENABLED = "udp_enabled"
        private const val KEY_PPV2_ENABLED = "ppv2_enabled"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_HEARTBEAT_DELAY = "heartbeat_delay"
        private const val KEY_NODE_LIST_URL = "node_list_url"
        private const val KEY_SELECTED_NODE_ID = "selected_node_id"
        private const val ASSET_APP_PROPERTIES = "app.properties"
        private const val ASSET_NODE_LIST = "nodes.json"
        private const val PROP_NODE_LIST_URL = "nkm.nodelist.url"
    }

    // ==================== UI 状态（只读暴露） ====================

    private val _tunnelState = MutableStateFlow(NeoLinkState.STOPPED)
    val tunnelState: StateFlow<NeoLinkState> = _tunnelState.asStateFlow()

    private val _tunnelAddress = MutableStateFlow<String?>(null)
    val tunnelAddress: StateFlow<String?> = _tunnelAddress.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // ==================== 连接参数表单状态 ====================

    val remoteDomain = MutableStateFlow("")
    val hookPort = MutableStateFlow("44801")
    val connectPort = MutableStateFlow("44802")
    val accessKey = MutableStateFlow("")
    val localPort = MutableStateFlow("")

    // ==================== 功能开关 ====================

    val tcpEnabled = MutableStateFlow(true)
    val udpEnabled = MutableStateFlow(true)
    val ppv2Enabled = MutableStateFlow(false)
    val autoReconnect = MutableStateFlow(true)
    val heartbeatDelay = MutableStateFlow("1000")

    // ==================== 公共节点 ====================

    val nodeListUrl = MutableStateFlow("")
    private val _nodes = MutableStateFlow<List<NeoNode>>(emptyList())
    val nodes: StateFlow<List<NeoNode>> = _nodes.asStateFlow()

    private val _nodeFetchInProgress = MutableStateFlow(false)
    val nodeFetchInProgress: StateFlow<Boolean> = _nodeFetchInProgress.asStateFlow()

    private val _nodeFetchMessage = MutableStateFlow<String?>(null)
    val nodeFetchMessage: StateFlow<String?> = _nodeFetchMessage.asStateFlow()

    private val prefs: SharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val nodeFile: File = File(application.filesDir, NodeConfig.NODE_LIST_FILE_NAME)

    // ==================== 节点选择 ====================

    private val _selectedNodeId = MutableStateFlow<String?>(null)
    val selectedNodeId: StateFlow<String?> = _selectedNodeId.asStateFlow()

    // ==================== Service 绑定 ====================

    private var service: TunnelService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val tunnelBinder = binder as? TunnelService.TunnelBinder ?: run {
                reportUiError("服务绑定失败：Binder 类型不匹配")
                return
            }
            service = tunnelBinder.service
            bound = true
            collectServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    init {
        loadSettings()
        loadBundledDefaults()
        observeSettings()
        bindService()
        fetchNodes()
    }

    private fun loadSettings() {
        remoteDomain.value = prefs.getString(KEY_REMOTE_DOMAIN, remoteDomain.value).orEmpty()
        hookPort.value = prefs.getString(KEY_HOOK_PORT, hookPort.value).orEmpty()
        connectPort.value = prefs.getString(KEY_CONNECT_PORT, connectPort.value).orEmpty()
        accessKey.value = prefs.getString(KEY_ACCESS_KEY, accessKey.value).orEmpty()
        localPort.value = prefs.getString(KEY_LOCAL_PORT, localPort.value).orEmpty()
        tcpEnabled.value = prefs.getBoolean(KEY_TCP_ENABLED, tcpEnabled.value)
        udpEnabled.value = prefs.getBoolean(KEY_UDP_ENABLED, udpEnabled.value)
        ppv2Enabled.value = prefs.getBoolean(KEY_PPV2_ENABLED, ppv2Enabled.value)
        autoReconnect.value = prefs.getBoolean(KEY_AUTO_RECONNECT, autoReconnect.value)
        heartbeatDelay.value = prefs.getString(KEY_HEARTBEAT_DELAY, heartbeatDelay.value).orEmpty()
        nodeListUrl.value = prefs.getString(KEY_NODE_LIST_URL, nodeListUrl.value).orEmpty()
        _selectedNodeId.value = prefs.getString(KEY_SELECTED_NODE_ID, null)
    }

    private fun observeSettings() {
        viewModelScope.launch {
            val persistedSettings: Array<Flow<Any?>> = arrayOf(
                remoteDomain.map { it },
                hookPort.map { it },
                connectPort.map { it },
                accessKey.map { it },
                localPort.map { it },
                tcpEnabled.map { it },
                udpEnabled.map { it },
                ppv2Enabled.map { it },
                autoReconnect.map { it },
                heartbeatDelay.map { it },
                nodeListUrl.map { it },
                selectedNodeId.map { it }
            )

            combine(*persistedSettings) { values -> values }.collect { values ->
                prefs.edit()
                    .putString(KEY_REMOTE_DOMAIN, values[0] as String)
                    .putString(KEY_HOOK_PORT, values[1] as String)
                    .putString(KEY_CONNECT_PORT, values[2] as String)
                    .putString(KEY_ACCESS_KEY, values[3] as String)
                    .putString(KEY_LOCAL_PORT, values[4] as String)
                    .putBoolean(KEY_TCP_ENABLED, values[5] as Boolean)
                    .putBoolean(KEY_UDP_ENABLED, values[6] as Boolean)
                    .putBoolean(KEY_PPV2_ENABLED, values[7] as Boolean)
                    .putBoolean(KEY_AUTO_RECONNECT, values[8] as Boolean)
                    .putString(KEY_HEARTBEAT_DELAY, values[9] as String)
                    .putString(KEY_NODE_LIST_URL, values[10] as String)
                    .putString(KEY_SELECTED_NODE_ID, values[11] as String?)
                    .apply()
            }
        }
    }

    private fun bindService() {
        val context = getApplication<Application>()
        val intent = Intent(context, TunnelService::class.java)
        // BIND_AUTO_CREATE：如果 Service 未运行则自动创建（但不 start）
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun collectServiceState() {
        viewModelScope.launch {
            service?.state?.collect { _tunnelState.value = it }
        }
        viewModelScope.launch {
            service?.tunnelAddress?.collect { _tunnelAddress.value = it }
        }
        viewModelScope.launch {
            service?.logs?.collect { _logs.value = it }
        }
        viewModelScope.launch {
            service?.lastError?.collect { _lastError.value = it }
        }
    }

    /**
     * 启动隧道。验证表单后构建配置并启动前台 Service。
     * @return 错误消息；null 表示启动成功
     */
    fun startTunnel(): String? {
        val result = TunnelInputGuard.buildStartConfig(
            TunnelInputGuard.StartRequest(
                remoteDomain = remoteDomain.value,
                hookPort = hookPort.value,
                connectPort = connectPort.value,
                accessKey = accessKey.value,
                localPort = localPort.value,
                tcpEnabled = tcpEnabled.value,
                udpEnabled = udpEnabled.value,
                ppv2Enabled = ppv2Enabled.value,
                autoReconnect = autoReconnect.value,
                heartbeatDelay = heartbeatDelay.value,
                selectedNodeId = selectedNodeId.value,
                nodes = nodes.value
            )
        )
        val cfg = result.config ?: return result.error

        val context = getApplication<Application>()
        val intent = TunnelService.createStartIntent(context, cfg)
        return try {
            context.startForegroundService(intent)
            null
        } catch (e: Exception) {
            reportUiError("启动隧道服务失败: ${displayMessage(e)}")
        }
    }

    fun fetchNodes(): String? {
        val url = nodeListUrl.value.trim()
        if (url.isBlank()) {
            _nodeFetchMessage.value = "请输入节点列表 URL"
            return _nodeFetchMessage.value
        }
        if (_nodeFetchInProgress.value) return null

        _nodeFetchInProgress.value = true
        _nodeFetchMessage.value = "正在拉取节点..."
        viewModelScope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) {
                    TunnelInputGuard.sanitizeFetchedNodes(NodeFetcher.getFromNKM(url, 5000).values.toList())
                }
                if (fetched.isEmpty()) {
                    throw IllegalStateException("NKM 当前未返回可用节点")
                }
                withContext(Dispatchers.IO) {
                    NodeConfig.saveAll(nodeFile, fetched)
                }
                applyNodeList(fetched)
                _nodeFetchMessage.value = "已拉取 ${fetched.size} 个节点"
            } catch (e: Exception) {
                val remoteError = e.message ?: e.javaClass.simpleName
                val fallback = loadLocalNodesWithBundledFallback()
                if (fallback.isEmpty()) {
                    _nodeFetchMessage.value = "节点拉取失败: $remoteError；本地 nodes.json 也不可用"
                    reportUiError(_nodeFetchMessage.value.orEmpty())
                } else {
                    applyNodeList(fallback)
                    _nodeFetchMessage.value = "节点拉取失败: $remoteError；已回退到本地 nodes.json"
                    reportUiError(_nodeFetchMessage.value.orEmpty())
                }
            } finally {
                _nodeFetchInProgress.value = false
            }
        }
        return null
    }

    /** 停止隧道 */
    fun stopTunnel(): String? {
        val context = getApplication<Application>()
        val intent = TunnelService.createStopIntent(context)
        return try {
            context.startService(intent)
            null
        } catch (e: Exception) {
            reportUiError("停止隧道服务失败: ${displayMessage(e)}")
        }
    }

    /** 运行时切换协议，无需重连 */
    fun updateProtocol(tcp: Boolean, udp: Boolean): String? {
        val previousTcp = tcpEnabled.value
        val previousUdp = udpEnabled.value
        tcpEnabled.value = tcp
        udpEnabled.value = udp
        viewModelScope.launch(Dispatchers.IO) {
            val error = service?.updateProtocolFlags(tcp, udp)
            if (error != null) {
                withContext(Dispatchers.Main) {
                    tcpEnabled.value = previousTcp
                    udpEnabled.value = previousUdp
                    reportUiError(error)
                }
            }
        }
        return null
    }

    fun updatePpv2(enabled: Boolean): String? {
        val previous = ppv2Enabled.value
        ppv2Enabled.value = enabled
        val shouldSyncRuntime = _tunnelState.value == NeoLinkState.STARTING || _tunnelState.value == NeoLinkState.RUNNING
        if (!shouldSyncRuntime) {
            return null
        }
        viewModelScope.launch(Dispatchers.IO) {
            val activeService = service
            val error = if (activeService == null) {
                "运行时 PPv2 更新失败: 隧道服务未绑定"
            } else {
                activeService.updatePpv2(enabled)
            }
            if (error != null) {
                withContext(Dispatchers.Main) {
                    ppv2Enabled.value = previous
                    reportUiError(error)
                }
            }
        }
        return null
    }

    fun selectNode(nodeId: String?) {
        _selectedNodeId.value = nodeId
        val node = nodes.value.firstOrNull { it.realId == nodeId }
        if (node != null) {
            val result = TunnelInputGuard.validateNodeSelection(node)
            val selection = result.selection ?: run {
                reportUiError(result.error ?: "节点数据无效")
                return
            }
            remoteDomain.value = selection.address
            hookPort.value = selection.hookPort.toString()
            connectPort.value = selection.connectPort.toString()
        }
    }

    private fun loadBundledDefaults() {
        if (nodeListUrl.value.isBlank()) {
            nodeListUrl.value = readBundledNodeListUrl()
        }
        val localNodes = loadLocalNodesWithBundledFallback()
        if (localNodes.isNotEmpty()) {
            applyNodeList(localNodes)
        }
    }

    private fun readBundledNodeListUrl(): String {
        return try {
            val props = Properties()
            getApplication<Application>().assets.open(ASSET_APP_PROPERTIES).use { props.load(it) }
            props.getProperty(PROP_NODE_LIST_URL).orEmpty()
        } catch (e: Exception) {
            reportUiError("读取内置节点配置失败: ${displayMessage(e)}")
            ""
        }
    }

    private fun loadLocalNodesWithBundledFallback(): List<NeoNode> {
        return try {
            loadNodesFromFile()
        } catch (localError: Exception) {
            try {
                copyBundledNodesToLocal(overwrite = true)
                loadNodesFromFile()
            } catch (bundledError: Exception) {
                reportUiError("加载本地节点失败: ${displayMessage(localError)}；内置节点失败: ${displayMessage(bundledError)}")
                emptyList()
            }
        }
    }

    private fun loadNodesFromFile(): List<NeoNode> {
        if (!nodeFile.exists()) {
            copyBundledNodesToLocal(overwrite = false)
        }
        val loaded = NodeConfig.loadAll(nodeFile).map { it.toNeoNode() }
        return TunnelInputGuard.sanitizeFetchedNodes(loaded)
    }

    private fun copyBundledNodesToLocal(overwrite: Boolean) {
        if (!overwrite && nodeFile.exists()) {
            return
        }
        nodeFile.parentFile?.mkdirs()
        getApplication<Application>().assets.open(ASSET_NODE_LIST).use { input ->
            nodeFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun applyNodeList(loadedNodes: List<NeoNode>) {
        _nodes.value = loadedNodes
        val selectedId = _selectedNodeId.value
        val selectedNode = loadedNodes.firstOrNull { it.realId == selectedId }
            ?: loadedNodes.firstOrNull()
        if (selectedNode == null) {
            _selectedNodeId.value = null
            return
        }
        selectNode(selectedNode.realId)
    }

    private fun reportUiError(message: String): String {
        _lastError.value = message
        _logs.value = (_logs.value + "[ERROR] $message").takeLast(500)
        return message
    }

    private fun displayMessage(e: Exception): String {
        return e.message ?: e.javaClass.simpleName
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    /** 派生状态：是否正在运行 */
    val isRunning: StateFlow<Boolean> = tunnelState.map { it == NeoLinkState.RUNNING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 派生状态：是否正在连接中 */
    val isConnecting: StateFlow<Boolean> = tunnelState.map { it == NeoLinkState.STARTING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    override fun onCleared() {
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
        super.onCleared()
    }
}
