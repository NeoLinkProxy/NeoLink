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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neoproxy.neolink.ConfigOperator
import neoproxy.neolink.NeoLink
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern

class NeoLinkViewModel {
    // --- UI State ---
    var remoteDomain by mutableStateOf(NeoLink.remoteDomainName)
    var localPort by mutableStateOf(if (NeoLink.localPort == -1) "" else NeoLink.localPort.toString())
    var accessKey by mutableStateOf(NeoLink.key ?: "")

    // 节点选择
    var nodeList = mutableStateListOf<NeoNode>()
    var selectedNode by mutableStateOf<NeoNode?>(null)

    // 高级设置
    var localDomain by mutableStateOf(NeoLink.localDomainName)
    var hostHookPort by mutableStateOf(NeoLink.hostHookPort.toString())
    var hostConnectPort by mutableStateOf(NeoLink.hostConnectPort.toString())

    // 开关状态
    var isTcpEnabled by mutableStateOf(!NeoLink.isDisableTCP)
    var isUdpEnabled by mutableStateOf(!NeoLink.isDisableUDP)
    var isPpv2Enabled by mutableStateOf(NeoLink.enableProxyProtocol)
    var isAutoReconnect by mutableStateOf(NeoLink.enableAutoReconnect)
    var isDebugMode by mutableStateOf(NeoLink.isDebugMode)
    var isShowConnection by mutableStateOf(NeoLink.showConnection)

    // 运行状态
    var isRunning by mutableStateOf(false)

