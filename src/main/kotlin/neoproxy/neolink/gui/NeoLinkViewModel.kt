package neoproxy.neolink.gui

import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neoproxy.neolink.NeoLink
import neoproxy.neolink.cli.ClientConsole
import neoproxy.neolink.cli.CommandLineProcessor
import neoproxy.neolink.config.ConfigOperator
import neoproxy.neolink.config.LanguageData
import neoproxy.neolink.config.NodeConfig
import neoproxy.neolink.core.NeoLinkCoreRunner
import neoproxy.neolink.node.NodeWorkflow
import neoproxy.neolink.state.ConnectionSettings
import neoproxy.neolink.state.ConnectionState
import neoproxy.neolink.state.FeatureSettings
import neoproxy.neolink.state.FeatureState
import neoproxy.neolink.state.RuntimeState
import top.ceroxe.api.neolink.NeoLinkCfg
import top.ceroxe.api.neolink.NeoNode
import java.io.File
import java.io.IOException

/**
 * NeoLink 桌面 UI 的 View-model 边界。
 *
 * <p>设计原因：
 * GUI 会把可编辑表单状态保留在本地，只在用户明确启动或修改运行时行为时，才把已经
 * 校验过的值回写到共享核心状态。这样可以避免无效 GUI 输入、损坏的配置文件或 CLI 参数
 * 直接把窗口弄崩，或者污染全局运行时状态。</p>
 */
class NeoLinkViewModel {
    private companion object {
        const val GUI_SYSTEM_PREFIX = "[System]"
    }

    var connectionState by mutableStateOf(connectionUiStateFromCore())
        private set
    var featureState by mutableStateOf(featureUiStateFromCore())
        private set
    var uiState by mutableStateOf(UiState())
        private set
    var runtimeState by mutableStateOf(RuntimeUiState())
        private set

    var remoteDomain: String
        get() = connectionState.remoteDomain
        set(value) {
            connectionState = connectionState.copy(remoteDomain = value)
        }

    var localPort: String
        get() = connectionState.localPort
        set(value) {
            connectionState = connectionState.copy(localPort = value)
        }

    var accessKey: String
        get() = connectionState.accessKey
        set(value) {
            connectionState = connectionState.copy(accessKey = value)
        }

    val nodeList: List<NeoNode>
        get() = uiState.nodeList

    var selectedNode: NeoNode?
        get() = uiState.selectedNode
        private set(value) {
            uiState = uiState.copy(selectedNode = value)
        }

    var localDomain: String
        get() = connectionState.localDomain
        set(value) {
            connectionState = connectionState.copy(localDomain = value)
        }

    var hostHookPort: String
        get() = connectionState.hostHookPort
        set(value) {
            connectionState = connectionState.copy(hostHookPort = value)
        }

    var hostConnectPort: String
        get() = connectionState.hostConnectPort
        set(value) {
            connectionState = connectionState.copy(hostConnectPort = value)
        }

    var isTcpEnabled: Boolean
        get() = featureState.tcpEnabled
        set(value) {
            featureState = featureState.copy(tcpEnabled = value)
        }

    var isUdpEnabled: Boolean
        get() = featureState.udpEnabled
        set(value) {
            featureState = featureState.copy(udpEnabled = value)
        }

    var isPpv2Enabled: Boolean
        get() = featureState.ppv2Enabled
        set(value) {
            applyFeatureToggles(featureState.copy(ppv2Enabled = value))
        }

    var isAutoReconnect: Boolean
        get() = featureState.autoReconnect
        set(value) {
            applyFeatureToggles(featureState.copy(autoReconnect = value))
        }

    var isDebugMode: Boolean
        get() = featureState.debugMode
        set(value) {
            applyFeatureToggles(featureState.copy(debugMode = value))
        }

    var isShowConnection: Boolean
        get() = featureState.showConnection
        set(value) {
            applyFeatureToggles(featureState.copy(showConnection = value))
        }

    var isRunning: Boolean
        get() = runtimeState.isRunning
        set(value) {
            runtimeState = runtimeState.copy(isRunning = value)
        }

    var isStopping: Boolean
        get() = runtimeState.isStopping
        private set(value) {
            runtimeState = runtimeState.copy(isStopping = value)
        }

    val logMessages: List<AnnotatedString>
        get() = runtimeState.logMessages

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var logFontSize: androidx.compose.ui.unit.TextUnit
        get() = uiState.logFontSize
        set(value) {
            uiState = uiState.copy(logFontSize = value)
        }

