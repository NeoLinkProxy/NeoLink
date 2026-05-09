package neoproxy.neolink.gui.state
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neoproxy.neolink.NeoLink
import neoproxy.neolink.cli.ClientConsole
import neoproxy.neolink.cli.CommandLineProcessor
import neoproxy.neolink.config.ConfigOperator
import neoproxy.neolink.config.LanguageData
import neoproxy.neolink.core.VersionInfo
import neoproxy.neolink.gui.config.DEFAULT_NAS_URL
import neoproxy.neolink.gui.data.NasClient
import neoproxy.neolink.gui.data.NeoLinkLocalStore
import neoproxy.neolink.gui.data.NkmNodeClient
import neoproxy.neolink.gui.data.NkmNodeSource
import neoproxy.neolink.gui.model.AuthMode
import neoproxy.neolink.gui.model.AuthUiState
import neoproxy.neolink.gui.model.CreateTunnelDraft
import neoproxy.neolink.gui.model.MainPage
import neoproxy.neolink.gui.model.NasKey
import neoproxy.neolink.gui.model.NasNode
import neoproxy.neolink.gui.model.NkmNode
import neoproxy.neolink.gui.model.RuntimeUiState
import neoproxy.neolink.gui.model.SessionStoreDocument
import neoproxy.neolink.gui.model.TrafficPoint
import neoproxy.neolink.gui.model.TunnelCardState
import neoproxy.neolink.gui.model.TunnelRuntimeUiState
import neoproxy.neolink.gui.model.UiState
import neoproxy.neolink.state.ConnectionState
import neoproxy.neolink.state.FeatureState
import neoproxy.neolink.state.RuntimeState
import neoproxy.neolink.util.Debugger.debugOperation
import neoproxy.neolink.util.LogSink
import top.ceroxe.api.neolink.NeoLinkAPI
import top.ceroxe.api.neolink.NeoLinkCfg
import top.ceroxe.api.neolink.NeoLinkState
import top.ceroxe.api.print.log.Loggist
import top.ceroxe.api.print.log.LogType
import top.ceroxe.api.print.log.State
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.LongAdder

/**
 * 新版桌面 UI 的状态与运行时边界。
 *
 * 旧 CLI 仍然通过全局 ConnectionState/FeatureState/NeoLinkCoreRunner 运行；GUI 则在这里为每个
 * 隧道卡片创建独立 NeoLinkAPI 实例。这样配置粒度与“单密钥单隧道”模型一致，也避免一个全局
 * 开关意外影响其它卡片。
 */
class NeoLinkViewModel {
    private companion object {
        const val GUI_SYSTEM_PREFIX = "[System]"
        const val MAX_LOG_LINES = 1000
        const val TRAFFIC_WINDOW_SECONDS = 10
        const val BYTES_PER_MIB = 1024.0 * 1024.0
        val KeyBalanceMessagePattern: Regex = Regex("这个密钥有\\s+(\\d+(?:\\.\\d+)?)\\s+M(?:i)?B\\s+流量可以消耗")
    }

    var authState by mutableStateOf(AuthUiState())
        private set
    var uiState by mutableStateOf(UiState())
        private set
    var runtimeState by mutableStateOf(RuntimeUiState())
        private set

    val keys = mutableStateListOf<NasKey>()
    val tunnels = mutableStateListOf<TunnelCardState>()
    val tunnelRuntime = mutableStateMapOf<String, TunnelRuntimeUiState>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeApis = ConcurrentHashMap<String, NeoLinkAPI>()
    private val runRequested = ConcurrentHashMap<String, AtomicBoolean>()
    private val pendingTrafficBytes = ConcurrentHashMap<String, LongAdder>()
    private val trafficFlusherStarted = AtomicBoolean(false)

    private var isInitialized = false
    private var isLogRedirected = false

    val appVersion: String
        get() = VersionInfo.VERSION

    val authenticatedEmail: String
        get() = authState.email

    val totalKeyCount: Int
        get() = keys.size

    val creatableTunnelCount: Int
        get() = availableKeysForCreate.size

    val activeTunnelCount: Int
        get() = tunnels.count { tunnelRuntime[it.id]?.running == true }

    val totalExternalConnections: Int
        get() = tunnels.sumOf { tunnelRuntime[it.id]?.activeConnections ?: 0 }

    val totalTrafficPoints: List<TrafficPoint>
        get() = mergeTrafficPoints(
            tunnels.map { tunnel ->
                val runtime = tunnelRuntime[tunnel.id]
                if (runtime?.running == true) runtime.trafficPoints else emptyList()
            }
        )

    val totalTrafficBytes: Long
        get() = tunnels.sumOf { tunnelRuntime[it.id]?.totalTrafficBytes ?: 0L }

    val availableKeysForCreate: List<NasKey>
        get() {
            val used = tunnels.mapNotNull { it.keyAlias.takeIf(String::isNotBlank) }.toSet()
            return keys.filter { it.alias.isNotBlank() && it.alias !in used }
        }