    // 日志
    val logMessages = mutableStateListOf<AnnotatedString>()

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Default)

    fun initialize(args: Array<String>) {
        // 1. 初始化基础环境
        NeoLink.initializeLogger()
        NeoLink.detectLanguage()
        ConfigOperator.readAndSetValue()

        // 2. 设置重定向器 (确保 UI 能接收到后续的 say 调用)
        setupLogRedirector()

        // 3. 【关键】复刻 JavaFX 的行为：在界面加载后立即打印
        // 注意：这里必须手动调用，因为之前的 CLI 逻辑可能已经运行过了
        NeoLink.printLogo()
        NeoLink.printBasicInfo()

        // 4. 加载节点
        loadNodes()

        if (NeoLink.shouldAutoStart()) {
            startService()
        }
    }

    private fun loadNodes() {
        val nodeFile = ConfigOperator.NODE_FILE
        if (!nodeFile.exists()) return

        try {
            var content = Files.readString(nodeFile.toPath(), StandardCharsets.UTF_8)
            content = content.replace("\uFEFF", "").trim() // 去除 BOM

            // 简单的手动 JSON 解析 (复刻原逻辑)
            if (content.startsWith("[") && content.endsWith("]")) {
                val objectPattern = Pattern.compile("\\{([^{}]+)\\}")
                val matcher = objectPattern.matcher(content)
                while (matcher.find()) {
                    val objStr = matcher.group(1)
                    val name = extractJsonValue(objStr, "name")
                    val address = extractJsonValue(objStr, "address")
                    val icon = extractJsonValue(objStr, "icon") // SVG String
                    val hookPort = extractJsonValue(objStr, "HOST_HOOK_PORT")?.toIntOrNull() ?: 44801
                    val connPort = extractJsonValue(objStr, "HOST_CONNECT_PORT")?.toIntOrNull() ?: 44802

                    if (!name.isNullOrBlank() && !address.isNullOrBlank()) {
                        nodeList.add(NeoNode(name, address, icon, hookPort, connPort))
                    }
                }
                if (nodeList.isNotEmpty()) {
                    selectNode(nodeList[0])
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 复刻原 Java 简单的 JSON 提取逻辑
    private fun extractJsonValue(json: String, key: String): String? {
        val searchKey = "\"$key\""
        val startIdx = json.indexOf(searchKey)
        if (startIdx == -1) return null

        var cursor = startIdx + searchKey.length
        while (cursor < json.length && json[cursor] != ':') cursor++ // 找冒号
        cursor++ // 跳过冒号
        while (cursor < json.length && json[cursor].isWhitespace()) cursor++ // 跳过空格

        if (cursor >= json.length) return null

        if (json[cursor] == '"') {
            // 字符串值
            cursor++
            val sb = StringBuilder()
            var inEscape = false
            while (cursor < json.length) {
                val c = json[cursor]
                if (inEscape) {
                    sb.append(c)
                    inEscape = false
                } else {
                    if (c == '\\') inEscape = true
                    else if (c == '"') return sb.toString()
                    else sb.append(c)
                }
                cursor++
            }
        } else {
            // 数字值
            val sb = StringBuilder()
            while (cursor < json.length) {
                val c = json[cursor]
                if (c.isDigit()) sb.append(c) else break
                cursor++
            }
            return sb.toString()
        }
        return null
    }

    fun selectNode(node: NeoNode) {
        selectedNode = node
        remoteDomain = node.address
        hostHookPort = node.hookPort.toString()
        hostConnectPort = node.connectPort.toString()
    }

    fun startService() {
        if (isRunning) return
        if (remoteDomain.isBlank() || localPort.isBlank() || accessKey.isBlank()) return

        // 同步配置到 NeoLink 静态变量
        NeoLink.remoteDomainName = remoteDomain
        NeoLink.localPort = localPort.toIntOrNull() ?: -1
        NeoLink.key = accessKey
        NeoLink.localDomainName = localDomain
        NeoLink.hostHookPort = hostHookPort.toIntOrNull() ?: 44801
        NeoLink.hostConnectPort = hostConnectPort.toIntOrNull() ?: 44802

        NeoLink.isDisableTCP = !isTcpEnabled
        NeoLink.isDisableUDP = !isUdpEnabled
        NeoLink.enableProxyProtocol = isPpv2Enabled
        NeoLink.enableAutoReconnect = isAutoReconnect
        NeoLink.isDebugMode = isDebugMode
        NeoLink.showConnection = isShowConnection

        isRunning = true

        // 注册停止回调
        NeoLinkCoreRunner.setStopCallback {
            // 切回 UI 线程更新状态
            scope.launch(Dispatchers.Main) {
                isRunning = false
                appendLog("\n服务已停止")
            }
        }

        // 异步启动
        scope.launch(Dispatchers.IO) {
            try {
                NeoLinkCoreRunner.runCore(NeoLink.remoteDomainName, NeoLink.localPort, NeoLink.key)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isRunning = false
                }
            }
        }
    }

    fun stopService() {
        if (!isRunning) return
        NeoLink.say("正在停止 NeoLink 服务...")
        scope.launch(Dispatchers.IO) {
            NeoLinkCoreRunner.requestStop()
            // 强制关闭 Socket 逻辑在 Java 侧已实现，这里触发即可
        }
    }

    private fun setupLogRedirector() {
        // 获取 initializeLogger 创建的原始文件日志对象
        val originalLoggist = NeoLink.loggist

        // 关键修复：提供一个带有父目录的绝对路径文件，防止 Loggist 内部创建目录失败
        val proxyLogFile = File(NeoLink.CURRENT_DIR_PATH, "gui_internal.log")

        // 创建代理 Loggist
        NeoLink.loggist = object : `fun`.ceroxe.api.print.log.Loggist(proxyLogFile) {
            override fun say(state: `fun`.ceroxe.api.print.log.State) {
                // 1. 提取带 ANSI 颜色的文字发送到 Compose UI (ViewModel 的列表)
                val msg = this.getLogString(state)
                addLogSafe(msg)

                // 2. 同时调用原始 Loggist，确保日志依然会写进原来的 .log 文件
                originalLoggist?.say(state)
            }

            override fun sayNoNewLine(state: `fun`.ceroxe.api.print.log.State) {
                val msg = this.getLogString(state)
                addLogSafe(msg)
                originalLoggist?.sayNoNewLine(state)
            }

            override fun write(str: String?, isNewLine: Boolean) {
                // 转发给原始日志处理器
                originalLoggist?.write(str, isNewLine)
            }
        }
    }

    private fun addLogSafe(ansiMsg: String) {
        scope.launch(Dispatchers.Main) {
            val styled = parseAnsi(ansiMsg)
            logMessages.add(styled)
            if (logMessages.size > 1000) logMessages.removeAt(0)
        }
    }

    private fun parseAnsi(text: String): AnnotatedString {
        return buildAnnotatedString {
            val ansiRegex = Regex("\u001B\\[([0-9;]*)m")
            var lastIndex = 0
            var currentStyle = SpanStyle(color = Color(0xFFCCCCCC)) // 默认灰色

            ansiRegex.findAll(text).forEach { result ->
                // 添加代码之前的普通文字
                val beforeText = text.substring(lastIndex, result.range.first)
                if (beforeText.isNotEmpty()) {
                    withStyle(currentStyle) { append(beforeText) }
                }

                // 解析 ANSI 代码
                val code = result.groupValues[1]
                currentStyle = when (code) {
                    "0" -> SpanStyle(color = Color(0xFFCCCCCC)) // 重置
                    "31" -> SpanStyle(color = Color(0xFFFF5555)) // Red
                    "32" -> SpanStyle(color = Color(0xFF50FA7B)) // Green
                    "33" -> SpanStyle(color = Color(0xFFF1FA8C)) // Yellow
                    "34" -> SpanStyle(color = Color(0xFFBD93F9)) // Blue/Purple
                    "35" -> SpanStyle(color = Color(0xFFFF79C6)) // Magenta
                    "36" -> SpanStyle(color = Color(0xFF8BE9FD)) // Cyan
                    "37" -> SpanStyle(color = Color(0xFFF8F8F2)) // White
                    else -> currentStyle // 其他代码保持当前颜色
                }

                lastIndex = result.range.last + 1
            }

            // 添加剩余文字
            if (lastIndex < text.length) {
                withStyle(currentStyle) {
                    append(text.substring(lastIndex))
                }
            }
        }
    }

    // 请确保这段代码在 NeoLinkViewModel 类的大括号内
    fun appendLog(ansiText: String) {
        // 切换到主线程更新 UI 状态
        scope.launch(Dispatchers.Main) {
            val styled = parseAnsi(ansiText)
            logMessages.add(styled)
            if (logMessages.size > 1000) logMessages.removeAt(0)
        }
    }
}