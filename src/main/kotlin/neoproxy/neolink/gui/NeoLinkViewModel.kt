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
import neoproxy.neolink.InternetOperator
import neoproxy.neolink.NeoLink
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Pattern

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
    private val scope = CoroutineScope(Dispatchers.Default)

    fun initialize(args: Array<String>) {
        // 1. 先定环境，再定日志
        ConfigOperator.initEnvironment()

        // 确保 logs 目录存在，彻底防止根目录污染
        File(ConfigOperator.WORKING_DIR, "logs").mkdirs()

        NeoLink.initializeLogger()
        NeoLink.detectLanguage()
        ConfigOperator.readAndSetValue()

        // 重定向 GUI 内部日志到 logs 子目录
        setupLogRedirector()

        NeoLink.printLogo()
        NeoLink.printBasicInfo()
        loadNodes()
        if (NeoLink.shouldAutoStart()) startService()
    }

    private fun loadNodes() {
        // [修改] 使用 ConfigOperator.WORKING_DIR 构造文件路径
        val nodeFile = File(ConfigOperator.WORKING_DIR, "node.json")
        if (!nodeFile.exists()) return
        try {
            var content = Files.readString(nodeFile.toPath(), StandardCharsets.UTF_8).replace("\uFEFF", "").trim()
            if (content.startsWith("[") && content.endsWith("]")) {
                val objectPattern = Pattern.compile("\\{([^{}]+)\\}")
                val matcher = objectPattern.matcher(content)
                while (matcher.find()) {
                    val objStr = matcher.group(1)
                    val name = extractJsonValue(objStr, "name")
                    val address = extractJsonValue(objStr, "address")
                    val icon = extractJsonValue(objStr, "icon")
                    val hookPort = extractJsonValue(objStr, "HOST_HOOK_PORT")?.toIntOrNull() ?: 44801
                    val connPort = extractJsonValue(objStr, "HOST_CONNECT_PORT")?.toIntOrNull() ?: 44802
                    if (!name.isNullOrBlank() && !address.isNullOrBlank()) nodeList.add(
                        NeoNode(
                            name,
                            address,
                            icon,
                            hookPort,
                            connPort
                        )
                    )
                }
                if (nodeList.isNotEmpty()) selectNode(nodeList[0])
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val searchKey = "\"$key\"";
        val startIdx = json.indexOf(searchKey)
        if (startIdx == -1) return null
        var cursor = startIdx + searchKey.length
        while (cursor < json.length && json[cursor] != ':') cursor++; cursor++
        while (cursor < json.length && json[cursor].isWhitespace()) cursor++
        if (cursor >= json.length) return null
        if (json[cursor] == '"') {
            cursor++;
            val sb = StringBuilder();
            var inEscape = false
            while (cursor < json.length) {
                val c = json[cursor]
                if (inEscape) {
                    sb.append(c); inEscape = false
                } else {
                    if (c == '\\') inEscape = true else if (c == '"') return sb.toString() else sb.append(c)
                }
                cursor++
            }; return null
        } else {
            val sb = StringBuilder()
            while (cursor < json.length) {
                val c = json[cursor]; if (c.isDigit()) sb.append(c) else break; cursor++
            }
            return sb.toString()
        }
    }

    fun selectNode(node: NeoNode) {
        selectedNode = node; remoteDomain = node.address; hostHookPort = node.hookPort.toString(); hostConnectPort =
            node.connectPort.toString()
    }

    fun startService() {
        if (isRunning || remoteDomain.isBlank() || localPort.isBlank() || accessKey.isBlank()) return
        NeoLink.remoteDomainName = remoteDomain; NeoLink.localPort = localPort.toIntOrNull() ?: -1; NeoLink.key =
            accessKey
        NeoLink.localDomainName = localDomain; NeoLink.hostHookPort =
            hostHookPort.toIntOrNull() ?: 44801; NeoLink.hostConnectPort = hostConnectPort.toIntOrNull() ?: 44802
        NeoLink.isDisableTCP = !isTcpEnabled; NeoLink.isDisableUDP = !isUdpEnabled; NeoLink.enableProxyProtocol =
            isPpv2Enabled; NeoLink.enableAutoReconnect = isAutoReconnect; NeoLink.isDebugMode =
            isDebugMode; NeoLink.showConnection = isShowConnection
        isRunning = true
        scope.launch(Dispatchers.IO) {
            try {
                NeoLinkCoreRunner.runCore(NeoLink.remoteDomainName, NeoLink.localPort, NeoLink.key)
            } finally {
                withContext(Dispatchers.Main) { isRunning = false; appendLog("\n[SYSTEM] 服务已停止") }
            }
        }
    }

    fun stopService() {
        if (!isRunning) return
        NeoLink.say("正在停止 NeoLink 服务...")
        scope.launch(Dispatchers.IO) {
            NeoLinkCoreRunner.requestStop()
            try {
                if (NeoLink.connectingSocket != null) NeoLink.connectingSocket.close()
            } catch (e: Exception) {
            }
            try {
                if (NeoLink.hookSocket != null) InternetOperator.close(NeoLink.hookSocket)
            } catch (e: Exception) {
            }
        }
    }

    private fun setupLogRedirector() {
        val originalLoggist = NeoLink.loggist
        // 关键：将 gui_internal.log 强制放入 logs 文件夹
        val internalLogFile = File(ConfigOperator.WORKING_DIR, "logs/gui_internal.log")

        NeoLink.loggist = object : `fun`.ceroxe.api.print.log.Loggist(internalLogFile) {
            override fun say(state: `fun`.ceroxe.api.print.log.State) {
                addLogSafe(getLogString(state)); originalLoggist?.say(state)
            }

            override fun sayNoNewLine(state: `fun`.ceroxe.api.print.log.State) {
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