    fun initialize(args: Array<String>) {
        if (isInitialized) return
        isInitialized = true

        ConfigOperator.initEnvironment()
        File(ConfigOperator.WORKING_DIR, "logs").mkdirs()

        val originalConnectionState = ConnectionState.snapshot()
        val originalFeatureState = FeatureState.snapshot()
        var initializationError: String? = null

        try {
            ConfigOperator.readAndSetValue()
            CommandLineProcessor.applyCommandLineArgs(args)
        } catch (e: IllegalArgumentException) {
            ConnectionState.apply(originalConnectionState)
            FeatureState.apply(originalFeatureState)
            initializationError = e.message ?: "未知错误"
        }

        RuntimeState.setLanguageData(LanguageData.getChineseLanguage())
        ClientConsole.initializeLogger(false)
        setupLogRedirector()
        startTrafficFlusher()
        NeoLinkLocalStore.ensureDesktopConfigDefaults()

        val session = NeoLinkLocalStore.loadSession()
        val configNasUrl = NeoLinkLocalStore.loadNasUrlFromConfig()
        val resolvedNasUrl = session.nasUrl.ifBlank { configNasUrl }.ifBlank { DEFAULT_NAS_URL }
        authState = authState.copy(
            nasUrl = resolvedNasUrl,
            email = session.email,
            sessionToken = session.sessionToken,
            isAuthenticated = session.sessionToken.isNotBlank()
        )

        tunnels.clear()
        tunnels.addAll(NeoLinkLocalStore.loadTunnels().mapIndexed { index, tunnel ->
            if (tunnel.name.isBlank()) {
                tunnel.name = "隧道${index + 1}"
            }
            tunnel.expanded = false
            tunnel
        })
        tunnels.forEach {
            tunnelRuntime[it.id] = TunnelRuntimeUiState()
        }

        if (initializationError != null) {
            appendSystemLog("配置或参数无效，已回退到安全默认值：$initializationError", surroundWithBlankLines = true)
        }

        ClientConsole.printLogo()
        ClientConsole.printBasicInfo()

        if (authState.isAuthenticated) {
            refreshSessionAndKeys()
        }

        if (NeoLink.shouldAutoStart()) {
            appendSystemLog("新版桌面 UI 已启用多隧道模型，--start 请在隧道卡片中逐项启动。")
        }
    }

    fun dispose() {
        tunnels.toList().forEach { stopTunnel(it.id) }
        scope.cancel()
    }

    fun setPage(page: MainPage) {
        uiState = uiState.copy(currentPage = page)
        if (page == MainPage.TUNNELS && authState.isAuthenticated) {
            refreshKeys()
        }
    }

    fun toggleSidebar() {
        uiState = uiState.copy(sidebarExpanded = !uiState.sidebarExpanded)
    }

    fun toggleTotalTraffic() {
        uiState = uiState.copy(totalTrafficExpanded = !uiState.totalTrafficExpanded)
    }

    fun showCreateTunnelDialog() {
        uiState = uiState.copy(createDialogVisible = true, createDraft = CreateTunnelDraft())
    }

    fun hideCreateTunnelDialog() {
        uiState = uiState.copy(createDialogVisible = false, createDraft = CreateTunnelDraft())
    }

    fun updateCreateDraft(
        keyAlias: String = uiState.createDraft.selectedKeyAlias,
        nodeId: String = uiState.createDraft.selectedNodeId,
        localPort: String = uiState.createDraft.localPort
    ) {
        val selectedKey = keys.firstOrNull { it.alias == keyAlias }
        val normalizedNodeId = if (selectedKey?.onlineNodes?.any { it.nodeId == nodeId } == true) {
            nodeId
        } else {
            selectedKey?.onlineNodes?.firstOrNull()?.nodeId.orEmpty()
        }
        uiState = uiState.copy(createDraft = CreateTunnelDraft(keyAlias, normalizedNodeId, localPort.filter { it.isDigit() }))
    }

    fun createTunnelFromDraft(): String? {
        val key = keys.firstOrNull { it.alias == uiState.createDraft.selectedKeyAlias }
            ?: return "请选择密钥。"
        val port = uiState.createDraft.localPort.toIntOrNull()
        if (port == null || port !in 1..65535) {
            return "本地端口必须在 1~65535 之间。"
        }
        val node = key.onlineNodes.firstOrNull { it.nodeId == uiState.createDraft.selectedNodeId }
            ?: key.onlineNodes.firstOrNull()
            ?: return "该密钥当前没有可用节点。"

        val index = tunnels.size
        val tunnel = TunnelCardState(
            name = "隧道${index + 1}",
            keyAlias = key.alias,
            keyType = key.type,
            keyBalanceMiB = key.balanceMiB,
            keyInitialBalanceMiB = key.balanceMiB,
            localPort = port.toString(),
            selectedNodeId = node.nodeId,
            selectedNodeName = node.displayName,
            remoteDomain = node.address,
            hookPort = node.hookPort.toString(),
            connectPort = node.connectPort.toString()
        )
        tunnels.add(tunnel)
        tunnelRuntime[tunnel.id] = TunnelRuntimeUiState()
        persistTunnels()
        hideCreateTunnelDialog()
        appendSystemLog("已创建 ${tunnel.name}，密钥 ${key.alias}。")
        return null
    }

    fun updateTunnelName(id: String, name: String) = updateTunnel(id) { it.name = name }
    fun updateTunnelLocalPort(id: String, value: String) = updateTunnel(id) { it.localPort = value.filter(Char::isDigit) }
    fun updateTunnelLocalDomain(id: String, value: String) = updateTunnel(id) { it.localDomain = value }
    fun updateTunnelHookPort(id: String, value: String) = updateTunnel(id) { it.hookPort = value.filter(Char::isDigit) }
    fun updateTunnelConnectPort(id: String, value: String) = updateTunnel(id) { it.connectPort = value.filter(Char::isDigit) }
    fun updateTunnelTcp(id: String, enabled: Boolean) {
        val previous = tunnels.firstOrNull { it.id == id }?.tcpEnabled
        updateTunnel(id) { it.tcpEnabled = enabled }
        if (previous != null && previous != enabled) {
            syncRuntimeProtocolFlags(id)
        }
    }

