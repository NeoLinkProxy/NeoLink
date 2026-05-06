package neoproxy.neolink.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import neoproxy.neolink.android.viewmodel.TunnelViewModel

/**
 * 设置页面：端口配置、协议开关、心跳与重连策略。
 * 运行时可热切换 TCP/UDP 标志，其余参数需重连后生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: TunnelViewModel) {
    val hookPort by viewModel.hookPort.collectAsStateWithLifecycle()
    val connectPort by viewModel.connectPort.collectAsStateWithLifecycle()
    val tcpEnabled by viewModel.tcpEnabled.collectAsStateWithLifecycle()
    val udpEnabled by viewModel.udpEnabled.collectAsStateWithLifecycle()
    val ppv2Enabled by viewModel.ppv2Enabled.collectAsStateWithLifecycle()
    val heartbeatDelay by viewModel.heartbeatDelay.collectAsStateWithLifecycle()
    val autoReconnect by viewModel.autoReconnect.collectAsStateWithLifecycle()
    val nodeListUrl by viewModel.nodeListUrl.collectAsStateWithLifecycle()
    val nodeFetchInProgress by viewModel.nodeFetchInProgress.collectAsStateWithLifecycle()
    val nodeFetchMessage by viewModel.nodeFetchMessage.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text("公共节点", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = nodeListUrl,
                onValueChange = { viewModel.nodeListUrl.value = it },
                label = { Text("NKM 节点列表 URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isRunning && !nodeFetchInProgress
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.fetchNodes() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning && !nodeFetchInProgress
            ) {
                if (nodeFetchInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (nodeFetchInProgress) "拉取中..." else "拉取节点")
            }

            if (nodeFetchMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(nodeFetchMessage.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }

            Text(
                text = "说明：节点列表会写入本地 nodes.json；拉取失败时会继续使用本地或内置节点列表。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 端口配置 =====
            Text("端口配置", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = hookPort,
                onValueChange = { viewModel.hookPort.value = it },
                label = { Text("Hook 端口") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                // 运行时不允许修改端口——需要重连
                enabled = !isRunning
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = connectPort,
                onValueChange = { viewModel.connectPort.value = it },
                label = { Text("Connect 端口") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isRunning
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 协议开关 =====
            Text("协议", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            SwitchRow(
                label = "TCP",
                checked = tcpEnabled,
                onCheckedChange = { viewModel.updateProtocol(it, udpEnabled) }
            )

            SwitchRow(
                label = "UDP",
                checked = udpEnabled,
                onCheckedChange = { viewModel.updateProtocol(tcpEnabled, it) }
            )

            SwitchRow(
                label = "Proxy Protocol v2",
                checked = ppv2Enabled,
                onCheckedChange = { viewModel.updatePpv2(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ===== 连接策略 =====
            Text("连接策略", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = heartbeatDelay,
                onValueChange = { viewModel.heartbeatDelay.value = it },
                label = { Text("心跳间隔 (ms)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isRunning
            )

            Spacer(modifier = Modifier.height(12.dp))

            SwitchRow(
                label = "自动重连",
                checked = autoReconnect,
                onCheckedChange = { viewModel.autoReconnect.value = it }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 可复用的 Switch 行组件。
 */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.alignByBaseline()
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
