package neoproxy.neolink.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import neoproxy.neolink.android.viewmodel.TunnelViewModel
import top.ceroxe.api.neolink.NeoLinkState

private val TunnelTransitioning = Color(0xFFF4B000)

/**
 * 主页：连接参数输入 + 启动/停止控制 + 状态展示。
 * 设计遵循「信息层级递进」：状态指示 → 核心操作 → 详细参数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TunnelViewModel,
    notificationPermissionGranted: Boolean,
    requestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val tunnelState by viewModel.tunnelState.collectAsStateWithLifecycle()
    val tunnelAddress by viewModel.tunnelAddress.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()

    // 表单状态
    val remoteDomain by viewModel.remoteDomain.collectAsStateWithLifecycle()
    val accessKey by viewModel.accessKey.collectAsStateWithLifecycle()
    val localPort by viewModel.localPort.collectAsStateWithLifecycle()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val selectedNodeId by viewModel.selectedNodeId.collectAsStateWithLifecycle()

    // Snackbar 用于显示错误
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NeoLink") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ===== 状态指示卡片 =====
            StatusCard(tunnelState, tunnelAddress, lastError, context)

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 连接参数输入区 =====
            if (nodes.isNotEmpty()) {
                NodeSelector(
                    nodes = nodes,
                    selectedNodeId = selectedNodeId,
                    enabled = !isRunning && !isConnecting,
                    onSelectNode = viewModel::selectNode
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = remoteDomain,
                onValueChange = { viewModel.remoteDomain.value = it },
                label = { Text(if (selectedNodeId == null) "远程域名（手动）" else "远程域名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isRunning && !isConnecting && selectedNodeId == null
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = accessKey,
                onValueChange = { viewModel.accessKey.value = it },
                label = { Text("访问密钥") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !isRunning && !isConnecting
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = localPort,
                onValueChange = { viewModel.localPort.value = it },
                label = { Text("本地端口") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isRunning && !isConnecting
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ===== 连接/断开按钮 =====
            ConnectButton(
                tunnelState = tunnelState,
                isRunning = isRunning,
                isConnecting = isConnecting,
                onConnect = {
                    if (!notificationPermissionGranted) {
                        requestNotificationPermission()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("请先允许通知权限，前台服务需要显示连接状态")
                        }
                        return@ConnectButton
                    }
                    val error = viewModel.startTunnel()
                    if (error != null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(error)
                        }
                    }
                },
                onDisconnect = {
                    val error = viewModel.stopTunnel()
                    if (error != null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(error)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(
    tunnelState: NeoLinkState,
    tunnelAddress: String?,
    lastError: String?,
    context: Context
) {
    // 状态点必须比品牌色更语义化：蓝色留给“连接动作”，绿色/黄/红只表达运行状态，
    // 避免用户在高压排障时把“失败”和“可点击主操作”混在一起。
    val statusColor by animateColorAsState(
        targetValue = when (tunnelState) {
            NeoLinkState.RUNNING -> MaterialTheme.colorScheme.tertiary
            NeoLinkState.STARTING, NeoLinkState.STOPPING -> TunnelTransitioning
            NeoLinkState.FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        },
        label = "statusColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusColor,
                    modifier = Modifier.size(12.dp)
                ) {}
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when (tunnelState) {
                        NeoLinkState.STOPPED -> "未连接"
                        NeoLinkState.STARTING -> "正在连接..."
                        NeoLinkState.RUNNING -> "已连接"
                        NeoLinkState.STOPPING -> "正在断开..."
                        NeoLinkState.FAILED -> "连接失败"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // 隧道地址展示（已连接时才显示）
            if (tunnelAddress != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tunnelAddress,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        try {
                            // 系统服务理论上应稳定存在；这里仍兜底，避免定制 ROM 返回异常对象时点击复制崩溃。
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            if (clipboard == null) {
                                Toast.makeText(context, "剪贴板不可用", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            clipboard.setPrimaryClip(ClipData.newPlainText("tunnel_addr", tunnelAddress))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                    }
                }
            }

            if (lastError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "最近错误: $lastError",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ConnectButton(
    tunnelState: NeoLinkState,
    isRunning: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "buttonColor"
    )

    Button(
        onClick = {
            if (isRunning || isConnecting) onDisconnect() else onConnect()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        enabled = tunnelState != NeoLinkState.STOPPING
    ) {
        if (isConnecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = when {
                isConnecting -> "连接中..."
                isRunning -> "断开连接"
                else -> "连接"
            },
            style = MaterialTheme.typography.titleMedium
        )
    }
}
