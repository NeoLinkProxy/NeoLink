package neoproxy.neolink.gui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import neoproxy.neolink.config.ConfigOperator
import neoproxy.neolink.node.NodeWorkflow
import neoproxy.neolink.state.RuntimeState
import neoproxy.neolink.util.LogSink
import neoproxy.neolink.util.MessageSink
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("NeoLinkViewModelTest")
class NeoLinkViewModelTest {
    @TempDir
    lateinit var tempDir: Path

    private val mainDispatcher = UnconfinedTestDispatcher()
    private var originalLogSink: LogSink? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        originalLogSink = RuntimeState.logSink()
    }

    @AfterEach
    fun tearDown() {
        RuntimeState.setLogSink(originalLogSink)
        NodeWorkflow.setMessageSink { _, _ -> }
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("appendSystemLog keeps 7.1.6 write-style GUI content")
    fun appendSystemLogKeepsLegacyWriteStyleGuiContent() = runTest(mainDispatcher) {
        val capturedMessages = mutableListOf<String>()
        val viewModel = NeoLinkViewModel()

        ConfigOperator.WORKING_DIR = tempDir.toString()
        RuntimeState.setLogSink { _, _, message -> capturedMessages.add(message) }
        invokePrivate(viewModel, "setupLogRedirector")

        viewModel.appendLog("前置日志")
        advanceUntilIdle()

        invokePrivate(viewModel, "appendSystemLog", "服务已停止。", true)
        advanceUntilIdle()

        val guiMessages = viewModel.runtimeState.logMessages.map(::stripAnsi)
        assertEquals(2, guiMessages.size)
        assertEquals("前置日志", guiMessages[0])
        assertEquals("\n[System] 服务已停止。\n", guiMessages[1])

        assertTrue(
            capturedMessages.contains("\n[System] 服务已停止。\n"),
            "GUI write-style 系统日志仍要转发到上游日志文件"
        )
    }

    @Test
    @DisplayName("LogSink keeps 7.1 GUI log structure")
    fun logSinkKeepsLegacyGuiLogStructure() = runTest(mainDispatcher) {
        val viewModel = NeoLinkViewModel()

        ConfigOperator.WORKING_DIR = tempDir.toString()
        RuntimeState.setLogSink(null)
        invokePrivate(viewModel, "setupLogRedirector")

        RuntimeState.logSink()!!.log(LogSink.Level.INFO, "HOST-CLIENT", "API版本 ： 7.2.0")
        RuntimeState.logSink()!!.log(LogSink.Level.WARNING, "", "有效期至： 2027/01/01-12:00")
        advanceUntilIdle()

        val guiMessages = viewModel.runtimeState.logMessages.map(::stripAnsi)
        assertTrue(
            Regex("^\\[\\d{4}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}]  \\[INFO] \\[HOST-CLIENT] API版本 ： 7.2.0$")
                .matches(guiMessages[0]),
            "普通 GUI 日志必须保持 7.1.x 的时间、级别、Subject 输出结构"
        )
        assertTrue(
            Regex("^\\[\\d{4}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}]  \\[WARNING] \\[HOST-CLIENT] 有效期至： 2027/01/01-12:00$")
                .matches(guiMessages[1]),
            "空 tag 必须回退为 HOST-CLIENT，且保留 WARNING 级别"
        )
    }

    @Test
    @DisplayName("NodeWorkflow messages are rendered as HOST-CLIENT GUI logs")
    fun nodeWorkflowMessagesAreRenderedAsHostClientGuiLogs() = runTest(mainDispatcher) {
        val viewModel = NeoLinkViewModel()

        ConfigOperator.WORKING_DIR = tempDir.toString()
        RuntimeState.setLogSink(null)
        invokePrivate(viewModel, "setupLogRedirector")
        invokePrivate(viewModel, "setupNodeWorkflowMessageSink")

        val nodeWorkflowSink = nodeWorkflowMessageSink()
        nodeWorkflowSink.say(
            "正在向 NKM 获取最新可用节点列表: https://p.ceroxe.top:49999/client/nodelist",
            LogSink.Level.INFO
        )
        nodeWorkflowSink.say("节点列表已成功更新。", LogSink.Level.INFO)
        nodeWorkflowSink.say("获取节点列表失败或超时 (已跳过): ", LogSink.Level.WARNING)
        advanceUntilIdle()

        val guiMessages = viewModel.runtimeState.logMessages.map(::stripAnsi)
        assertTrue(
            Regex("^\\[\\d{4}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}]  \\[INFO] \\[HOST-CLIENT] 正在向 NKM 获取最新可用节点列表: https://p\\.ceroxe\\.top:49999/client/nodelist$")
                .matches(guiMessages[0]),
            "NKM 拉取日志必须进入 GUI，并保持 7.1.6 的 HOST-CLIENT 运行日志结构"
        )
        assertTrue(
            Regex("^\\[\\d{4}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}]  \\[INFO] \\[HOST-CLIENT] 节点列表已成功更新。$")
                .matches(guiMessages[1]),
            "NKM 成功日志必须进入 GUI，并保持 7.1.6 的 HOST-CLIENT 运行日志结构"
        )
        assertTrue(
            Regex("^\\[\\d{4}\\.\\d{2}\\.\\d{2} \\d{2}:\\d{2}:\\d{2}]  \\[WARNING] \\[HOST-CLIENT] 获取节点列表失败或超时 \\(已跳过\\): $")
                .matches(guiMessages[2]),
            "NKM 失败日志必须进入 GUI，并保持 7.1.6 的 WARNING HOST-CLIENT 运行日志结构"
        )
    }

    private fun nodeWorkflowMessageSink(): MessageSink {
        val field = NodeWorkflow::class.java.getDeclaredField("messageSink")
        field.isAccessible = true
        return field.get(null) as MessageSink
    }

    private fun stripAnsi(text: String): String = text.replace(AnsiEscapeRegex, "")

    private fun invokePrivate(target: Any, methodName: String, vararg args: Any) {
        val parameterTypes = args.map {
            when (it) {
                is Boolean -> java.lang.Boolean.TYPE
                is Int -> java.lang.Integer.TYPE
                is Long -> java.lang.Long.TYPE
                is Double -> java.lang.Double.TYPE
                is Float -> java.lang.Float.TYPE
                else -> it.javaClass
            }
        }.toTypedArray()
        val method = target.javaClass.getDeclaredMethod(methodName, *parameterTypes)
        method.isAccessible = true
        method.invoke(target, *args)
    }

    private companion object {
        val AnsiEscapeRegex = Regex("\u001B\\[[0-9;]*m")
    }
}
