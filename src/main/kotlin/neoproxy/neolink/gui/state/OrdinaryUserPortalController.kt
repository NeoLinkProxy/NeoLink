package neoproxy.neolink.gui.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neoproxy.neolink.gui.config.DEFAULT_NAS_URL
import neoproxy.neolink.gui.data.NasAccountLockedException
import neoproxy.neolink.gui.data.NasClient
import neoproxy.neolink.gui.data.NasSessionExpiredException
import neoproxy.neolink.gui.data.NeoLinkLocalStore
import neoproxy.neolink.gui.model.AuthMode
import neoproxy.neolink.gui.model.AuthUiState
import neoproxy.neolink.gui.model.IdentityStatus
import neoproxy.neolink.gui.model.MainPage
import neoproxy.neolink.gui.model.NasDashboardState
import neoproxy.neolink.gui.model.NasKey
import neoproxy.neolink.gui.model.PaymentDialogState
import neoproxy.neolink.gui.model.RefreshKeyDialogState
import neoproxy.neolink.gui.model.SessionStoreDocument
import neoproxy.neolink.gui.model.UiState
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the ordinary-user NeoAuth website replacement flow.
 *
 * The desktop ViewModel should stay focused on observable UI state and tunnel runtime wiring.
 * User-account behavior lives here so login, identity verification, key refresh, payment, and
 * announcements evolve as one cohesive contract without pulling admin-console concerns into the app.
 */