    fun updateTunnelUdp(id: String, enabled: Boolean) {
        val previous = tunnels.firstOrNull { it.id == id }?.udpEnabled
        updateTunnel(id) { it.udpEnabled = enabled }
        if (previous != null && previous != enabled) {
            syncRuntimeProtocolFlags(id)
        }
    }

    fun updateTunnelPpv2(id: String, enabled: Boolean) {
        val previous = tunnels.firstOrNull { it.id == id }?.ppv2Enabled
        updateTunnel(id) { it.ppv2Enabled = enabled }
        if (previous != null && previous != enabled) {
            syncRuntimePpv2Flag(id, enabled)
        }
    }

    fun updateTunnelAutoReconnect(id: String, enabled: Boolean) {
        val previous = tunnels.firstOrNull { it.id == id }?.autoReconnect
        updateTunnel(id) { it.autoReconnect = enabled }
        if (previous != null && previous != enabled) {
            appendTunnelSystemLog(id, "自动重连已${if (enabled) "开启" else "关闭"}。")
        }
    }

    fun updateTunnelDebug(id: String, enabled: Boolean) {
        val previous = tunnels.firstOrNull { it.id == id }?.debugMode
        updateTunnel(id) { it.debugMode = enabled }
        if (previous != null && previous != enabled) {
            appendTunnelSystemLog(id, "调试模式已${if (enabled) "开启" else "关闭"}。")
        }
    }

    fun updateTunnelShowConnection(id: String, enabled: Boolean) {
        val previous = tunnels.firstOrNull { it.id == id }?.showConnection
        updateTunnel(id) { it.showConnection = enabled }
        if (previous != null && previous != enabled) {
            appendTunnelSystemLog(id, "详细连接日志已${if (enabled) "开启" else "关闭"}。")
        }
    }

    fun toggleTunnelExpanded(id: String) {
        val index = tunnels.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = tunnels[index]
        tunnels[index] = current.copy(expanded = !current.expanded)
        persistTunnels()
    }

    fun selectTunnelNode(id: String, node: NasNode) = updateTunnel(id) {
        it.selectedNodeId = node.nodeId
        it.selectedNodeName = node.displayName
        it.remoteDomain = node.address
        it.hookPort = node.hookPort.toString()
        it.connectPort = node.connectPort.toString()
    }

    fun deleteTunnel(id: String) {
        stopTunnel(id)
        val removed = tunnels.removeIf { it.id == id }
        tunnelRuntime.remove(id)
        if (removed) {
            persistTunnels()
            appendSystemLog("隧道已删除。")
        }
    }

    fun toggleTunnelRunning(id: String): String? {
        return if (tunnelRuntime[id]?.running == true) {
            stopTunnel(id)
            null
        } else {
            startTunnel(id)
        }
    }

    fun startTunnel(id: String): String? {
        val tunnel = tunnels.firstOrNull { it.id == id } ?: return "隧道不存在。"
        validateTunnel(tunnel)?.let {
            appendTunnelSystemLog(id, it, surroundWithBlankLines = true)
            return it
        }
        if (tunnelRuntime[id]?.running == true) {
            return null
        }

        pendingTrafficBytes.remove(id)
        runRequested[id] = AtomicBoolean(true)
        setTunnelRuntime(id) { it.copy(running = true, stopping = false, activeConnections = 0, trafficPoints = emptyList()) }
        appendTunnelLog(id, "正在连接 ${tunnel.remoteDomain}:${tunnel.hookPort} ...")

        scope.launch(Dispatchers.IO) {
            do {
                val keepRunning = runRequested[id]?.get() == true
                if (!keepRunning) break
                val api = buildTunnelApi(tunnel, id)
                activeApis[id] = api
                try {
                    api.start()
                } catch (e: Exception) {
                    appendTunnelLog(id, "隧道异常：${e.message ?: e.javaClass.simpleName}", LogType.ERROR)
                } finally {
                    activeApis.remove(id, api)
                    api.close()
                    setTunnelRuntimeOnMain(id) { it.copy(activeConnections = 0) }
                }

                if (runRequested[id]?.get() == true && isTunnelAutoReconnectEnabled(id)) {
                    appendTunnelLog(id, "自动重连将在 30 秒后执行。")
                    delay(30_000)
                }
            } while (runRequested[id]?.get() == true && isTunnelAutoReconnectEnabled(id))

            withContext(Dispatchers.Main) {
                setTunnelRuntime(id) { it.copy(running = false, stopping = false, activeConnections = 0) }
                appendTunnelSystemLog(id, "服务已停止。", surroundWithBlankLines = true)
            }
        }
        persistTunnels()
        return null
    }

    fun stopTunnel(id: String) {
        val runtime = tunnelRuntime[id]
        if (runtime?.running == true && !runtime.stopping) {
            appendTunnelLog(id, "正在停止 NeoLink 服务...")
        }
        runRequested[id]?.set(false)
        pendingTrafficBytes.remove(id)
        setTunnelRuntime(id) { it.copy(stopping = it.running) }
        activeApis.remove(id)?.close()
    }

    fun updateNasUrl(value: String) {
        authState = authState.copy(nasUrl = value)
    }

    fun switchAuthMode(mode: AuthMode) {
        authState = authState.copy(mode = mode, message = "")
    }

    fun updateEmail(value: String) {
        authState = authState.copy(email = value.trim())
    }

    fun updatePassword(value: String) {
        authState = authState.copy(password = value)
    }

