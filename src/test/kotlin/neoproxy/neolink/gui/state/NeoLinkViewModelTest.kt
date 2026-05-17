package neoproxy.neolink.gui.state
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import neoproxy.neolink.config.ConfigOperator
import neoproxy.neolink.gui.app.NeoLinkSingleInstanceGuard
import neoproxy.neolink.gui.model.AuthMode
import neoproxy.neolink.gui.model.AuthUiState
import neoproxy.neolink.gui.model.MainPage
import neoproxy.neolink.gui.model.NasKey
import neoproxy.neolink.gui.model.NasDashboardState
import neoproxy.neolink.gui.model.PaymentDialogState
import neoproxy.neolink.gui.model.TunnelCardState
import neoproxy.neolink.gui.model.TunnelRuntimeUiState
import neoproxy.neolink.platform.DesktopLogManager
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
import top.ceroxe.api.print.log.LogType
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Duration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

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
        assertTrue(
            Regex("""\[\d{4}\.\d{2}\.\d{2} \d{2}:\d{2}:\d{2}]  \[INFO] \[UI] \n服务已停止。\n\n""")
                .matches(guiMessages[1]),
            guiMessages[1]
        )

        assertTrue(
            capturedMessages.contains("\n服务已停止。\n"),
            "LogSink 必须接收到和 GUI 中同源且不再重复标注 [System] 的消息文本"
        )
    }

    @Test
    @DisplayName("GUI startup banner logs ASCII logo before UI announcements")
    fun guiStartupBannerLogsAsciiLogoBeforeUiAnnouncements() = runTest(mainDispatcher) {
        val capturedMessages = mutableListOf<String>()
        val viewModel = NeoLinkViewModel()

        RuntimeState.setLogSink { _, tag, message -> capturedMessages.add("[$tag] $message") }
        invokePrivate(viewModel, "setupLogRedirector")

        invokePrivate(viewModel, "logUiStartupBanner")
        advanceUntilIdle()

        assertTrue(capturedMessages.first().contains("_____"), capturedMessages.first())
        assertTrue(capturedMessages.first().startsWith("[UI]"), capturedMessages.first())
        assertTrue(capturedMessages.drop(1).none { it.contains("[System]") })
    }

    @Test
    @DisplayName("sendCode starts the same cooldown behavior as NeoAuth web")
    fun sendCodeStartsCooldown() = runTest(mainDispatcher) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/send-code") { exchange ->
            json(exchange, """{"success":true,"msg":"验证码已发送"}""")
        }
        server.start()
        try {
            val viewModel = NeoLinkViewModel()
            setPrivateProperty(
                viewModel,
                "AuthState",
                AuthUiState(
                    mode = AuthMode.REGISTER,
                    nasUrl = "http://127.0.0.1:${server.address.port}",
                    email = "user@example.com"
                )
            )

            viewModel.sendCode()

            waitUntil(Duration.ofSeconds(2)) { viewModel.authState.codeCooldownSeconds in 1..60 }
            waitUntil(Duration.ofSeconds(2)) { viewModel.authState.message == "验证码已发送" }
            assertEquals("验证码已发送", viewModel.authState.message)
        } finally {
            server.stop(0)
        }
    }

    @Test
    @DisplayName("reset password follows NeoAuth ordinary-user web contract")
    fun resetPasswordFollowsNeoAuthUserContract() = runTest(mainDispatcher) {
        val requestBodies = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/send-code") { exchange ->
            requestBodies += "send:" + exchange.requestBody.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
            json(exchange, """{"success":true,"msg":"验证码已发送"}""")
        }
        server.createContext("/api/reset-password") { exchange ->
            requestBodies += "reset:" + exchange.requestBody.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
            json(exchange, """{"success":true}""")
        }
        server.start()
        try {
            val viewModel = NeoLinkViewModel()
            setPrivateProperty(
                viewModel,
                "AuthState",
                AuthUiState(
                    mode = AuthMode.RESET_PASSWORD,
                    nasUrl = "http://127.0.0.1:${server.address.port}",
                    email = "user@example.com",
                    code = "123456",
                    password = "StrongPwd123",
                    confirmPassword = "StrongPwd123"
                )
            )

            viewModel.sendCode()
            waitUntil(Duration.ofSeconds(2)) { requestBodies.any { it.startsWith("send:") } }
            viewModel.resetPassword()
            waitUntil(Duration.ofSeconds(2)) { viewModel.authState.message == "重置成功，请登录。" }

            assertTrue(Regex(""""mode"\s*:\s*"reset"""").containsMatchIn(requestBodies.single { it.startsWith("send:") }))
            assertTrue(Regex(""""password"\s*:\s*"StrongPwd123"""").containsMatchIn(requestBodies.single { it.startsWith("reset:") }))
            assertEquals(AuthMode.LOGIN, viewModel.authState.mode)
        } finally {
            server.stop(0)
        }
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
    @DisplayName("tunnel option changes append CMP system messages into tunnel log")
    fun tunnelOptionChangesAppendCmpSystemMessagesIntoTunnelLog() = runTest(mainDispatcher) {
        val viewModel = NeoLinkViewModel()
        val tunnelId = "tunnel-system-options"
        viewModel.tunnels.add(TunnelCardState(id = tunnelId, autoReconnect = true, debugMode = false, showConnection = true))
        viewModel.tunnelRuntime[tunnelId] = TunnelRuntimeUiState()

        viewModel.updateTunnelAutoReconnect(tunnelId, false)
        viewModel.updateTunnelDebug(tunnelId, true)
        viewModel.updateTunnelShowConnection(tunnelId, false)
        viewModel.updateTunnelPpv2(tunnelId, true)
        viewModel.updateTunnelTcp(tunnelId, false)
        advanceUntilIdle()

        val logText = viewModel.tunnelRuntime[tunnelId]?.logs.orEmpty().joinToString("\n") { it.text }
        assertTrue(logText.contains("[System] 自动重连已关闭。"))
        assertTrue(logText.contains("[System] 调试模式已开启。"))
        assertTrue(logText.contains("[System] 详细连接日志已关闭。"))
        assertTrue(logText.contains("[System] 真实 IP 透传已开启。"))
        assertTrue(logText.contains("[System] TCP 已关闭，UDP 已开启。"))
    }

    @Test
    @DisplayName("startTunnel validation failure appends CMP system message into tunnel log")
    fun startTunnelValidationFailureAppendsCmpSystemMessageIntoTunnelLog() = runTest(mainDispatcher) {
        val viewModel = NeoLinkViewModel()
        val tunnelId = "tunnel-invalid-start"
        viewModel.tunnels.add(TunnelCardState(id = tunnelId, keyAlias = "key-a", localPort = "", remoteDomain = "p.ceroxe.top"))
        viewModel.tunnelRuntime[tunnelId] = TunnelRuntimeUiState()

        val error = viewModel.startTunnel(tunnelId)
        advanceUntilIdle()

        assertEquals("本地端口必须在 1~65535 之间。", error)
        assertTrue(
            Regex(""".*\[INFO] \[HOST-CLIENT] \[System] 本地端口必须在 1~65535 之间。\n\n""", RegexOption.DOT_MATCHES_ALL)
                .matches(viewModel.tunnelRuntime[tunnelId]?.logs?.single()?.text.orEmpty()),
            viewModel.tunnelRuntime[tunnelId]?.logs?.single()?.text
        )
    }

    @Test
    @DisplayName("tunnel validation follows NeoLinkAPI protocol flags and allows both protocols disabled")
    fun tunnelValidationAllowsBothProtocolsDisabled() {
        val viewModel = NeoLinkViewModel()
        val tunnel = TunnelCardState(
            keyAlias = "key-a",
            localPort = "25565",
            remoteDomain = "p.ceroxe.top",
            localDomain = "localhost",
            hookPort = "44801",
            connectPort = "44802",
            tcpEnabled = false,
            udpEnabled = false
        )

        assertNull(validateTunnel(viewModel, tunnel))
    }

    @Test
    @DisplayName("NAS validation rejects malformed URLs and emails before network calls")
    fun nasValidationRejectsMalformedUrlsAndEmails() {
        val viewModel = NeoLinkViewModel()

        setPrivateProperty(
            viewModel,
            "AuthState",
            AuthUiState(nasUrl = "ftp://nas.example.com", email = "user@example.com")
        )
        assertEquals("NAS_URL 必须以 http:// 或 https:// 开头。", validateNasAndEmail(viewModel))

        setPrivateProperty(
            viewModel,
            "AuthState",
            AuthUiState(nasUrl = "http:///missing-host", email = "user@example.com")
        )
        assertEquals("NAS_URL 必须包含有效主机名。", validateNasAndEmail(viewModel))

        setPrivateProperty(
            viewModel,
            "AuthState",
            AuthUiState(nasUrl = "https://nas.example.com", email = "not-an-email")
        )
        assertEquals("邮箱格式无效。", validateNasAndEmail(viewModel))

        setPrivateProperty(
            viewModel,
            "AuthState",
            AuthUiState(nasUrl = "https://nas.example.com", email = "user@example.com")
        )
        assertNull(validateNasAndEmail(viewModel))
    }

    @Test
    @DisplayName("tunnel names must be valid log file names")
    fun tunnelNamesMustBeValidLogFileNames() {
        val viewModel = NeoLinkViewModel()
        val tunnel = TunnelCardState(
            name = "bad:name",
            keyAlias = "key-a",
            localPort = "25565",
            remoteDomain = "p.ceroxe.top",
            localDomain = "localhost",
            hookPort = "44801",
            connectPort = "44802"
        )

        assertEquals("隧道名称不能包含文件系统不支持的字符。", validateTunnel(viewModel, tunnel))
        assertTrue(DesktopLogManager.isValidTunnelLogFileName("生产隧道"))
        assertEquals(false, DesktopLogManager.isValidTunnelLogFileName("生产隧道 "))
        assertEquals(false, DesktopLogManager.isValidTunnelLogFileName("CON"))
    }

    @Test
    @DisplayName("updateTunnelName rejects names that cannot be used as log files")
    fun updateTunnelNameRejectsNamesThatCannotBeUsedAsLogFiles() {
        val viewModel = NeoLinkViewModel()
        val tunnel = TunnelCardState(id = "tunnel-name", name = "生产隧道")
        viewModel.tunnels.add(tunnel)

        viewModel.updateTunnelName(tunnel.id, "bad/name")

        assertEquals("生产隧道", viewModel.tunnels.single().name)
    }

    @Test
    @DisplayName("duplicate tunnel names are rejected because log files are name based")
    fun duplicateTunnelNamesAreRejectedBecauseLogFilesAreNameBased() {
        val viewModel = NeoLinkViewModel()
        viewModel.tunnels.add(TunnelCardState(id = "tunnel-a", name = "生产隧道"))
        viewModel.tunnels.add(
            TunnelCardState(
                id = "tunnel-b",
                name = "备用隧道",
                keyAlias = "key-a",
                localPort = "25565",
                remoteDomain = "p.ceroxe.top",
                localDomain = "localhost",
                hookPort = "44801",
                connectPort = "44802"
            )
        )

        viewModel.updateTunnelName("tunnel-b", "生产隧道")

        assertEquals("备用隧道", viewModel.tunnels[1].name)
        assertEquals("隧道名称不能重复。", validateTunnel(viewModel, viewModel.tunnels[1].copy(name = "生产隧道")))
    }

    @Test
    @DisplayName("key balance log updates remaining balance against the first observed full balance")
    fun keyBalanceLogUpdatesRemainingBalanceAgainstInitialBalance() = runTest(mainDispatcher) {
        val viewModel = NeoLinkViewModel()
        val tunnelId = "tunnel-key-balance-log"
        viewModel.tunnels.add(
            TunnelCardState(
                id = tunnelId,
                keyAlias = "key-a",
                keyBalanceMiB = 100.0,
                keyInitialBalanceMiB = 100.0
            )
        )
        viewModel.tunnelRuntime[tunnelId] = TunnelRuntimeUiState(trafficSinceBalanceSyncBytes = 5L * 1024L * 1024L)

        invokePrivate(viewModel, "appendTunnelLog", tunnelId, "这个密钥有 80.5 MB 流量可以消耗。", LogType.INFO)
        advanceUntilIdle()

        assertEquals(80.5, viewModel.tunnels.single().keyBalanceMiB)
        assertEquals(100.0, viewModel.tunnels.single().keyInitialBalanceMiB)
        assertEquals(0L, viewModel.tunnelRuntime[tunnelId]?.trafficSinceBalanceSyncBytes)
    }

    @Test
    @DisplayName("closing successful payment dialog moves user to key management")
    fun closingSuccessfulPaymentDialogMovesUserToKeyManagement() {
        val viewModel = NeoLinkViewModel()
        setPrivateProperty(
            viewModel,
            "NasState",
            NasDashboardState(
                paymentDialog = PaymentDialogState(
                    visible = true,
                    orderId = "order-paid",
                    status = "SUCCESS"
                )
            )
        )

        viewModel.closePaymentDialog()

        assertEquals(false, viewModel.nasState.paymentDialog.visible)
        assertEquals(MainPage.KEY_MANAGEMENT, viewModel.uiState.currentPage)
    }

    @Test
    @DisplayName("pending payment dialog cannot be closed before timeout or success")
    fun pendingPaymentDialogCannotBeClosedBeforeTerminalState() {
        val viewModel = NeoLinkViewModel()
        setPrivateProperty(
            viewModel,
            "NasState",
            NasDashboardState(
                paymentDialog = PaymentDialogState(
                    visible = true,
                    orderId = "order-pending",
                    status = "PENDING",
                    timedOut = false
                )
            )
        )

        viewModel.closePaymentDialog()

        assertEquals(true, viewModel.nasState.paymentDialog.visible)
        assertEquals("order-pending", viewModel.nasState.paymentDialog.orderId)
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
        assertEquals(100.0, viewModel.tunnels.single().keyInitialBalanceMiB)
        assertEquals(5L * 1024L * 1024L, viewModel.tunnelRuntime[tunnelId]?.trafficSinceBalanceSyncBytes)

        viewModel.keys.clear()
        viewModel.keys.add(NasKey(alias = "key-a", balanceMiB = 90.0))
        invokePrivate(viewModel, "reconcileTunnelsWithKeys")

        assertEquals(90.0, viewModel.tunnels.single().keyBalanceMiB)
        assertEquals(100.0, viewModel.tunnels.single().keyInitialBalanceMiB)
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
        val runtimeDir = tempDir.resolve("state")
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

    @Test
    @DisplayName("acknowledged announcements are not shown again during dashboard refresh")
    fun acknowledgedAnnouncementsAreNotShownAgainDuringDashboardRefresh() = runTest(mainDispatcher) {
        val readBodies = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/config") { exchange ->
            json(exchange, """{"success":true,"data":{}}""")
        }
        server.createContext("/api/my-announcements") { exchange ->
            json(
                exchange,
                """{"success":true,"data":[{"id":7,"title":"维护公告","content":"今晚维护","content_type":"text","allow_dismiss":true}]}"""
            )
        }
        server.createContext("/api/announcement/read") { exchange ->
            readBodies += exchange.requestBody.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
            json(exchange, """{"success":true}""")
        }
        server.start()
        try {
            val viewModel = NeoLinkViewModel()
            setPrivateProperty(
                viewModel,
                "AuthState",
                AuthUiState(
                    nasUrl = "http://127.0.0.1:${server.address.port}",
                    sessionToken = "session-token",
                    isAuthenticated = true,
                    isVerified = true
                )
            )

            viewModel.refreshNasDashboard()
            waitUntil(Duration.ofSeconds(2)) { viewModel.nasState.announcements.map { it.id } == listOf(7) }

            assertEquals(listOf(7), viewModel.nasState.announcements.map { it.id })
            assertTrue(viewModel.nasState.announcementDialogVisible)

            viewModel.closeCurrentAnnouncement(dismissed = false)
            advanceUntilIdle()
            viewModel.refreshNasDashboard()
            advanceUntilIdle()

            assertTrue(viewModel.nasState.announcements.isEmpty())
            assertEquals(false, viewModel.nasState.announcementDialogVisible)
            waitUntil(Duration.ofSeconds(2)) { readBodies.isNotEmpty() }
            assertTrue(Regex(""""dismissed"\s*:\s*false""").containsMatchIn(readBodies.single()), readBodies.single())
        } finally {
            server.stop(0)
        }
    }

    @Test
    @DisplayName("NAS announcements are hidden until identity is verified")
    fun nasAnnouncementsAreHiddenUntilIdentityIsVerified() = runTest(mainDispatcher) {
        val announcementRequests = CopyOnWriteArrayList<String>()
        val configRequests = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/config") { exchange ->
            configRequests += exchange.requestURI.path
            json(exchange, """{"success":true,"data":{"price_traffic":0.07}}""")
        }
        server.createContext("/api/my-announcements") { exchange ->
            announcementRequests += exchange.requestURI.path
            json(exchange, """{"success":true,"data":[{"id":7,"title":"维护公告","content":"今晚维护","content_type":"text","allow_dismiss":true}]}""")
        }
        server.start()
        try {
            val viewModel = NeoLinkViewModel()
            setPrivateProperty(
                viewModel,
                "AuthState",
                AuthUiState(
                    nasUrl = "http://127.0.0.1:${server.address.port}",
                    sessionToken = "session-token",
                    isAuthenticated = true,
                    isVerified = false
                )
            )

            viewModel.refreshNasDashboard()

            waitUntil(Duration.ofSeconds(2)) { configRequests.isNotEmpty() }
            assertTrue(announcementRequests.isEmpty())
            assertTrue(viewModel.nasState.announcements.isEmpty())
            assertEquals(false, viewModel.nasState.announcementDialogVisible)
        } finally {
            server.stop(0)
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

    private fun setPrivateProperty(target: Any, propertyName: String, value: Any) {
        val setter = target.javaClass.getDeclaredMethod("set$propertyName", value.javaClass)
        setter.isAccessible = true
        setter.invoke(target, value)
    }

    private fun json(exchange: com.sun.net.httpserver.HttpExchange, body: String) {
        val response = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(200, response.size.toLong())
        exchange.responseBody.use { it.write(response) }
    }

    private fun validateTunnel(viewModel: NeoLinkViewModel, tunnel: TunnelCardState): String? {
        val method = viewModel.javaClass.getDeclaredMethod("validateTunnel", TunnelCardState::class.java)
        method.isAccessible = true
        return method.invoke(viewModel, tunnel) as String?
    }

    private fun validateNasAndEmail(viewModel: NeoLinkViewModel): String? {
        val method = viewModel.javaClass.getDeclaredMethod("validateNasAndEmail")
        method.isAccessible = true
        return method.invoke(viewModel) as String?
    }

    private fun waitUntil(timeout: Duration, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10)
        }
        check(condition())
    }
}
