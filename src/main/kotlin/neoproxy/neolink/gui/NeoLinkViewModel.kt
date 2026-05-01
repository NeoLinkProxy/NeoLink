package neoproxy.neolink.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import neoproxy.neolink.NeoLink
import neoproxy.neolink.app.LanguageManager
import neoproxy.neolink.cli.ClientConsole
import neoproxy.neolink.cli.CommandLineProcessor
import neoproxy.neolink.config.ConfigOperator
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
 * View-model boundary for NeoLink desktop UI.
 *
 * The UI keeps editable form state locally and commits into the dedicated core state objects only at
 * explicit synchronization points. This prevents Compose recomposition from mutating runtime globals
 * and keeps CLI behavior isolated from GUI-only state transitions.
 */
class NeoLinkViewModel {
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
    fun initialize(args: Array<String>) {        // 防止 Compose 重建界面时重复初始化（duplicate initialization）。
        if (isInitialized) return
        isInitialized = true

        ConfigOperator.initEnvironment()
        File(ConfigOperator.WORKING_DIR, "logs").mkdirs()

        ConfigOperator.readAndSetValue()
        CommandLineProcessor.applyCommandLineArgs(args)
        LanguageManager.detectLanguage()
        syncStateFromCore()
        ClientConsole.initializeLogger(false)

        setupLogRedirector()

        ClientConsole.printLogo()
        ClientConsole.printBasicInfo()        // 异步刷新公开节点（public nodes），避免首次渲染被网络 I/O 阻塞。
        scope.launch(Dispatchers.IO) {
            NodeWorkflow.fetchAndSaveNodes()
            withContext(Dispatchers.Main) {
                loadNodes()
            }
        }

        if (NeoLink.shouldAutoStart()) startService()
    }


    private fun loadNodes() {        // 从 CLI / GUI 共用的可写工作目录加载节点缓存。
        val nodeFile = File(ConfigOperator.WORKING_DIR, NodeConfig.NODE_LIST_FILE_NAME)
        if (!nodeFile.exists()) return
        try {
            val loadedNodes = mutableListOf<NeoNode>()
            for (node in NodeConfig.loadAll(nodeFile)) {
                loadedNodes.add(
                    NeoNode(
                        node.name,
                        node.realId,
                        node.address,
                        node.icon,
                        node.hostHookPort,
                        node.hostConnectPort
                    )
                )
            }
            uiState = uiState.copy(
                nodeList = loadedNodes,
                selectedNode = loadedNodes.firstOrNull()
            )
            loadedNodes.firstOrNull()?.let(::selectNode)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
    }

    fun updateTransportProtocols(tcpEnabled: Boolean, udpEnabled: Boolean) {
        val previousTcpEnabled = isTcpEnabled
        val previousUdpEnabled = isUdpEnabled

        featureState = featureState.copy(tcpEnabled = tcpEnabled, udpEnabled = udpEnabled)

        if (!isRunning) {
            FeatureState.applyRuntimeTransportSelection(tcpEnabled, udpEnabled)
            appendLog(
                "[系统] 已更新协议开关（protocol flags）：TCP=" +
                        if (tcpEnabled) "开启" else "关闭" +
                        "，UDP=" +
                        if (udpEnabled) "开启" else "关闭"
            )
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                NeoLinkCoreRunner.updateRuntimeProtocolFlags(tcpEnabled, udpEnabled)
                appendLog(
                        "[系统] 运行时协议已更新（runtime protocol update）：TCP=" +
                                if (tcpEnabled) "开启" else "关闭" +
                                "，UDP=" +
                                if (udpEnabled) "开启" else "关闭"
                )
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    featureState = featureState.copy(
                        tcpEnabled = previousTcpEnabled,
                        udpEnabled = previousUdpEnabled
                    )
                    FeatureState.applyRuntimeTransportSelection(previousTcpEnabled, previousUdpEnabled)
                    appendLog("[系统] 运行时协议更新失败：${e.message}")
                }
            }
        }
    }

    fun startService() {
        if (isRunning || isStopping || remoteDomain.isBlank() || localPort.isBlank() || accessKey.isBlank() || localDomain.isBlank()) return
        val parsedLocalPort = localPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: return
        val parsedHookPort = hostHookPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: return
        val parsedConnectPort = hostConnectPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: return
        val tunnelConfig = buildTunnelConfig(parsedLocalPort, parsedHookPort, parsedConnectPort)
        val currentFeatures = FeatureState.snapshot()
        ConnectionState.apply(
            ConnectionSettings(
                remoteDomain.trim(),
                localDomain.trim(),
                parsedHookPort,
                parsedConnectPort,
                accessKey,
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
                    appendLog("[系统] 服务已停止。")
                }
            }
        }
    }

    private fun buildTunnelConfig(parsedLocalPort: Int, parsedHookPort: Int, parsedConnectPort: Int): NeoLinkCfg {
        val selected = selectedNode
        return if (selected != null && remoteDomain.trim() == selected.address) {
            selected.toCfg(accessKey, parsedLocalPort)
        } else {
            NeoLinkCfg(remoteDomain.trim(), parsedHookPort, parsedConnectPort, accessKey, parsedLocalPort)
        }
    }

    fun dispose() {
        stopService()
        scope.cancel()
    }

    fun stopService() {
        if (!isRunning || isStopping) return
        isStopping = true
        ClientConsole.say("正在停止 NeoLink 服务（service）...")
        scope.launch(Dispatchers.IO) {
            NeoLinkCoreRunner.requestStop()
        }
    }

    private fun setupLogRedirector() {        // 只安装一次 GUI 日志桥（GUI log bridge），原始 logger 继续负责文件落盘。
        if (isLogRedirected) return
        isLogRedirected = true

        val originalLoggist = RuntimeState.loggist()        // GUI 桥的内部诊断日志仍落在常规 logs 目录。
        val internalLogFile = File(ConfigOperator.WORKING_DIR, "logs/gui_internal.log")

        RuntimeState.setLoggist(object : top.ceroxe.api.print.log.Loggist(internalLogFile) {
            override fun say(state: top.ceroxe.api.print.log.State) {
                addLogSafe(getLogString(state)); originalLoggist?.say(state)
            }

            override fun sayNoNewLine(state: top.ceroxe.api.print.log.State) {
                addLogSafe(getLogString(state)); originalLoggist?.sayNoNewLine(state)
            }

            override fun write(str: String?, isNewLine: Boolean) {
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
            val ansiRegex = Regex("\u001B\\[([0-9;]*)m");
            var lastIndex = 0;
            var currentStyle = SpanStyle(color = Color(0xFFCCCCCC))
            ansiRegex.findAll(text).forEach { result ->
                val beforeText = text.substring(lastIndex, result.range.first)
                if (beforeText.isNotEmpty()) withStyle(currentStyle) { append(beforeText) }
                val code = result.groupValues[1]
                currentStyle = when (code) {
                    "31" -> SpanStyle(color = Color(0xFFFF5555)); "32" -> SpanStyle(color = Color(0xFF50FA7B)); "33" -> SpanStyle(
                        color = Color(0xFFF1FA8C)
                    ); "34" -> SpanStyle(color = Color(0xFFBD93F9)); "36" -> SpanStyle(color = Color(0xFF8BE9FD)); else -> SpanStyle(
                        color = Color(0xFFCCCCCC)
                    )
                }
                lastIndex = result.range.last + 1
            }
            if (lastIndex < text.length) withStyle(currentStyle) { append(text.substring(lastIndex)) }
        }
    }

    fun appendLog(ansiText: String) {
        addLogSafe(ansiText)
    }
}




