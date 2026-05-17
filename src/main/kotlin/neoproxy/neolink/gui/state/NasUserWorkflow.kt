package neoproxy.neolink.gui.state

import neoproxy.neolink.gui.config.DEFAULT_NAS_URL
import neoproxy.neolink.gui.config.DEFAULT_NKM_NODELIST_URL
import neoproxy.neolink.gui.data.NasClient
import neoproxy.neolink.gui.data.NkmNodeClient
import neoproxy.neolink.gui.data.NkmNodeSource
import neoproxy.neolink.gui.model.AuthMode
import neoproxy.neolink.gui.model.AuthUiState
import neoproxy.neolink.gui.model.IdentityStatus
import neoproxy.neolink.gui.model.NasKey
import neoproxy.neolink.gui.model.NasPricingConfig
import neoproxy.neolink.gui.model.NkmNode
import neoproxy.neolink.gui.model.PurchaseDraft
import neoproxy.neolink.gui.model.RechargeDraft
import java.net.URI
import java.util.Locale

/**
 * Keeps the desktop client aligned with the ordinary-user NeoAuth API contract.
 *
 * This class deliberately stays inside ordinary-account behavior.  The desktop app is a front-stage
 * replacement for website user flows, so its boundary is validation, session-backed NAS calls,
 * purchasable limits, and the authorized-node intersection required before starting tunnels.
 */
class NasUserWorkflow {
    companion object {
        const val CODE_COOLDOWN_SECONDS = 60
        private val EmailPattern: Regex = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
    }

    fun client(state: AuthUiState): NasClient = NasClient(state.nasUrl, state.sessionToken)

    fun codeMode(mode: AuthMode): String {
        return when (mode) {
            AuthMode.REGISTER -> "reg"
            AuthMode.RESET_PASSWORD -> "reset"
            AuthMode.LOGIN -> "login"
            AuthMode.VERIFY_IDENTITY -> "login"
        }
    }

    fun validateNasAndEmail(state: AuthUiState): String? {
        validateHttpUrl("NAS_URL", state.nasUrl.trim())?.let { return it }
        if (!EmailPattern.matches(state.email.trim())) return "邮箱格式无效。"
        return null
    }

    fun validateLogin(state: AuthUiState): String? {
        return validateNasAndEmail(state)
            ?: if (state.password.isBlank()) "密码不能为空。" else null
    }

    fun validateRegister(state: AuthUiState): String? {
        validateNasAndEmail(state)?.let { return it }
        validatePasswordForNeoAuth(state.password)?.let { return it }
        if (state.password != state.confirmPassword) return "两次输入的密码不一致。"
        return validateSixDigitCode(state.code)
    }

    fun validateResetPassword(state: AuthUiState): String? {
        validateNasAndEmail(state)?.let { return it }
        validateSixDigitCode(state.code)?.let { return it }
        validatePasswordForNeoAuth(state.password)?.let { return it }
        if (state.password != state.confirmPassword) return "两次输入的密码不一致。"
        return null
    }

    fun validateIdentity(realName: String, idCard: String): String? {
        return if (realName.isBlank()) "姓名不能为空。"
        else if (idCard.isBlank()) "身份证号不能为空。"
        else null
    }

