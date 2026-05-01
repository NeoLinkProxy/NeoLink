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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import top.ceroxe.api.print.log.Loggist
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("NeoLinkViewModelTest")
class NeoLinkViewModelTest {
    @TempDir
    lateinit var tempDir: Path

    private val mainDispatcher = UnconfinedTestDispatcher()
    private var originalLoggist: Loggist? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        originalLoggist = RuntimeState.loggist()
    }

    @AfterEach
    fun tearDown() {
        RuntimeState.setLoggist(originalLoggist)
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("appendSystemLog keeps GUI and file content identical")
    fun appendSystemLogKeepsGuiAndFileContentIdentical() = runTest(mainDispatcher) {
        val persistedLogFile = tempDir.resolve("gui-visible.log")
        val originalFileLoggist = Loggist(persistedLogFile.toFile())
        val viewModel = NeoLinkViewModel()

        try {
            ConfigOperator.WORKING_DIR = tempDir.toString()
            RuntimeState.setLoggist(originalFileLoggist)
            invokePrivate(viewModel, "setupLogRedirector")

            viewModel.appendLog("前置日志")
            advanceUntilIdle()

            invokePrivate(viewModel, "appendSystemLog", "服务已停止。", true)
            advanceUntilIdle()

            val guiMessages = viewModel.runtimeState.logMessages.map { it.text }
            assertEquals(2, guiMessages.size)
            assertEquals("前置日志", guiMessages[0])
            assertEquals("\n[System] 服务已停止。\n", guiMessages[1])

            RuntimeState.loggist()?.close()
        } finally {
            originalFileLoggist.close()
        }

        val persistedContent = Files.readString(persistedLogFile, StandardCharsets.UTF_8)
        assertTrue(
            persistedContent.contains("\n[System] 服务已停止。\n"),
            "log 文件必须和 GUI 中的 [System] 消息文本完全一致"
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