    fun updateConfirmPassword(value: String) {
        authState = authState.copy(confirmPassword = value)
    }

    fun updateCode(value: String) {
        authState = authState.copy(code = value.filter(Char::isDigit).take(6))
    }

    fun updateRealName(value: String) {
        authState = authState.copy(realName = value)
    }

    fun updateIdCard(value: String) {
        authState = authState.copy(idCard = value.trim())
    }

    fun sendCode() {
        val mode = if (authState.mode == AuthMode.REGISTER) "reg" else "login"
        val error = validateNasAndEmail()
        if (error != null) {
            authState = authState.copy(message = error)
            return
        }
        runAuth("正在发送验证码...") { state ->
            val response = nasClient(state).sendCode(state.email, mode)
            updateAuthStateOnMain { it.copy(message = response.message) }
        }
    }

    fun login() {
        val error = validateNasAndEmail() ?: if (authState.password.isBlank()) "密码不能为空。" else null
        if (error != null) {
            authState = authState.copy(message = error)
            return
        }
        runAuth("正在登录...") { state ->
            val response = nasClient(state).login(state.email, state.password)
            if (!response.success) {
                updateAuthStateOnMain { it.copy(message = response.message) }
                return@runAuth
            }
            persistSession(state.nasUrl, state.email, response.sessionToken)
            checkIdentityAndRefreshKeys(state.nasUrl, response.sessionToken)
        }
    }

    fun register() {
        val error = validateNasAndEmail()
            ?: if (authState.password.length < 8) "密码至少 8 位。" else null
            ?: if (authState.password != authState.confirmPassword) "两次输入的密码不一致。" else null
            ?: if (authState.code.length != 6) "请输入 6 位验证码。" else null
        if (error != null) {
            authState = authState.copy(message = error)
            return
        }
        runAuth("正在注册...") { state ->
            val response = nasClient(state).register(state.email, state.password, state.code)
            if (!response.success) {
                updateAuthStateOnMain { it.copy(message = response.message) }
                return@runAuth
            }
            persistSession(state.nasUrl, state.email, response.sessionToken)
            updateAuthStateOnMain {
                it.copy(mode = AuthMode.VERIFY_IDENTITY, isAuthenticated = true, message = "注册成功，请完成实名认证。")
            }
        }
    }

    fun verifyIdentity() {
        val error = if (authState.realName.isBlank()) "姓名不能为空。"
        else if (authState.idCard.isBlank()) "身份证号不能为空。"
        else null
        if (error != null) {
            authState = authState.copy(message = error)
            return
        }
        runAuth("正在实名认证...") { state ->
            val response = nasClient(state).verifyIdentity(state.realName, state.idCard)
            if (!response.success) {
                updateAuthStateOnMain { it.copy(message = response.message) }
                return@runAuth
            }
            updateAuthStateOnMain { it.copy(isVerified = true, mode = AuthMode.LOGIN, message = "认证通过。") }
            checkIdentityAndRefreshKeys(state.nasUrl, state.sessionToken)
        }
    }

    fun logout() {
        tunnels.toList().forEach { stopTunnel(it.id) }
        NeoLinkLocalStore.clearSession()
        keys.clear()
        authState = AuthUiState(nasUrl = authState.nasUrl.ifBlank { DEFAULT_NAS_URL })
        appendSystemLog("已退出登录。")
    }

    fun refreshSessionAndKeys() {
        val requestState = authState
        authState = authState.copy(isRestoringSession = true, isLoading = false, message = "")
        scope.launch(Dispatchers.IO) {
            try {
                nasClient(requestState).heartbeat()
                checkIdentityAndRefreshKeys(requestState.nasUrl, requestState.sessionToken)
            } catch (e: Exception) {
                NeoLinkLocalStore.clearSession()
                updateAuthStateOnMain {
                    it.copy(isAuthenticated = false, sessionToken = "", message = "会话已失效，请重新登录。")
                }
            } finally {
                updateAuthStateOnMain { it.copy(isRestoringSession = false) }
            }
        }
    }

