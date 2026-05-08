package neoproxy.neolink.gui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import neoproxy.neolink.config.ConfigOperator
import neoproxy.neolink.state.RuntimeState
import neoproxy.neolink.util.LogSink
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("NeoLinkViewModelTest")
class NeoLinkViewModelTest {
    @TempDir
    lateinit var tempDir: Path

    private val mainDispatcher = UnconfinedTestDispatcher()
    private var originalLogSink: LogSink? = null
    private var originalWorkingDir: String? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        originalLogSink = RuntimeState.logSink()
        originalWorkingDir = ConfigOperator.WORKING_DIR
        ConfigOperator.setWorkingDirectoryProvider { tempDir }
        ConfigOperator.WORKING_DIR = tempDir.toString()
    }

    @AfterEach
    fun tearDown() {
        RuntimeState.setLogSink(originalLogSink)
        ConfigOperator.setWorkingDirectoryProvider(null)
        ConfigOperator.WORKING_DIR = originalWorkingDir
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("appendSystemLog keeps GUI and LogSink content identical")
    fun appendSystemLogKeepsGuiAndLogSinkContentIdentical() = runTest(mainDispatcher) {
        val capturedMessages = mutableListOf<String>()
        val viewModel = NeoLinkViewModel()

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

    @Test
    @DisplayName("creatableTunnelCount ignores stale local tunnels whose keys no longer exist in NAS")
    fun creatableTunnelCountIgnoresStaleLocalTunnels() {
        val viewModel = NeoLinkViewModel()

        viewModel.keys.addAll(
            listOf(
                NasKey(alias = "key-a"),
                NasKey(alias = "key-b")
            )
        )
        viewModel.tunnels.addAll(
            listOf(
                TunnelCardState(keyAlias = "key-a"),
                TunnelCardState(keyAlias = "deleted-key")
            )
        )

        assertEquals(1, viewModel.creatableTunnelCount)
        assertEquals(listOf("key-b"), viewModel.availableKeysForCreate.map { it.alias })
    }

    @Test
    @DisplayName("balance reconciliation subtracts only traffic not already reflected by NAS")
    fun balanceReconciliationKeepsOnlyUnaccountedLocalTraffic() {
        val viewModel = NeoLinkViewModel()
        val tunnelId = "tunnel-balance"
        viewModel.tunnels.add(TunnelCardState(id = tunnelId, keyAlias = "key-a", keyBalanceMiB = 100.0))
        viewModel.tunnelRuntime[tunnelId] = TunnelRuntimeUiState()

        invokePrivate(viewModel, "applyTrafficOnMain", tunnelId, 10L * 1024L * 1024L)
        assertEquals(10L * 1024L * 1024L, viewModel.tunnelRuntime[tunnelId]?.trafficSinceBalanceSyncBytes)

        viewModel.keys.clear()
        viewModel.keys.add(NasKey(alias = "key-a", balanceMiB = 95.0))
        invokePrivate(viewModel, "reconcileTunnelsWithKeys")

        assertEquals(95.0, viewModel.tunnels.single().keyBalanceMiB)
        assertEquals(5L * 1024L * 1024L, viewModel.tunnelRuntime[tunnelId]?.trafficSinceBalanceSyncBytes)

        viewModel.keys.clear()
        viewModel.keys.add(NasKey(alias = "key-a", balanceMiB = 90.0))
        invokePrivate(viewModel, "reconcileTunnelsWithKeys")

        assertEquals(90.0, viewModel.tunnels.single().keyBalanceMiB)
        assertEquals(0L, viewModel.tunnelRuntime[tunnelId]?.trafficSinceBalanceSyncBytes)
    }

    @Test
    @DisplayName("single instance guard rejects a second lock and releases cleanly")
    fun singleInstanceGuardRejectsSecondLockAndReleasesCleanly() {
        val lockFile = tempDir.resolve("neolink-desktop.lock").toFile()
        val first = NeoLinkSingleInstanceGuard.acquire(lockFile)
        assertNotNull(first)

        val second = NeoLinkSingleInstanceGuard.acquire(lockFile)
        assertNull(second)

        first?.close()
        val third = NeoLinkSingleInstanceGuard.acquire(lockFile)
        assertNotNull(third)
        third?.close()
    }

    @Test
    @DisplayName("toggleTunnelExpanded updates observable expansion state and persisted card state")
    fun toggleTunnelExpandedUpdatesObservableAndPersistedState() {
        val viewModel = NeoLinkViewModel()
        val tunnel = TunnelCardState(id = "tunnel-expand", expanded = true)
        viewModel.tunnels.add(tunnel)

        viewModel.toggleTunnelExpanded(tunnel.id)

        assertEquals(false, viewModel.tunnels.single().expanded)

        viewModel.toggleTunnelExpanded(tunnel.id)

        assertEquals(true, viewModel.tunnels.single().expanded)
    }

    @Test
    @DisplayName("initialize collapses persisted tunnels to avoid expensive first-resize rendering")
    fun initializeCollapsesPersistedTunnels() = runTest(mainDispatcher) {
        val runtimeDir = tempDir
        Files.createDirectories(runtimeDir)
        Files.writeString(
            runtimeDir.resolve("tunnels.json"),
            """
            {
              "tunnels" : [ {
                "id" : "persisted-expanded",
                "name" : "旧隧道",
                "keyAlias" : "key-a",
                "expanded" : true
              } ]
            }
            """.trimIndent()
        )

        val viewModel = NeoLinkViewModel()
        try {
            viewModel.initialize(emptyArray())
            runCurrent()

            assertEquals(1, viewModel.tunnels.size)
            assertEquals(false, viewModel.tunnels.single().expanded)
        } finally {
            viewModel.dispose()
        }
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
