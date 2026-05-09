package neoproxy.neolink.gui.model
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import neoproxy.neolink.gui.config.DEFAULT_NAS_URL
import java.util.UUID

enum class AuthMode {
    LOGIN,
    REGISTER,
    VERIFY_IDENTITY
}

enum class MainPage {
    TUNNELS,
    USER_CENTER,
    SETTINGS
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val nasUrl: String = DEFAULT_NAS_URL,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val code: String = "",
    val realName: String = "",
    val idCard: String = "",
    val sessionToken: String = "",
    val isAuthenticated: Boolean = false,
    val isVerified: Boolean = false,
    val isRestoringSession: Boolean = false,
    val isLoading: Boolean = false,
    val message: String = ""
)

data class NasNode(
    var nodeId: String = "",
    var displayName: String = "",
    var isOnline: Boolean = false,
    var address: String = "",
    var hookPort: Int = 44801,
    var connectPort: Int = 44802,
    var icon: String = ""
)

data class NkmNode(
    val realId: String,
    val name: String,
    val address: String,
    val icon: String,
    val hookPort: Int,
    val connectPort: Int
)

data class NasKey(
    var alias: String = "",
    var realKey: String = "",
    var type: String = "",
    var balanceMiB: Double = 0.0,
    var expire: String = "",
    var status: String = "",
    var rate: String = "",
    var port: String = "",
    var availableNodes: List<NasNode> = emptyList(),
    var refreshCount: Int = 0,
    var refreshMaxPerDay: Int = 0,
    var refreshRemainingToday: Int = 0
) {
    val isTrial: Boolean
        get() = type.equals("FREE", ignoreCase = true)

    val onlineNodes: List<NasNode>
        get() = availableNodes.filter { it.isOnline && it.address.isNotBlank() && it.hookPort in 1..65535 && it.connectPort in 1..65535 }

    val displayType: String
        get() = if (isTrial) "体验版" else "正式版"
}

data class TrafficPoint(
    val second: Long,
    val bytes: Long
)

data class TunnelRuntimeUiState(
    val running: Boolean = false,
    val stopping: Boolean = false,
    val activeConnections: Int = 0,
    val totalTrafficBytes: Long = 0L,
    val trafficSinceBalanceSyncBytes: Long = 0L,
    val trafficPoints: List<TrafficPoint> = emptyList(),
    val logs: List<AnnotatedString> = emptyList()
)

data class RuntimeUiState(
    val logMessages: List<AnnotatedString> = emptyList()
)

data class TunnelCardState(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var keyAlias: String = "",
    var keyType: String = "",
    var keyBalanceMiB: Double = 0.0,
    var keyInitialBalanceMiB: Double = 0.0,
    var localPort: String = "",
    var selectedNodeId: String = "",
    var selectedNodeName: String = "",
    var remoteDomain: String = "",
    var localDomain: String = "localhost",
    var hookPort: String = "44801",
    var connectPort: String = "44802",
    var tcpEnabled: Boolean = true,
    var udpEnabled: Boolean = true,
    var ppv2Enabled: Boolean = false,
    var autoReconnect: Boolean = true,
    var debugMode: Boolean = false,
    var showConnection: Boolean = true,
    var expanded: Boolean = false
) {
    fun effectiveName(index: Int): String = name.ifBlank { "隧道${index + 1}" }
}

data class TunnelStoreDocument(
    var tunnels: MutableList<TunnelCardState> = mutableListOf()
)

data class SessionStoreDocument(
    var nasUrl: String = "",
    var email: String = "",
    var sessionToken: String = ""
)

data class CreateTunnelDraft(
    val selectedKeyAlias: String = "",
    val selectedNodeId: String = "",
    val localPort: String = ""
)

data class UiState(
    val currentPage: MainPage = MainPage.TUNNELS,
    val sidebarExpanded: Boolean = false,
    val logFontSize: TextUnit = 12.sp,
    val totalTrafficExpanded: Boolean = true,
    val createDialogVisible: Boolean = false,
    val createDraft: CreateTunnelDraft = CreateTunnelDraft()
)
