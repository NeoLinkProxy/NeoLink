package neoproxy.neolink.gui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import neoproxy.neolink.config.ConfigOperator
import neoproxy.neolink.state.RuntimeState
import neoproxy.neolink.util.LogSink
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
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("appendSystemLog keeps GUI and LogSink content identical")
    fun appendSystemLogKeepsGuiAndLogSinkContentIdentical() = runTest(mainDispatcher) {
        val capturedMessages = mutableListOf<String>()
        val viewModel = NeoLinkViewModel()

        ConfigOperator.WORKING_DIR = tempDir.toString()
        RuntimeState.setLogSink { _, _, message -> capturedMessages.add(message) }
        invokePrivate(viewModel, "setupLogRedirector")

        viewModel.appendLog("前置日志")
        advanceUntilIdle()

        invokePrivate(viewModel, "appendSystemLog", "服务已停止。", true)
        advanceUntilIdle()

        val guiMessages = viewModel.runtimeState.logMessages.map { it.text }
        assertEquals(2, guiMessages.size)
        assertEquals("前置日志", guiMessages[0])
        assertEquals("[GUI] \n[System] 服务已停止。\n\n", guiMessages[1])

        assertTrue(
            capturedMessages.contains("\n[System] 服务已停止。\n"),
            "LogSink 必须接收到和 GUI 中同源的 [System] 消息文本"
        )
    }

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
}