internal class OrdinaryUserPortalController(
    private val scope: CoroutineScope,
    private val workflow: NasUserWorkflow,
    private val host: Host,
    private val paymentCountdownSeconds: Int,
    private val codeCooldownSeconds: Int
) {
    interface Host {
        fun authState(): AuthUiState
        fun setAuthState(value: AuthUiState)
        fun nasState(): NasDashboardState
        fun setNasState(value: NasDashboardState)
        fun uiState(): UiState
        fun setUiState(value: UiState)
        fun replaceKeys(keys: List<NasKey>)
        fun clearKeys()
        fun reconcileTunnelsWithKeys()
        fun stopAllTunnels()
        fun appendSystemLog(message: String, surroundWithBlankLines: Boolean = false)
    }

    private var codeCooldownJob: Job? = null
    private val acknowledgedAnnouncementIds = ConcurrentHashMap.newKeySet<Int>()
    private val paymentTracker = PaymentTracker(
        scope = scope,
        totalSeconds = paymentCountdownSeconds,
        isStillCurrent = { orderId ->
            host.nasState().paymentDialog.let { it.visible && it.orderId == orderId }
        },
        isSuccessful = { orderId ->
            host.nasState().paymentDialog.let { it.orderId == orderId && it.status == "SUCCESS" }
        },
        updateCountdown = ::updatePaymentCountdown,
        pollStatus = { orderId, requestContext ->
            pollPaymentStatus(orderId, requestContext as? AuthUiState ?: host.authState())
        },
        onSuccess = ::handlePaymentSuccess
    )

    fun dispose() {
        codeCooldownJob?.cancel()
        paymentTracker.cancel()
    }

    fun clearAcknowledgedAnnouncements() {
        acknowledgedAnnouncementIds.clear()
    }

    fun sendCode() {
        val current = host.authState()
        if (current.codeCooldownSeconds > 0) {
            return
        }
        val error = workflow.validateNasAndEmail(current)
        if (error != null) {
            host.setAuthState(current.copy(message = error))
            return
        }

        val loadingMessage = "正在发送验证码..."
        startCodeCooldown(codeCooldownSeconds)
        host.setAuthState(host.authState().copy(isLoading = true, isRestoringSession = false, message = loadingMessage))
        val requestState = host.authState()
        scope.launch(Dispatchers.IO) {
            var terminalMessage: String? = null
            try {
                val response = client(requestState).sendCode(requestState.email, workflow.codeMode(requestState.mode))
                terminalMessage = response.message
                if (!response.success && response.timeLeftSeconds <= 0) {
                    stopCodeCooldown()
                } else if (!response.success && response.timeLeftSeconds > 0) {
                    startCodeCooldown(response.timeLeftSeconds)
                }
                updateAuthStateOnMain { current ->
                    if (current.message == loadingMessage) current.copy(message = response.message) else current
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stopCodeCooldown()
                terminalMessage = e.message ?: e.javaClass.simpleName
                updateAuthStateOnMain { current ->
                    if (current.message == loadingMessage) current.copy(message = e.message ?: e.javaClass.simpleName) else current
                }
            } finally {
                updateAuthStateOnMain { current ->
                    if (current.message == loadingMessage || current.message == terminalMessage) {
                        current.copy(isLoading = false)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun login() {
        val error = workflow.validateLogin(host.authState())
        if (error != null) {
            host.setAuthState(host.authState().copy(message = error))
            return
        }
        runAuth("正在登录...") { state ->
            val response = client(state).login(state.email, state.password)
            if (!response.success) {
                if (response.isAccountLocked) {
                    showAccountLockedOnMain(state.nasUrl, state.email, response.message)
                    return@runAuth
                }
                updateAuthStateOnMain { it.copy(message = response.message) }
                return@runAuth
            }
            persistSession(state.nasUrl, state.email, response.sessionToken)
            checkIdentityAndRefreshKeys(state.nasUrl, response.sessionToken)
            refreshDashboard(response.sessionToken)
        }
    }

    fun register() {
        val error = workflow.validateRegister(host.authState())
        if (error != null) {
            host.setAuthState(host.authState().copy(message = error))
            return
        }
        runAuth("正在注册...") { state ->
            val response = client(state).register(state.email, state.password, state.code)
            if (!response.success) {
                updateAuthStateOnMain { it.copy(message = response.message) }
                return@runAuth
            }
            persistSession(state.nasUrl, state.email, response.sessionToken)
            refreshDashboard(response.sessionToken)
            updateAuthStateOnMain {
                it.copy(
                    mode = AuthMode.VERIFY_IDENTITY,
                    isAuthenticated = true,
                    isVerified = false,
                    message = "注册成功，请完成实名认证。"
                )
            }
        }
    }

    fun resetPassword() {
        val error = workflow.validateResetPassword(host.authState())
        if (error != null) {
            host.setAuthState(host.authState().copy(message = error))
            return
        }
        runAuth("正在重置密码...") { state ->
            val response = client(state).resetPassword(state.email, state.code, state.password)
            if (!response.success) {
                updateAuthStateOnMain { it.copy(message = response.message) }
                return@runAuth
            }
            updateAuthStateOnMain {
                it.copy(
                    mode = AuthMode.LOGIN,
                    password = "",
                    confirmPassword = "",
                    code = "",
                    message = "重置成功，请登录。"
                )
            }
        }
    }

    fun verifyIdentity() {
        val current = host.authState()
        val error = workflow.validateIdentity(current.realName, current.idCard)
        if (error != null) {
            host.setAuthState(current.copy(message = error))
            return
        }
        runAuth("正在实名认证...") { state ->
            val response = client(state).verifyIdentity(state.realName, state.idCard)
            if (!response.success) {
                updateAuthStateOnMain { it.copy(message = response.message) }
                return@runAuth
            }
            updateAuthStateOnMain { it.copy(isVerified = true, mode = AuthMode.LOGIN, message = "认证通过。") }
            checkIdentityAndRefreshKeys(state.nasUrl, state.sessionToken)
            refreshDashboard(state.sessionToken)
        }
    }

    fun logout() {
        val requestState = host.authState()
        if (requestState.sessionToken.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                runCatching { client(requestState).logout() }
            }
        }
        host.stopAllTunnels()
        NeoLinkLocalStore.clearSession()
        acknowledgedAnnouncementIds.clear()
        host.clearKeys()
        host.setNasState(NasDashboardState())
        host.setAuthState(AuthUiState(nasUrl = requestState.nasUrl.ifBlank { DEFAULT_NAS_URL }))
        host.appendSystemLog("已退出登录。")
    }

    fun refreshSessionAndKeys() {
        val requestState = host.authState()
        host.setAuthState(requestState.copy(isRestoringSession = true, isLoading = false, message = ""))
        scope.launch(Dispatchers.IO) {
            try {
                client(requestState).heartbeat()
                checkIdentityAndRefreshKeys(requestState.nasUrl, requestState.sessionToken)
                refreshDashboard(requestState.sessionToken)
            } catch (e: NasAccountLockedException) {
                showAccountLockedOnMain(requestState.nasUrl, requestState.email, e.message ?: "账号已被封禁，请联系管理员。")
            } catch (e: NasSessionExpiredException) {
                expireSessionOnMain(requestState.nasUrl, requestState.email, e.message ?: "会话已失效，请重新登录。")
            } catch (e: Exception) {
                expireSessionOnMain(requestState.nasUrl, requestState.email, "会话已失效，请重新登录。")
            } finally {
                updateAuthStateOnMain { it.copy(isRestoringSession = false) }
            }
        }
    }

    fun refreshKeys() {
        val requestState = host.authState()
        if (!requestState.isAuthenticated || !requestState.isVerified) return
        scope.launch(Dispatchers.IO) {
            try {
                val loaded = loadKeysWithAvailableNkmNodes(client(requestState))
                withContext(Dispatchers.Main) {
                    host.replaceKeys(loaded)
                    host.reconcileTunnelsWithKeys()
                    host.appendSystemLog("密钥列表已刷新，共 ${loaded.size} 个。")
                }
            } catch (e: NasAccountLockedException) {
                showAccountLockedOnMain(requestState.nasUrl, requestState.email, e.message ?: "账号已被封禁，请联系管理员。")
            } catch (e: NasSessionExpiredException) {
                expireSessionOnMain(requestState.nasUrl, requestState.email, e.message ?: "会话已失效，请重新登录。")
            } catch (e: Exception) {
                host.appendSystemLog("密钥列表刷新失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun refreshDashboard(sessionToken: String = host.authState().sessionToken) {
        if (!host.authState().isAuthenticated) return
        val requestState = host.authState().copy(sessionToken = sessionToken.ifBlank { host.authState().sessionToken })
        scope.launch(Dispatchers.IO) {
            try {
                val portalClient = client(requestState)
                val pricing = runCatchingPreservingSessionBoundary { portalClient.config() }
                    .getOrDefault(host.nasState().pricing)
                if (!requestState.isVerified) {
                    updateNasStateOnMain {
                        it.copy(
                            pricing = pricing,
                            announcements = emptyList(),
                            announcementIndex = 0,
                            announcementDialogVisible = false,
                            message = ""
                        )
                    }
                    return@launch
                }
                val announcements = runCatchingPreservingSessionBoundary { portalClient.myAnnouncements() }
                    .getOrDefault(emptyList())
                    .filterNot { it.id in acknowledgedAnnouncementIds }
                updateNasStateOnMain {
                    it.copy(
                        pricing = pricing,
                        announcements = announcements,
                        announcementIndex = 0,
                        announcementDialogVisible = announcements.isNotEmpty(),
                        message = ""
                    )
                }
            } catch (e: NasAccountLockedException) {
                showAccountLockedOnMain(requestState.nasUrl, requestState.email, e.message ?: "账号已被封禁，请联系管理员。")
            } catch (e: NasSessionExpiredException) {
                expireSessionOnMain(requestState.nasUrl, requestState.email, e.message ?: "会话已失效，请重新登录。")
            } catch (e: Exception) {
                updateNasStateOnMain { it.copy(message = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun submitRefreshKey() {
        val dialog = host.nasState().refreshKeyDialog
        if (!dialog.visible || dialog.keyName.isBlank() || dialog.loading) return
        val requestState = host.authState()
        host.setNasState(host.nasState().copy(refreshKeyDialog = dialog.copy(loading = true), message = "正在重置序列号..."))
        scope.launch(Dispatchers.IO) {
            try {
                val response = client(requestState).refreshKey(dialog.keyName)
                if (!response.success) {
                    updateNasStateOnMain {
                        it.copy(refreshKeyDialog = it.refreshKeyDialog.copy(loading = false), message = response.message)
                    }
                    return@launch
                }
                refreshKeys()
                updateNasStateOnMain { it.copy(refreshKeyDialog = RefreshKeyDialogState(), message = "刷新成功，旧设备已踢出。") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateNasStateOnMain {
                    it.copy(refreshKeyDialog = it.refreshKeyDialog.copy(loading = false), message = e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    fun createPurchaseOrder() {
        val state = host.nasState()
        val error = workflow.validatePurchaseDraft(host.authState().isVerified, state.pricing, state.purchaseDraft, purchaseAmount())
        if (error != null) {
            host.setNasState(state.copy(message = error))
            return
        }
        val draft = state.purchaseDraft
        createOrder(
            trafficGiB = draft.trafficGiB.toDoubleOrNull() ?: 0.0,
            days = draft.days.toIntOrNull() ?: 0,
            rateMbps = draft.rateMbps.toIntOrNull() ?: 10,
            targetKey = ""
        )
    }

    fun createRechargeOrder() {
        val state = host.nasState()
        val error = workflow.validateRechargeDraft(host.authState().isVerified, state.pricing, state.rechargeDraft, rechargeAmount())
        if (error != null) {
            host.setNasState(state.copy(message = error))
            return
        }
        val draft = state.rechargeDraft
        createOrder(
            trafficGiB = draft.trafficGiB.toDoubleOrNull() ?: 0.0,
            days = draft.days.toIntOrNull() ?: 0,
            rateMbps = 10,
            targetKey = draft.targetKey
        )
    }

    fun closePaymentDialog() {
        val paymentDialog = host.nasState().paymentDialog
        if (paymentDialog.status != "SUCCESS" && !paymentDialog.timedOut) {
            return
        }
        paymentTracker.cancel()
        host.setNasState(host.nasState().copy(paymentDialog = PaymentDialogState()))
        if (paymentDialog.status == "SUCCESS") {
            host.setUiState(host.uiState().copy(currentPage = MainPage.KEY_MANAGEMENT))
        }
    }

    fun closeCurrentAnnouncement(dismissed: Boolean) {
        val state = host.nasState()
        val announcement = state.announcements.getOrNull(state.announcementIndex) ?: return
        val requestState = host.authState()
        acknowledgedAnnouncementIds += announcement.id
        scope.launch(Dispatchers.IO) {
            runCatching { client(requestState).markAnnouncementRead(announcement.id, dismissed && announcement.allowDismiss) }
        }
        val nextIndex = state.announcementIndex + 1
        host.setNasState(
            if (nextIndex < state.announcements.size) {
                state.copy(announcementIndex = nextIndex, announcementDialogVisible = true)
            } else {
                state.copy(announcementDialogVisible = false, announcementIndex = 0, announcements = emptyList())
            }
        )
    }

    fun validateNasAndEmail(): String? = workflow.validateNasAndEmail(host.authState())

    fun validateHttpUrl(name: String, value: String): String? = workflow.validateHttpUrl(name, value)

    fun purchaseAmount(): Double {
        val state = host.nasState()
        val draft = state.purchaseDraft
        val pricing = state.pricing
        val traffic = draft.trafficGiB.toDoubleOrNull() ?: 0.0
        val days = draft.days.toIntOrNull() ?: 0
        val rate = draft.rateMbps.toIntOrNull() ?: 0
        return roundCurrency(traffic * pricing.priceTraffic + days * pricing.priceDay + rate * pricing.priceRateUnit)
    }

    fun rechargeAmount(): Double {
        val state = host.nasState()
        val draft = state.rechargeDraft
        val pricing = state.pricing
        val traffic = draft.trafficGiB.toDoubleOrNull() ?: 0.0
        val days = draft.days.toIntOrNull() ?: 0
        return roundCurrency(traffic * pricing.priceTraffic + days * pricing.priceDay)
    }

    private fun createOrder(trafficGiB: Double, days: Int, rateMbps: Int, targetKey: String) {
        if (!host.authState().isVerified) {
            host.setNasState(host.nasState().copy(message = "请先完成实名认证。"))
            return
        }
        paymentTracker.cancel()
        val requestState = host.authState()
        host.setNasState(
            host.nasState().copy(
                rechargeDialogVisible = false,
                paymentDialog = PaymentDialogState(visible = true, loading = true, message = "正在创建订单..."),
                message = ""
            )
        )
        scope.launch(Dispatchers.IO) {
            try {
                val order = client(requestState).createOrder(trafficGiB, days, rateMbps, targetKey)
                updateNasStateOnMain {
                    it.copy(
                        paymentDialog = PaymentDialogState(
                            visible = true,
                            orderId = order.orderId,
                            amount = order.amount,
                            status = "PENDING",
                            secondsLeft = paymentCountdownSeconds,
                            message = "请在 120 秒内完成支付。",
                            timedOut = false,
                            loading = false
                        )
                    )
                }
                paymentTracker.start(order.orderId, requestState)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NasAccountLockedException) {
                showAccountLockedOnMain(requestState.nasUrl, requestState.email, e.message ?: "账号已被封禁，请联系管理员。")
            } catch (e: NasSessionExpiredException) {
                expireSessionOnMain(requestState.nasUrl, requestState.email, e.message ?: "会话已失效，请重新登录。")
            } catch (e: Exception) {
                updateNasStateOnMain {
                    it.copy(paymentDialog = PaymentDialogState(), message = e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    private suspend fun updatePaymentCountdown(orderId: String, secondsLeft: Int) {
        updateNasStateOnMain {
            if (!it.paymentDialog.visible || it.paymentDialog.orderId != orderId || it.paymentDialog.status == "SUCCESS") {
                it
            } else {
                it.copy(
                    paymentDialog = it.paymentDialog.copy(
                        secondsLeft = secondsLeft,
                        timedOut = secondsLeft <= 0,
                        message = if (secondsLeft <= 0) {
                            "订单已超时，二维码已失效，可关闭后重新创建订单。"
                        } else {
                            "请在 120 秒内完成支付。"
                        }
                    )
                )
            }
        }
    }

    private fun pollPaymentStatus(orderId: String, state: AuthUiState): Boolean {
        return runCatching { client(state).payStatus(orderId) }.getOrDefault("PENDING") == "SUCCESS"
    }

    private suspend fun handlePaymentSuccess(orderId: String, secondsLeft: Int) {
        updateNasStateOnMain {
            if (!it.paymentDialog.visible || it.paymentDialog.orderId != orderId) {
                it
            } else {
                it.copy(
                    paymentDialog = it.paymentDialog.copy(
                        status = "SUCCESS",
                        secondsLeft = secondsLeft,
                        timedOut = false,
                        message = "支付成功，正在刷新密钥列表。"
                    )
                )
            }
        }
        refreshKeys()
    }

    private fun runAuth(loadingMessage: String, action: suspend (AuthUiState) -> Unit) {
        host.setAuthState(host.authState().copy(isLoading = true, isRestoringSession = false, message = loadingMessage))
        val requestState = host.authState()
        scope.launch(Dispatchers.IO) {
            try {
                action(requestState)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NasAccountLockedException) {
                showAccountLockedOnMain(requestState.nasUrl, requestState.email, e.message ?: "账号已被封禁，请联系管理员。")
            } catch (e: NasSessionExpiredException) {
                expireSessionOnMain(requestState.nasUrl, requestState.email, e.message ?: "会话已失效，请重新登录。")
            } catch (e: Exception) {
                updateAuthStateOnMain { it.copy(message = e.message ?: e.javaClass.simpleName) }
            } finally {
                updateAuthStateOnMain { it.copy(isLoading = false) }
            }
        }
    }

    private inline fun <T> runCatchingPreservingSessionBoundary(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: NasAccountLockedException) {
            throw e
        } catch (e: NasSessionExpiredException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private suspend fun checkIdentityAndRefreshKeys(nasUrl: String, sessionToken: String) {
        val portalClient = workflow.client(AuthUiState(nasUrl = nasUrl, sessionToken = sessionToken))
        val status = runCatching { portalClient.identityStatus() }
            .getOrElse { error -> if (isAccountLockedError(error)) "LOCKED" else throw error }
        val identityStatus = workflow.resolveIdentityStatus(status)
        updateNasStateOnMain { it.copy(identityStatus = identityStatus) }
        if (identityStatus == IdentityStatus.VERIFIED) {
            val loaded = loadKeysWithAvailableNkmNodes(portalClient)
            withContext(Dispatchers.Main) {
                host.setAuthState(
                    host.authState().copy(
                        nasUrl = nasUrl,
                        sessionToken = sessionToken,
                        isAuthenticated = true,
                        isVerified = true,
                        isAccountLocked = false,
                        mode = AuthMode.LOGIN,
                        message = "已登录。"
                    )
                )
                host.replaceKeys(loaded)
                host.reconcileTunnelsWithKeys()
            }
        } else {
            updateAuthStateOnMain {
                it.copy(
                    nasUrl = nasUrl,
                    sessionToken = sessionToken,
                    isAuthenticated = true,
                    isVerified = false,
                    isAccountLocked = identityStatus == IdentityStatus.LOCKED,
                    mode = AuthMode.LOGIN,
                    message = if (identityStatus == IdentityStatus.LOCKED) "账号已被封禁，请联系管理员。" else "请完成实名认证。"
                )
            }
        }
    }

    private suspend fun persistSession(nasUrl: String, email: String, sessionToken: String) {
        val normalizedNasUrl = nasUrl.trim().ifBlank { DEFAULT_NAS_URL }
        NeoLinkLocalStore.saveNasUrlToConfig(normalizedNasUrl)
        NeoLinkLocalStore.saveSession(SessionStoreDocument(normalizedNasUrl, email, sessionToken))
        updateAuthStateOnMain {
            it.copy(
                nasUrl = normalizedNasUrl,
                email = email,
                sessionToken = sessionToken,
                isAuthenticated = sessionToken.isNotBlank(),
                isAccountLocked = false
            )
        }
    }

    private suspend fun expireSessionOnMain(nasUrl: String, email: String, message: String) {
        withContext(Dispatchers.Main) {
            clearAuthenticatedSession(nasUrl, email, message, accountLocked = false)
            host.appendSystemLog(message)
        }
    }

    private suspend fun showAccountLockedOnMain(nasUrl: String, email: String, message: String) {
        withContext(Dispatchers.Main) {
            clearAuthenticatedSession(nasUrl, email, message, accountLocked = true)
            host.appendSystemLog(message)
        }
    }

    private fun clearAuthenticatedSession(nasUrl: String, email: String, message: String, accountLocked: Boolean) {
        host.stopAllTunnels()
        NeoLinkLocalStore.clearSession()
        acknowledgedAnnouncementIds.clear()
        host.clearKeys()
        host.setNasState(NasDashboardState(identityStatus = if (accountLocked) IdentityStatus.LOCKED else IdentityStatus.UNKNOWN))
        host.setAuthState(
            AuthUiState(
                nasUrl = nasUrl.ifBlank { host.authState().nasUrl.ifBlank { DEFAULT_NAS_URL } },
                email = email,
                isAccountLocked = accountLocked,
                message = message
            )
        )
    }

    private fun loadKeysWithAvailableNkmNodes(portalClient: NasClient): List<NasKey> {
        val result = workflow.loadKeysWithAvailableNkmNodes(portalClient, NeoLinkLocalStore.loadNkmNodeListUrlFromConfig())
        result.warning?.let { host.appendSystemLog(it) }
        result.refreshedNodeCount?.let { count ->
            host.appendSystemLog("NKM 可用节点列表已刷新并缓存，共 $count 个。")
        }
        return result.keys
    }

    private fun startCodeCooldown(seconds: Int) {
        val boundedSeconds = seconds.coerceIn(1, codeCooldownSeconds)
        codeCooldownJob?.cancel()
        codeCooldownJob = scope.launch(Dispatchers.Main) {
            host.setAuthState(host.authState().copy(codeCooldownSeconds = boundedSeconds))
            var remaining = boundedSeconds
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining--
                host.setAuthState(host.authState().copy(codeCooldownSeconds = remaining))
            }
        }
    }

    private fun stopCodeCooldown() {
        codeCooldownJob?.cancel()
        codeCooldownJob = null
        scope.launch(Dispatchers.Main) {
            host.setAuthState(host.authState().copy(codeCooldownSeconds = 0))
        }
    }

    private suspend fun updateAuthStateOnMain(update: (AuthUiState) -> AuthUiState) {
        withContext(Dispatchers.Main) {
            host.setAuthState(update(host.authState()))
        }
    }

    private suspend fun updateNasStateOnMain(update: (NasDashboardState) -> NasDashboardState) {
        withContext(Dispatchers.Main) {
            host.setNasState(update(host.nasState()))
        }
    }

    private fun client(state: AuthUiState): NasClient = workflow.client(state)

    private fun isAccountLockedError(error: Throwable): Boolean {
        return error is NasAccountLockedException ||
            (error.message ?: "").let { it.contains("封禁") || it.contains("锁定") }
    }

    private fun roundCurrency(value: Double): Double {
        return kotlin.math.round(value * 100.0) / 100.0
    }
}