    fun validateHttpUrl(name: String, value: String): String? {
        if (value.isBlank()) return "$name 不能为空。"
        val uri = runCatching { URI(value) }.getOrNull()
            ?: return "$name 格式无效。"
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return "$name 必须以 http:// 或 https:// 开头。"
        }
        if (uri.host.isNullOrBlank()) {
            return "$name 必须包含有效主机名。"
        }
        return null
    }

    fun normalizeNasUrl(value: String): String = value.trim().ifBlank { DEFAULT_NAS_URL }

    fun normalizeNkmNodeListUrl(value: String): String = value.trim().ifBlank { DEFAULT_NKM_NODELIST_URL }

    fun resolveIdentityStatus(rawStatus: String): IdentityStatus {
        return when (rawStatus) {
            "VERIFIED" -> IdentityStatus.VERIFIED
            "LOCKED" -> IdentityStatus.LOCKED
            else -> IdentityStatus.UNVERIFIED
        }
    }

    fun loadKeysWithAvailableNkmNodes(client: NasClient, nodeListUrl: String): KeyLoadResult {
        val loadedKeys = client.myKeys()
        val nkmNodeLoadResult = NkmNodeClient.loadOnlineNodes(normalizeNkmNodeListUrl(nodeListUrl))
        return KeyLoadResult(
            keys = intersectNasAuthorizedNodesWithNkmAvailability(loadedKeys, nkmNodeLoadResult.nodes),
            warning = nkmNodeLoadResult.warning,
            refreshedNodeCount = nkmNodeLoadResult.nodes.size.takeIf { nkmNodeLoadResult.source == NkmNodeSource.NETWORK }
        )
    }

    fun validatePurchaseDraft(isVerified: Boolean, pricing: NasPricingConfig, draft: PurchaseDraft, amount: Double): String? {
        if (!isVerified) return "请先完成实名认证。"
        val traffic = draft.trafficGiB.toDoubleOrNull() ?: return "流量必须是数字。"
        val days = draft.days.toIntOrNull() ?: return "时长必须是整数。"
        val rate = draft.rateMbps.toIntOrNull() ?: return "带宽必须是整数。"
        if (traffic <= 0.0 || traffic > pricing.purchaseMaxTrafficGiB) return "流量必须在 1-${pricing.purchaseMaxTrafficGiB} GiB 之间。"
        if (days <= 0 || days > pricing.purchaseMaxDays) return "时长必须在 1-${pricing.purchaseMaxDays} 天之间。"
        if (rate <= 0 || rate > pricing.purchaseMaxRateMbps) return "带宽必须在 1-${pricing.purchaseMaxRateMbps} Mbps 之间。"
        if (amount < 0.01) return "订单金额异常。"
        return null
    }

    fun validateRechargeDraft(isVerified: Boolean, pricing: NasPricingConfig, draft: RechargeDraft, amount: Double): String? {
        if (!isVerified) return "请先完成实名认证。"
        if (draft.targetKey.isBlank()) return "充值目标密钥不能为空。"
        val traffic = draft.trafficGiB.toDoubleOrNull() ?: return "流量必须是数字。"
        val days = draft.days.toIntOrNull() ?: return "时长必须是整数。"
        if (traffic < 0.0 || traffic > pricing.purchaseMaxTrafficGiB) return "流量必须在 0-${pricing.purchaseMaxTrafficGiB} GiB 之间。"
        if (days < 0 || days > pricing.purchaseMaxDays) return "时长必须在 0-${pricing.purchaseMaxDays} 天之间。"
        if (traffic == 0.0 && days == 0) return "充值明细不能为空。"
        if (amount < 0.01) return "订单金额异常。"
        return null
    }

    private fun validatePasswordForNeoAuth(password: String): String? {
        if (password.length < 8) return "密码至少 8 位。"
        if (!password.any(Char::isUpperCase) || !password.any(Char::isLowerCase) || !password.any(Char::isDigit)) {
            return "密码必须包含大小写字母和数字。"
        }
        return null
    }

    private fun validateSixDigitCode(code: String): String? {
        return if (code.length != 6 || !code.all(Char::isDigit)) "请输入验证码。" else null
    }

    private fun intersectNasAuthorizedNodesWithNkmAvailability(keysFromNas: List<NasKey>, onlineNodesFromNkm: List<NkmNode>): List<NasKey> {
        if (onlineNodesFromNkm.isEmpty()) {
            return keysFromNas.map { key -> key.copy(availableNodes = emptyList()) }
        }

        val nkmById = onlineNodesFromNkm.associateBy { normalizeNodeIdentity(it.realId) }
        val nkmByEndpoint = onlineNodesFromNkm.associateBy { endpointKey(it.address, it.hookPort, it.connectPort) }
        val nkmByName = onlineNodesFromNkm.associateBy { normalizeNodeIdentity(it.name) }

        return keysFromNas.map { key ->
            val intersectedNodes = key.availableNodes.mapNotNull { nasNode ->
                val nkmNode = nkmById[normalizeNodeIdentity(nasNode.nodeId)]
                    ?: nkmByEndpoint[endpointKey(nasNode.address, nasNode.hookPort, nasNode.connectPort)]
                    ?: nkmByName[normalizeNodeIdentity(nasNode.displayName)]
                    ?: return@mapNotNull null

                nasNode.copy(
                    nodeId = nkmNode.realId,
                    displayName = nkmNode.name,
                    isOnline = true,
                    address = nkmNode.address,
                    hookPort = nkmNode.hookPort,
                    connectPort = nkmNode.connectPort,
                    icon = nkmNode.icon
                )
            }
            key.copy(availableNodes = intersectedNodes)
        }
    }

    private fun normalizeNodeIdentity(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    private fun endpointKey(address: String, hookPort: Int, connectPort: Int): String {
        return "${address.trim().lowercase(Locale.ROOT)}:$hookPort:$connectPort"
    }

    data class KeyLoadResult(
        val keys: List<NasKey>,
        val warning: String?,
        val refreshedNodeCount: Int?
    )
}