    private var isInitialized = false
    private var isLogRedirected = false

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

        forceGuiChineseLanguage()
        syncStateFromCore()
        ClientConsole.initializeLogger(false)
        setupLogRedirector()

        if (initializationError != null) {
            appendSystemLog("配置或参数无效，已回退到安全默认值：$initializationError", surroundWithBlankLines = true)
        }

        ClientConsole.printLogo()
        ClientConsole.printBasicInfo()

        scope.launch(Dispatchers.IO) {
            NodeWorkflow.fetchAndSaveNodes()
            withContext(Dispatchers.Main) {
                loadNodes()
            }
        }

        if (NeoLink.shouldAutoStart()) {
            startService()
        }
    }

    private fun loadNodes() {
        val nodeFile = File(ConfigOperator.WORKING_DIR, NodeConfig.NODE_LIST_FILE_NAME)
        if (!nodeFile.exists()) {
            return
        }

        try {
            val loadedNodes = NodeConfig.loadAll(nodeFile).map { node ->
                NeoNode(
                    node.name,
                    node.realId,
                    node.address,
                    node.icon,
                    node.hostHookPort,
                    node.hostConnectPort
                )
            }

            uiState = uiState.copy(
                nodeList = loadedNodes,
                selectedNode = loadedNodes.firstOrNull()
            )
            loadedNodes.firstOrNull()?.let(::selectNode)
        } catch (e: Exception) {
            appendSystemLog("节点列表加载失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun forceGuiChineseLanguage() {
        RuntimeState.setLanguageData(LanguageData.getChineseLanguage())
    }

    private fun syncStateFromCore() {
        connectionState = connectionUiStateFromCore()
        featureState = featureUiStateFromCore()
    }

    fun selectNode(node: NeoNode) {
        selectedNode = node
        connectionState = connectionState.copy(
            remoteDomain = node.address,
            hostHookPort = node.hookPort.toString(),
            hostConnectPort = node.connectPort.toString()
        )
    }

    private fun connectionUiStateFromCore(): ConnectionUiState {
        val connection = ConnectionState.snapshot()
        return ConnectionUiState(
            remoteDomain = connection.remoteDomainName(),
            localPort = if (connection.localPort() == NeoLink.INVALID_LOCAL_PORT) "" else connection.localPort().toString(),
            accessKey = connection.key() ?: "",
            localDomain = connection.localDomainName(),
            hostHookPort = connection.hostHookPort().toString(),
            hostConnectPort = connection.hostConnectPort().toString()
        )
    }

    private fun featureUiStateFromCore(): FeatureToggleUiState {
        val features = FeatureState.snapshot()
        return FeatureToggleUiState(
            tcpEnabled = !features.disableTcp(),
            udpEnabled = !features.disableUdp(),
            ppv2Enabled = features.enableProxyProtocol(),
            autoReconnect = features.enableAutoReconnect(),
            debugMode = features.debugMode(),
            showConnection = features.showConnection()
        )
    }

    private fun applyFeatureToggles(updated: FeatureToggleUiState) {
        val previous = featureState
        featureState = updated
        val current = FeatureState.snapshot()
        FeatureState.apply(
            FeatureSettings(
                updated.debugMode,
                updated.showConnection,
                current.guiMode(),
                !updated.tcpEnabled,
                !updated.udpEnabled,
                updated.ppv2Enabled,
                updated.autoReconnect,
                current.enableAutoUpdate(),
                current.testUpdate(),
                current.noEffectMode(),
                current.heartbeatPacketDelay(),
                current.reconnectionIntervalSeconds(),
                current.proxyIPToLocalServer(),
                current.proxyIPToNeoServer(),
                current.outputFilePath(),
                current.nkmNodeListUrl()
            )
        )

        if (previous.autoReconnect != updated.autoReconnect) {
            appendSystemLog("自动重连已${if (updated.autoReconnect) "开启" else "关闭"}。")
        }
        if (previous.debugMode != updated.debugMode) {
            appendSystemLog("调试模式已${if (updated.debugMode) "开启" else "关闭"}。")
        }
        if (previous.ppv2Enabled != updated.ppv2Enabled) {
            appendSystemLog("真实 IP 透传已${if (updated.ppv2Enabled) "开启" else "关闭"}。")
        }
        if (previous.showConnection != updated.showConnection) {
            appendSystemLog("详细连接日志已${if (updated.showConnection) "开启" else "关闭"}。")
        }
    }

    fun updateTransportProtocols(tcpEnabled: Boolean, udpEnabled: Boolean) {
        val previousTcpEnabled = isTcpEnabled
        val previousUdpEnabled = isUdpEnabled

        if (!tcpEnabled && !udpEnabled) {
            featureState = featureState.copy(
                tcpEnabled = previousTcpEnabled,
                udpEnabled = previousUdpEnabled
            )
            appendSystemLog("GUI 至少需要保留一种传输协议，TCP 和 UDP 不能同时关闭。")
            return
        }

        featureState = featureState.copy(tcpEnabled = tcpEnabled, udpEnabled = udpEnabled)

        if (!isRunning) {
            FeatureState.applyRuntimeTransportSelection(tcpEnabled, udpEnabled)
            appendSystemLog(buildProtocolUpdateMessage(tcpEnabled, udpEnabled))
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                NeoLinkCoreRunner.updateRuntimeProtocolFlags(tcpEnabled, udpEnabled)
                appendSystemLog(buildProtocolUpdateMessage(tcpEnabled, udpEnabled))
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    featureState = featureState.copy(
                        tcpEnabled = previousTcpEnabled,
                        udpEnabled = previousUdpEnabled
                    )
                    FeatureState.applyRuntimeTransportSelection(previousTcpEnabled, previousUdpEnabled)
                    appendSystemLog("运行时协议更新失败：${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    fun startService() {
        if (isRunning || isStopping) {
            return
        }

        val validationError = validateStartRequest()
        if (validationError != null) {
            appendSystemLog(validationError, surroundWithBlankLines = true)
            return
        }

        val normalizedRemoteDomain = remoteDomain.trim()
        val normalizedLocalDomain = localDomain.trim()
        val normalizedAccessKey = accessKey.trim()
        val parsedLocalPort = localPort.trim().toInt()
        val parsedHookPort = hostHookPort.trim().toInt()
        val parsedConnectPort = hostConnectPort.trim().toInt()
        val tunnelConfig = buildTunnelConfig(parsedLocalPort, parsedHookPort, parsedConnectPort)
        val currentFeatures = FeatureState.snapshot()

        ConnectionState.apply(
            ConnectionSettings(
                normalizedRemoteDomain,
                normalizedLocalDomain,
                parsedHookPort,
                parsedConnectPort,
                normalizedAccessKey,
                parsedLocalPort,
                selectedNode?.realId
            )
        )
        FeatureState.apply(
            FeatureSettings(
                isDebugMode,
                isShowConnection,
                currentFeatures.guiMode(),
                !isTcpEnabled,
                !isUdpEnabled,
                isPpv2Enabled,
                isAutoReconnect,
                currentFeatures.enableAutoUpdate(),
                currentFeatures.testUpdate(),
                currentFeatures.noEffectMode(),
                currentFeatures.heartbeatPacketDelay(),
                currentFeatures.reconnectionIntervalSeconds(),
                currentFeatures.proxyIPToLocalServer(),
                currentFeatures.proxyIPToNeoServer(),
                currentFeatures.outputFilePath(),
                currentFeatures.nkmNodeListUrl()
            )
        )

        isRunning = true
        isStopping = false
        scope.launch(Dispatchers.IO) {
            try {
                NeoLinkCoreRunner.runCore(tunnelConfig)
            } finally {
                withContext(Dispatchers.Main) {
                    isRunning = false
                    isStopping = false
                    appendSystemLog("服务已停止。", surroundWithBlankLines = true)
                }
            }
        }
    }

    private fun validateStartRequest(): String? {
        if (remoteDomain.trim().isBlank()) {
            return "远程地址不能为空。"
        }
        if (localDomain.trim().isBlank()) {
            return "本地域名不能为空。"
        }
        if (accessKey.trim().isBlank()) {
            return "访问密钥不能为空。"
        }
        if (!isTcpEnabled && !isUdpEnabled) {
            return "TCP 和 UDP 不能同时关闭。"
        }
        if (localPort.trim().toIntOrNull()?.takeIf { it in 1..65535 } == null) {
            return "本地端口必须是 1-65535 的整数。"
        }
        if (hostHookPort.trim().toIntOrNull()?.takeIf { it in 1..65535 } == null) {
            return "Hook 端口必须是 1-65535 的整数。"
        }
        if (hostConnectPort.trim().toIntOrNull()?.takeIf { it in 1..65535 } == null) {
            return "Connect 端口必须是 1-65535 的整数。"
        }
        return null
    }

    private fun buildTunnelConfig(parsedLocalPort: Int, parsedHookPort: Int, parsedConnectPort: Int): NeoLinkCfg {
        return NeoLinkCfg(remoteDomain.trim(), parsedHookPort, parsedConnectPort, accessKey.trim(), parsedLocalPort)
    }

    fun dispose() {
        stopService()
        scope.cancel()
    }

    fun stopService() {
        if (!isRunning || isStopping) {
            return
        }
        isStopping = true
        ClientConsole.say("正在停止 NeoLink 服务...")
        scope.launch(Dispatchers.IO) {
            NeoLinkCoreRunner.requestStop()
        }
    }

    private fun setupLogRedirector() {
        if (isLogRedirected) {
            return
        }
        isLogRedirected = true

        val originalLoggist = RuntimeState.loggist()
        val internalLogFile = File(ConfigOperator.WORKING_DIR, "logs/gui_internal.log")

        RuntimeState.setLoggist(object : top.ceroxe.api.print.log.Loggist(internalLogFile) {
            override fun say(state: top.ceroxe.api.print.log.State) {
                addLogSafe(getLogString(state))
                originalLoggist?.say(state)
            }

            override fun sayNoNewLine(state: top.ceroxe.api.print.log.State) {
                addLogSafe(getLogString(state))
                originalLoggist?.sayNoNewLine(state)
            }

            override fun write(str: String?, isNewLine: Boolean) {
                if (str != null) {
                    addLogSafe(
                        buildString {
                            append(str)
                            if (isNewLine) {
                                appendLine()
                            }
                        }
                    )
                }
                originalLoggist?.write(str, isNewLine)
            }
        })
    }

    private fun addLogSafe(ansiMsg: String) {
        scope.launch(Dispatchers.Main) {
            val updatedMessages = runtimeState.logMessages.toMutableList()
            updatedMessages.add(parseAnsi(ansiMsg))
            if (updatedMessages.size > 1000) {
                updatedMessages.removeAt(0)
            }
            runtimeState = runtimeState.copy(logMessages = updatedMessages)
        }
    }

    private fun parseAnsi(text: String): AnnotatedString {
        return buildAnnotatedString {
            val ansiRegex = Regex("\u001B\\[([0-9;]*)m")
            var lastIndex = 0
            var currentStyle = SpanStyle(color = Color(0xFFCCCCCC))

            ansiRegex.findAll(text).forEach { result ->
                val beforeText = text.substring(lastIndex, result.range.first)
                if (beforeText.isNotEmpty()) {
                    withStyle(currentStyle) { append(beforeText) }
                }
                val code = result.groupValues[1]
                currentStyle = when (code) {
                    "31" -> SpanStyle(color = Color(0xFFFF5555))
                    "32" -> SpanStyle(color = Color(0xFF50FA7B))
                    "33" -> SpanStyle(color = Color(0xFFF1FA8C))
                    "34" -> SpanStyle(color = Color(0xFFBD93F9))
                    "36" -> SpanStyle(color = Color(0xFF8BE9FD))
                    else -> SpanStyle(color = Color(0xFFCCCCCC))
                }
                lastIndex = result.range.last + 1
            }

            if (lastIndex < text.length) {
                withStyle(currentStyle) { append(text.substring(lastIndex)) }
            }
        }
    }

    fun appendLog(ansiText: String) {
        addLogSafe(ansiText)
    }

    private fun appendSystemLog(message: String, surroundWithBlankLines: Boolean = false) {
        val normalizedMessage = buildString {
            if (surroundWithBlankLines && runtimeState.logMessages.isNotEmpty()) {
                append('\n')
            }
            append(GUI_SYSTEM_PREFIX)
            append(' ')
            append(message)
            if (surroundWithBlankLines) {
                append('\n')
            }
        }
        RuntimeState.loggist()?.write(normalizedMessage, false) ?: addLogSafe(normalizedMessage)
    }

    private fun buildProtocolUpdateMessage(tcpEnabled: Boolean, udpEnabled: Boolean): String {
        val tcpStatus = if (tcpEnabled) "已开启" else "已关闭"
        val udpStatus = if (udpEnabled) "已开启" else "已关闭"
        return "TCP $tcpStatus，UDP $udpStatus。"
    }
}
