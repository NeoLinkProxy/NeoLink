package neoproxy.neolink.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import neoproxy.neolink.config.ConfigOperator
import neoproxy.neolink.config.NodeConfig
import neoproxy.neolink.core.NeoLinkCoreRunner
import top.ceroxe.api.neolink.NeoLinkCfg
import top.ceroxe.api.neolink.NodeFetcher
import java.io.File

/**
 * NeoLink 图形界面视图模型
 *
 * 核心职责：
 * 1. 管理图形界面的所有状态数据
 * 2. 处理用户输入和配置变更
 * 3. 协调后台服务的启动和停止
 * 4. 管理日志显示和终端颜色解析
 * 5. 加载和解析节点列表
 *
 * 架构设计：
 * - 使用 Compose 状态实现响应式数据绑定
 * - 使用协程作用域处理异步操作
 * - 通过自定义 Loggist 包装器实现日志重定向
 *
 * 状态管理：
 * - 连接配置：远程域名、本地端口、访问密钥等
 * - 功能开关：TCP/UDP 启用、代理协议、自动重连等
 * - 运行时状态：服务运行状态、日志消息列表
 *
 * @author NeoProxy Team
 * @since 5.0.0
 */
class NeoLinkViewModel {
    var remoteDomain by mutableStateOf(NeoLink.remoteDomainName)
    var localPort by mutableStateOf(if (NeoLink.localPort == -1) "" else NeoLink.localPort.toString())
    var accessKey by mutableStateOf(NeoLink.key ?: "")
    var nodeList = mutableStateListOf<NeoNode>()
    var selectedNode by mutableStateOf<NeoNode?>(null)
    var localDomain by mutableStateOf(NeoLink.localDomainName)
    var hostHookPort by mutableStateOf(NeoLink.hostHookPort.toString())
    var hostConnectPort by mutableStateOf(NeoLink.hostConnectPort.toString())
    var isTcpEnabled by mutableStateOf(!NeoLink.isDisableTCP)
    var isUdpEnabled by mutableStateOf(!NeoLink.isDisableUDP)
    var isPpv2Enabled by mutableStateOf(NeoLink.enableProxyProtocol)
    var isAutoReconnect by mutableStateOf(NeoLink.enableAutoReconnect)
    var isDebugMode by mutableStateOf(NeoLink.isDebugMode)
    var isShowConnection by mutableStateOf(NeoLink.showConnection)
    var isRunning by mutableStateOf(false)
    val logMessages = mutableStateListOf<AnnotatedString>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var logFontSize by mutableStateOf(12.sp)

    private var isInitialized = false
    private var isLogRedirected = false
    fun initialize(args: Array<String>) {
        // [修复] 增加初始化锁，防止窗口重建导致重复调用
        if (isInitialized) return
        isInitialized = true

        ConfigOperator.initEnvironment()
        File(ConfigOperator.WORKING_DIR, "logs").mkdirs()

        ConfigOperator.readAndSetValue()
        NeoLink.applyCommandLineArgs(args)
        NeoLink.detectLanguage()
        syncStateFromCore()
        NeoLink.initializeLogger()

        setupLogRedirector()

        NeoLink.printLogo()
        NeoLink.printBasicInfo()

        // 【优化】使用协程后台加载节点，防止阻塞 GUI 初始化
        scope.launch(Dispatchers.IO) {
            fetchNodesFromApi()
            withContext(Dispatchers.Main) {
                loadNodes()
            }
        }

        if (NeoLink.shouldAutoStart()) startService()
    }


