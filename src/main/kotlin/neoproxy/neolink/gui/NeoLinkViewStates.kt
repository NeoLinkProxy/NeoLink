package neoproxy.neolink.gui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import top.ceroxe.api.neolink.NeoNode

data class ConnectionUiState(
    val remoteDomain: String = "localhost",
    val localPort: String = "",
    val accessKey: String = "",
    val localDomain: String = "localhost",
    val hostHookPort: String = "44801",
    val hostConnectPort: String = "44802"
)

data class FeatureToggleUiState(
    val tcpEnabled: Boolean = true,
    val udpEnabled: Boolean = true,
    val ppv2Enabled: Boolean = false,
    val autoReconnect: Boolean = true,
    val debugMode: Boolean = false,
    val showConnection: Boolean = true
)

data class UiState(
    val nodeList: List<NeoNode> = emptyList(),
    val selectedNode: NeoNode? = null,
    val logFontSize: TextUnit = 12.sp
)

data class RuntimeUiState(
    val isRunning: Boolean = false,
    val isStopping: Boolean = false,
    val logMessages: List<AnnotatedString> = emptyList()
)