    fun refreshKeys() {
        if (!authState.isAuthenticated) return
        val requestState = authState
        scope.launch(Dispatchers.IO) {
            try {
                val loaded = loadKeysWithAvailableNkmNodes(nasClient(requestState))
                withContext(Dispatchers.Main) {
                    keys.clear()
                    keys.addAll(loaded)
                    reconcileTunnelsWithKeys()
                    appendSystemLog("密钥列表已刷新，共 ${loaded.size} 个。")
                }
            } catch (e: Exception) {
                appendSystemLog("密钥列表刷新失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun saveSettings() {
        val normalized = authState.nasUrl.trim().ifBlank { DEFAULT_NAS_URL }
        NeoLinkLocalStore.saveNasUrlToConfig(normalized)
        if (authState.sessionToken.isNotBlank()) {
            NeoLinkLocalStore.saveSession(SessionStoreDocument(normalized, authState.email, authState.sessionToken))
        }
        authState = authState.copy(nasUrl = normalized)
        appendSystemLog("设置已保存。")
    }

    var logFontSize: androidx.compose.ui.unit.TextUnit
        get() = uiState.logFontSize
        set(value) {
            uiState = uiState.copy(logFontSize = value)
        }

    fun appendLog(ansiText: String) {
        addLogSafe(ansiText)
    }

    private fun buildTunnelApi(tunnel: TunnelCardState, tunnelId: String): NeoLinkAPI {
        val cfg = NeoLinkCfg(
            tunnel.remoteDomain.trim(),
            tunnel.hookPort.trim().toInt(),
            tunnel.connectPort.trim().toInt(),
            tunnel.keyAlias.trim(),
            tunnel.localPort.trim().toInt()
        )
            .setLocalDomainName(tunnel.localDomain.trim())
            .setTCPEnabled(tunnel.tcpEnabled)
            .setUDPEnabled(tunnel.udpEnabled)
            .setPPV2Enabled(tunnel.ppv2Enabled)
            .setDebugMsg(false)
            .setClientVersion(ClientConsole.getClientVersionToReport())
        cfg.setLanguage(LanguageData.getChineseLanguage().currentLanguage)

        return NeoLinkAPI(cfg)
            .setUnsupportedVersionDecision { true }
            .setOnStateChanged { state ->
                if (state == NeoLinkState.RUNNING) {
                    appendTunnelLog(tunnelId, "隧道已连接。")
                }
            }
            .setOnServerMessage { message -> appendTunnelLog(tunnelId, message) }
            .setOnError { message, cause ->
                val text = message ?: cause?.message ?: cause?.javaClass?.simpleName ?: "未知错误"
                appendTunnelLog(tunnelId, text, LogType.ERROR)
            }
            .setOnConnect { protocol, source, target ->
                setTunnelRuntimeOnMain(tunnelId) { it.copy(activeConnections = it.activeConnections + 1) }
                if (isTunnelShowConnectionEnabled(tunnelId)) {
                    appendTunnelLog(tunnelId, "$protocol ${source.hostString}:${source.port} -> ${target.hostString}:${target.port} 已建立")
                }
            }
            .setOnDisconnect { protocol, source, target ->
                setTunnelRuntimeOnMain(tunnelId) { it.copy(activeConnections = (it.activeConnections - 1).coerceAtLeast(0)) }
                if (isTunnelShowConnectionEnabled(tunnelId)) {
                    appendTunnelLog(tunnelId, "$protocol ${source.hostString}:${source.port} -> ${target.hostString}:${target.port} 已断开")
                }
            }
            .setOnTraffic { _, _, bytes -> recordTraffic(tunnelId, bytes) }
            .setOnConnectNeoFailure { appendTunnelLog(tunnelId, "连接 NeoProxyServer 传输端口失败。", LogType.ERROR) }
            .setOnConnectLocalFailure { appendTunnelLog(tunnelId, "连接本地下游服务失败：${tunnel.localPort}", LogType.ERROR) }
            .setDebugSink { message, cause ->
                if (isTunnelDebugEnabled(tunnelId) && message != null) appendTunnelLog(tunnelId, message)
                if (isTunnelDebugEnabled(tunnelId) && cause != null) appendTunnelLog(tunnelId, cause.message ?: cause.javaClass.simpleName, LogType.ERROR)
            }
    }

    private fun validateTunnel(tunnel: TunnelCardState): String? {
        if (tunnel.remoteDomain.trim().isBlank()) return "节点地址不能为空。"
        if (tunnel.localDomain.trim().isBlank()) return "本地域名不能为空。"
        if (tunnel.keyAlias.trim().isBlank()) return "密钥不能为空。"
        if (tunnel.localPort.trim().toIntOrNull()?.takeIf { it in 1..65535 } == null) return "本地端口必须在 1~65535 之间。"
        if (tunnel.hookPort.trim().toIntOrNull()?.takeIf { it in 1..65535 } == null) return "Hook 端口必须在 1~65535 之间。"
        if (tunnel.connectPort.trim().toIntOrNull()?.takeIf { it in 1..65535 } == null) return "连接端口必须在 1~65535 之间。"
        return null
    }

    private fun updateTunnel(id: String, block: (TunnelCardState) -> Unit) {
        val index = tunnels.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = tunnels[index]
        val updated = current.copy()
        block(updated)
        if (updated == current) return
        tunnels[index] = updated
        persistTunnelsAsync()
    }

    private fun syncRuntimeProtocolFlags(id: String) {
        val tunnel = tunnels.firstOrNull { it.id == id }?.copy() ?: return
        val api = activeApis[id]
        if (api == null) {
            appendTunnelSystemLog(id, buildProtocolUpdateMessage(tunnel.tcpEnabled, tunnel.udpEnabled))
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                api.updateRuntimeProtocolFlags(tunnel.tcpEnabled, tunnel.udpEnabled)
                appendTunnelSystemLog(id, buildProtocolUpdateMessage(tunnel.tcpEnabled, tunnel.udpEnabled))
            } catch (e: Exception) {
                appendTunnelSystemLog(id, "运行时协议更新失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun syncRuntimePpv2Flag(id: String, enabled: Boolean) {
        val api = activeApis[id]
        if (api == null) {
            appendTunnelSystemLog(id, buildPpv2UpdateMessage(enabled))
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                api.setPPV2Enabled(enabled)
                appendTunnelSystemLog(id, buildPpv2UpdateMessage(enabled))
            } catch (e: Exception) {
                appendTunnelSystemLog(id, "运行时 PPv2 更新失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun isTunnelAutoReconnectEnabled(id: String): Boolean =
        tunnels.firstOrNull { it.id == id }?.autoReconnect == true

    private fun isTunnelShowConnectionEnabled(id: String): Boolean =
        tunnels.firstOrNull { it.id == id }?.showConnection == true

    private fun isTunnelDebugEnabled(id: String): Boolean =
        tunnels.firstOrNull { it.id == id }?.debugMode == true

    private fun persistTunnels() {
        NeoLinkLocalStore.saveTunnels(tunnels)
    }

    private fun persistTunnelsAsync() {
        val snapshot = tunnels.map { it.copy() }
        scope.launch(Dispatchers.IO) {
            try {
                NeoLinkLocalStore.saveTunnels(snapshot)
            } catch (e: Exception) {
                debugOperation(e)
            }
        }
    }

    private fun reconcileTunnelsWithKeys() {
        val byAlias = keys.associateBy { it.alias }
        tunnels.forEachIndexed { index, tunnel ->
            val key = byAlias[tunnel.keyAlias] ?: return@forEachIndexed
            applyTunnelBalanceSnapshot(tunnel, key.balanceMiB)
            tunnel.keyType = key.type
            if (tunnel.name.isBlank()) tunnel.name = "隧道${index + 1}"
            if (tunnel.selectedNodeId.isBlank()) {
                key.onlineNodes.firstOrNull()?.let { node ->
                    tunnel.selectedNodeId = node.nodeId
                    tunnel.selectedNodeName = node.displayName
                    tunnel.remoteDomain = node.address
                    tunnel.hookPort = node.hookPort.toString()
                    tunnel.connectPort = node.connectPort.toString()
                }
            }
            tunnels[index] = tunnel.copy()
        }
        persistTunnels()
    }

    private fun recordTraffic(tunnelId: String, bytes: Long) {
        if (bytes <= 0) return
        pendingTrafficBytes.computeIfAbsent(tunnelId) { LongAdder() }.add(bytes)
    }

    private fun startTrafficFlusher() {
        if (!trafficFlusherStarted.compareAndSet(false, true)) return
        scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(1_000)
                val runningTunnelIds = tunnels
                    .asSequence()
                    .map { it.id }
                    .filter { tunnelRuntime[it]?.running == true }
                    .toList()
                runningTunnelIds.forEach { tunnelId ->
                    val bytes = pendingTrafficBytes[tunnelId]?.sumThenReset() ?: 0L
                    applyTrafficOnMain(tunnelId, bytes)
                }
            }
        }
    }

    private fun applyTrafficOnMain(tunnelId: String, bytes: Long) {
        val second = Instant.now().epochSecond
        setTunnelRuntime(tunnelId) { runtime ->
            val merged = runtime.trafficPoints.toMutableList()
            val last = merged.lastOrNull()
            if (last != null && last.second == second) {
                merged[merged.lastIndex] = TrafficPoint(second, last.bytes + bytes)
            } else {
                merged.add(TrafficPoint(second, bytes))
            }
            val oldestVisibleSecond = second - (TRAFFIC_WINDOW_SECONDS - 1)
            while (merged.isNotEmpty() && merged.first().second < oldestVisibleSecond) merged.removeAt(0)
            runtime.copy(
                totalTrafficBytes = runtime.totalTrafficBytes + bytes,
                trafficSinceBalanceSyncBytes = saturatingAdd(runtime.trafficSinceBalanceSyncBytes, bytes),
                trafficPoints = merged
            )
        }
    }

    private fun reconcileTunnelBalance(tunnelId: String, previousBalanceMiB: Double, latestBalanceMiB: Double) {
        val previousBalanceBytes = mibToBytes(previousBalanceMiB)
        val latestBalanceBytes = mibToBytes(latestBalanceMiB)
        val serverAccountedBytes = (previousBalanceBytes - latestBalanceBytes).coerceAtLeast(0L)
        if (serverAccountedBytes <= 0L) {
            return
        }
        setTunnelRuntime(tunnelId) { runtime ->
            runtime.copy(
                trafficSinceBalanceSyncBytes = (runtime.trafficSinceBalanceSyncBytes - serverAccountedBytes).coerceAtLeast(0L)
            )
        }
    }

    private fun applyTunnelBalanceSnapshot(tunnel: TunnelCardState, latestBalanceMiB: Double) {
        if (!latestBalanceMiB.isFinite() || latestBalanceMiB < 0.0) {
            return
        }
        val previousBalanceMiB = tunnel.keyBalanceMiB
        reconcileTunnelBalance(tunnel.id, previousBalanceMiB, latestBalanceMiB)
        tunnel.keyInitialBalanceMiB = resolveInitialKeyBalanceMiB(
            tunnel.keyInitialBalanceMiB,
            previousBalanceMiB,
            latestBalanceMiB
        )
        tunnel.keyBalanceMiB = latestBalanceMiB
    }

    private fun resolveInitialKeyBalanceMiB(currentInitialMiB: Double, previousBalanceMiB: Double, latestBalanceMiB: Double): Double {
        val observedInitialMiB = listOf(currentInitialMiB, previousBalanceMiB, latestBalanceMiB)
            .filter { it.isFinite() && it > 0.0 }
            .maxOrNull()
            ?: 0.0
        return observedInitialMiB.coerceAtLeast(latestBalanceMiB)
    }

    private fun mibToBytes(value: Double): Long {
        if (!value.isFinite() || value <= 0.0) {
            return 0L
        }
        val bytes = value * BYTES_PER_MIB
        return if (bytes >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else bytes.toLong()
    }

    private fun saturatingAdd(left: Long, right: Long): Long {
        if (right <= 0L) {
            return left
        }
        return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }

    private fun setTunnelRuntime(id: String, update: (TunnelRuntimeUiState) -> TunnelRuntimeUiState) {
        val current = tunnelRuntime[id] ?: TunnelRuntimeUiState()
        tunnelRuntime[id] = update(current)
    }

    private fun setTunnelRuntimeOnMain(id: String, update: (TunnelRuntimeUiState) -> TunnelRuntimeUiState) {
        scope.launch(Dispatchers.Main) {
            setTunnelRuntime(id, update)
        }
    }

    private fun appendTunnelLog(id: String, message: String, level: LogType = LogType.INFO) {
        val keyBalanceMatch = KeyBalanceMessagePattern.find(message)
        if (keyBalanceMatch != null) {
            keyBalanceMatch.groupValues.getOrNull(1)?.toDoubleOrNull()?.let { balanceMiB ->
                scope.launch(Dispatchers.Main) {
                    val index = tunnels.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        val tunnel = tunnels[index]
                        applyTunnelBalanceSnapshot(tunnel, balanceMiB)
                        tunnels[index] = tunnel.copy()
                        persistTunnelsAsync()
                    }
                }
            }
            refreshKeys()
        }
        val line = formatGuiLog(level, "HOST-CLIENT", message)
        scope.launch(Dispatchers.Main) {
            setTunnelRuntime(id) { runtime ->
                val updated = runtime.logs.toMutableList()
                updated.add(parseAnsi(line))
                if (updated.size > MAX_LOG_LINES) updated.removeAt(0)
                runtime.copy(logs = updated)
            }
        }
    }

    private fun appendTunnelSystemLog(id: String, message: String, surroundWithBlankLines: Boolean = false) {
        val normalizedMessage = buildString {
            if (surroundWithBlankLines && (tunnelRuntime[id]?.logs?.isNotEmpty() == true)) {
                append('\n')
            }
            append(GUI_SYSTEM_PREFIX)
            append(' ')
            append(message)
            if (surroundWithBlankLines) {
                append('\n')
            }
        }
        scope.launch(Dispatchers.Main) {
            setTunnelRuntime(id) { runtime ->
                val updated = runtime.logs.toMutableList()
                updated.add(parseAnsi(normalizedMessage))
                if (updated.size > MAX_LOG_LINES) updated.removeAt(0)
                runtime.copy(logs = updated)
            }
        }
    }

    private fun setupLogRedirector() {
        if (isLogRedirected) return
        isLogRedirected = true

        val originalLogSink = RuntimeState.logSink()
        RuntimeState.setLogSink { level, tag, message ->
            addLogSafe("[$tag] $message\n")
            originalLogSink?.log(level, tag, message)
        }
    }

    private fun addLogSafe(ansiMsg: String) {
        scope.launch(Dispatchers.Main) {
            val updatedMessages = runtimeState.logMessages.toMutableList()
            updatedMessages.add(parseAnsi(ansiMsg))
            if (updatedMessages.size > MAX_LOG_LINES) updatedMessages.removeAt(0)
            runtimeState = runtimeState.copy(logMessages = updatedMessages)
        }
    }

    private fun appendSystemLog(message: String, surroundWithBlankLines: Boolean = false) {
        val normalizedMessage = buildString {
            if (surroundWithBlankLines && runtimeState.logMessages.isNotEmpty()) {
                append('\n')
            }
            append(GUI_SYSTEM_PREFIX)
            append(' ')
            append(message)
            if (surroundWithBlankLines) append('\n')
        }
        RuntimeState.logSink()?.log(LogSink.Level.INFO, "GUI", normalizedMessage)
            ?: addLogSafe(normalizedMessage)
    }

    private fun buildProtocolUpdateMessage(tcpEnabled: Boolean, udpEnabled: Boolean): String {
        val tcpStatus = if (tcpEnabled) "已开启" else "已关闭"
        val udpStatus = if (udpEnabled) "已开启" else "已关闭"
        return "TCP $tcpStatus，UDP $udpStatus。"
    }

    private fun buildPpv2UpdateMessage(enabled: Boolean): String {
        return "真实 IP 透传已${if (enabled) "开启" else "关闭"}。"
    }

    private fun formatGuiLog(level: LogType, subject: String, message: String): String {
        return guiLoggist().getLogString(State(level, subject, message))
    }

    private fun guiLoggist(): Loggist {
        val logsDirectory = File(ConfigOperator.resolveWritableRuntimeDirectory(), "logs")
        if (!logsDirectory.exists() && !logsDirectory.mkdirs()) {
            debugOperation("Unable to create GUI log directory: ${logsDirectory.absolutePath}")
        }
        return Loggist(File(logsDirectory, "gui_internal.log"))
    }

    private fun parseAnsi(text: String): AnnotatedString {
        return buildAnnotatedString {
            val ansiRegex = Regex("\u001B\\[([0-9;]*)m")
            var lastIndex = 0
            var currentStyle = SpanStyle(color = Color(0xFFCCCCCC))

            ansiRegex.findAll(text).forEach { result ->
                val beforeText = text.substring(lastIndex, result.range.first)
                if (beforeText.isNotEmpty()) withStyle(currentStyle) { append(beforeText) }
                currentStyle = when (result.groupValues[1]) {
                    "31" -> SpanStyle(color = Color(0xFFFF5555))
                    "32" -> SpanStyle(color = Color(0xFF50FA7B))
                    "33" -> SpanStyle(color = Color(0xFFF1FA8C))
                    "34" -> SpanStyle(color = Color(0xFFBD93F9))
                    "36" -> SpanStyle(color = Color(0xFF8BE9FD))
                    else -> SpanStyle(color = Color(0xFFCCCCCC))
                }
                lastIndex = result.range.last + 1
            }
            if (lastIndex < text.length) withStyle(currentStyle) { append(text.substring(lastIndex)) }
        }
    }

    private fun runAuth(loadingMessage: String, action: suspend (AuthUiState) -> Unit) {
        authState = authState.copy(isLoading = true, isRestoringSession = false, message = loadingMessage)
        val requestState = authState
        scope.launch(Dispatchers.IO) {
            try {
                action(requestState)
            } catch (e: Exception) {
                updateAuthStateOnMain { it.copy(message = e.message ?: e.javaClass.simpleName) }
            } finally {
                updateAuthStateOnMain { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun checkIdentityAndRefreshKeys(nasUrl: String, sessionToken: String) {
        val client = NasClient(nasUrl, sessionToken)
        val status = client.identityStatus()
        if (status == "VERIFIED") {
            val loaded = loadKeysWithAvailableNkmNodes(client)
            withContext(Dispatchers.Main) {
                authState = authState.copy(
                    nasUrl = nasUrl,
                    sessionToken = sessionToken,
                    isAuthenticated = true,
                    isVerified = true,
                    mode = AuthMode.LOGIN,
                    message = "已登录。"
                )
                keys.clear()
                keys.addAll(loaded)
                reconcileTunnelsWithKeys()
            }
        } else {
            updateAuthStateOnMain {
                it.copy(
                    nasUrl = nasUrl,
                    sessionToken = sessionToken,
                    isAuthenticated = true,
                    isVerified = false,
                    mode = AuthMode.VERIFY_IDENTITY,
                    message = "请完成实名认证。"
                )
            }
        }
    }

    private suspend fun persistSession(nasUrl: String, email: String, sessionToken: String) {
        val normalizedNasUrl = nasUrl.trim().ifBlank { DEFAULT_NAS_URL }
        NeoLinkLocalStore.saveNasUrlToConfig(normalizedNasUrl)
        NeoLinkLocalStore.saveSession(SessionStoreDocument(normalizedNasUrl, email, sessionToken))
        updateAuthStateOnMain {
            it.copy(nasUrl = normalizedNasUrl, email = email, sessionToken = sessionToken, isAuthenticated = sessionToken.isNotBlank())
        }
    }

    private suspend fun updateAuthStateOnMain(update: (AuthUiState) -> AuthUiState) {
        withContext(Dispatchers.Main) {
            authState = update(authState)
        }
    }

    private fun nasClient(state: AuthUiState): NasClient = NasClient(state.nasUrl, state.sessionToken)

    private fun loadKeysWithAvailableNkmNodes(client: NasClient): List<NasKey> {
        val loadedKeys = client.myKeys()
        val nkmNodeLoadResult = NkmNodeClient.loadOnlineNodes(NeoLinkLocalStore.loadNkmNodeListUrlFromConfig())
        nkmNodeLoadResult.warning?.let { appendSystemLog(it) }
        if (nkmNodeLoadResult.source == NkmNodeSource.NETWORK) {
            appendSystemLog("NKM 可用节点列表已刷新并缓存，共 ${nkmNodeLoadResult.nodes.size} 个。")
        }
        return intersectNasAuthorizedNodesWithNkmAvailability(loadedKeys, nkmNodeLoadResult.nodes)
    }

    private fun intersectNasAuthorizedNodesWithNkmAvailability(keysFromNas: List<NasKey>, onlineNodesFromNkm: List<NkmNode>): List<NasKey> {
        if (onlineNodesFromNkm.isEmpty()) {
            return keysFromNas.map { key -> key.copy(availableNodes = emptyList()) }
        }

        val nkmById = onlineNodesFromNkm.associateBy { normalizeNodeIdentity(it.realId) }
        val nkmByEndpoint = onlineNodesFromNkm.associateBy { endpointKey(it.address, it.hookPort, it.connectPort) }
        val nkmByName = onlineNodesFromNkm.associateBy { normalizeNodeIdentity(it.name) }

        return keysFromNas.map { key ->
            val intersectedNodes = key.availableNodes.mapNotNull { nasNode ->
                val nkmNode = nkmById[normalizeNodeIdentity(nasNode.nodeId)]
                    ?: nkmByEndpoint[endpointKey(nasNode.address, nasNode.hookPort, nasNode.connectPort)]
                    ?: nkmByName[normalizeNodeIdentity(nasNode.displayName)]
                    ?: return@mapNotNull null

                nasNode.copy(
                    nodeId = nkmNode.realId,
                    displayName = nkmNode.name,
                    isOnline = true,
                    address = nkmNode.address,
                    hookPort = nkmNode.hookPort,
                    connectPort = nkmNode.connectPort,
                    icon = nkmNode.icon
                )
            }
            key.copy(availableNodes = intersectedNodes)
        }
    }

    private fun normalizeNodeIdentity(value: String): String {
        return value.trim().lowercase()
    }

    private fun endpointKey(address: String, hookPort: Int, connectPort: Int): String {
        return "${address.trim().lowercase()}:$hookPort:$connectPort"
    }

    private fun validateNasAndEmail(): String? {
        if (authState.nasUrl.trim().isBlank()) return "NAS_URL 不能为空。"
        if (!authState.nasUrl.startsWith("http://") && !authState.nasUrl.startsWith("https://")) {
            return "NAS_URL 必须以 http:// 或 https:// 开头。"
        }
        if (authState.email.isBlank() || !authState.email.contains("@")) return "邮箱格式无效。"
        return null
    }

    private fun mergeTrafficPoints(all: List<List<TrafficPoint>>): List<TrafficPoint> {
        val merged = linkedMapOf<Long, Long>()
        all.flatten().forEach { point ->
            merged[point.second] = (merged[point.second] ?: 0L) + point.bytes
        }
        return merged.entries.sortedBy { it.key }
            .takeLast(TRAFFIC_WINDOW_SECONDS)
            .map { TrafficPoint(it.key, it.value) }
    }
}