    private fun loadNodes() {
        // [修改] 使用 ConfigOperator.WORKING_DIR 构造文件路径
        val nodeFile = File(ConfigOperator.WORKING_DIR, NodeConfig.NODE_LIST_FILE_NAME)
        if (!nodeFile.exists()) return
        try {
            nodeList.clear()
            for (node in NodeConfig.loadAll(nodeFile)) {
                nodeList.add(
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
            if (nodeList.isNotEmpty()) selectNode(nodeList[0])
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun syncStateFromCore() {
        remoteDomain = NeoLink.remoteDomainName
        localPort = if (NeoLink.localPort == -1) "" else NeoLink.localPort.toString()
        accessKey = NeoLink.key ?: ""
        localDomain = NeoLink.localDomainName
        hostHookPort = NeoLink.hostHookPort.toString()
        hostConnectPort = NeoLink.hostConnectPort.toString()
        isTcpEnabled = !NeoLink.isDisableTCP
        isUdpEnabled = !NeoLink.isDisableUDP
        isPpv2Enabled = NeoLink.enableProxyProtocol
        isAutoReconnect = NeoLink.enableAutoReconnect
        isDebugMode = NeoLink.isDebugMode
        isShowConnection = NeoLink.showConnection
    }

    fun selectNode(node: NeoNode) {
        selectedNode = node; remoteDomain = node.address; hostHookPort = node.hookPort.toString(); hostConnectPort =
            node.connectPort.toString()
    }

    fun startService() {
        if (isRunning || remoteDomain.isBlank() || localPort.isBlank() || accessKey.isBlank() || localDomain.isBlank()) return
        val parsedLocalPort = localPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: return
        val parsedHookPort = hostHookPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: return
        val parsedConnectPort = hostConnectPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: return
        val tunnelConfig = buildTunnelConfig(parsedLocalPort, parsedHookPort, parsedConnectPort)
        NeoLink.localDomainName = localDomain.trim()
        NeoLink.isDisableTCP = !isTcpEnabled; NeoLink.isDisableUDP = !isUdpEnabled; NeoLink.enableProxyProtocol =
            isPpv2Enabled; NeoLink.enableAutoReconnect = isAutoReconnect; NeoLink.isDebugMode =
            isDebugMode; NeoLink.showConnection = isShowConnection
        isRunning = true
        scope.launch(Dispatchers.IO) {
            try {
                NeoLinkCoreRunner.runCore(tunnelConfig)
            } finally {
                withContext(Dispatchers.Main) { isRunning = false; appendLog("\n[SYSTEM] 服务已停止") }
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
        if (!isRunning) return
        NeoLink.say("正在停止 NeoLink 服务...")
        scope.launch(Dispatchers.IO) {
            NeoLinkCoreRunner.requestStop()
        }
    }

    private fun fetchNodesFromApi() {
        if (NeoLink.nkmNodeListUrl.isBlank()) return

        NeoLink.say(
            NeoLink.languageData.FETCHING_NODE_LIST + NeoLink.nkmNodeListUrl,
            top.ceroxe.api.print.log.LogType.INFO
        )
        try {
            val nodes = NodeFetcher.getFromNKM(NeoLink.nkmNodeListUrl)
            if (nodes.isEmpty()) {
                NeoLink.say(NeoLink.languageData.NODE_LIST_EMPTY, top.ceroxe.api.print.log.LogType.INFO)
                return
            }
            NodeConfig.saveAll(File(ConfigOperator.WORKING_DIR, NodeConfig.NODE_LIST_FILE_NAME), nodes.values)
            NeoLink.say(NeoLink.languageData.NODE_LIST_FETCH_SUCCESS, top.ceroxe.api.print.log.LogType.INFO)
        } catch (e: Exception) {
            neoproxy.neolink.util.Debugger.debugOperation(e)
            NeoLink.say(NeoLink.languageData.NODE_LIST_FETCH_FAIL, top.ceroxe.api.print.log.LogType.WARNING)
        }
    }

    private fun setupLogRedirector() {
        // [修复] 检查是否已经重定向过，防止递归包装导致日志重复
        if (isLogRedirected) return
        isLogRedirected = true

        val originalLoggist = NeoLink.loggist
        // 关键：将 gui_internal.log 强制放入 logs 文件夹
        val internalLogFile = File(ConfigOperator.WORKING_DIR, "logs/gui_internal.log")

        NeoLink.loggist = object : top.ceroxe.api.print.log.Loggist(internalLogFile) {
            override fun say(state: top.ceroxe.api.print.log.State) {
                addLogSafe(getLogString(state)); originalLoggist?.say(state)
            }

            override fun sayNoNewLine(state: top.ceroxe.api.print.log.State) {
                addLogSafe(getLogString(state)); originalLoggist?.sayNoNewLine(state)
            }

            override fun write(str: String?, isNewLine: Boolean) {
                originalLoggist?.write(str, isNewLine)
            }
        }
    }


    private fun addLogSafe(ansiMsg: String) {
        scope.launch(Dispatchers.Main) {
            val styled = parseAnsi(ansiMsg); logMessages.add(styled); if (logMessages.size > 1000) logMessages.removeAt(
            0
        )
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
