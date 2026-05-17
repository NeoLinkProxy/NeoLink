package neoproxy.neolink.gui.ui.screens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import neoproxy.neolink.gui.model.MainPage
import neoproxy.neolink.gui.state.NeoLinkViewModel
import neoproxy.neolink.gui.ui.components.labelText
import neoproxy.neolink.gui.ui.components.modernCheckbox
import neoproxy.neolink.gui.ui.components.modernTextField
import neoproxy.neolink.gui.ui.components.sectionCard
import neoproxy.neolink.gui.ui.components.sectionTitle
import neoproxy.neolink.gui.ui.theme.ModernTheme

private const val PageCrossfadeDurationMs = 180

@Composable
fun workspaceScreen(viewModel: NeoLinkViewModel, onAlert: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxSize()) {
        sidebar(viewModel)
        Divider(Modifier.fillMaxHeight().width(1.dp), color = ModernTheme.divider.copy(alpha = 0.72f))
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.fillMaxWidth().widthIn(max = 1000.dp)) {
                Crossfade(
                    targetState = viewModel.uiState.currentPage,
                    animationSpec = tween(durationMillis = PageCrossfadeDurationMs),
                    label = "workspace-page-crossfade"
                ) { page ->
                    when (page) {
                        MainPage.TUNNELS -> tunnelManagementPage(viewModel, onAlert)
                        MainPage.PURCHASE -> purchasePage(viewModel)
                        MainPage.KEY_MANAGEMENT -> keyManagementPage(viewModel)
                        MainPage.TUTORIAL -> tutorialPage(viewModel)
                        MainPage.USER_CENTER -> userCenterPage(viewModel)
                        MainPage.SETTINGS -> settingsPage(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun sidebar(viewModel: NeoLinkViewModel) {
    val width by animateDpAsState(if (viewModel.uiState.sidebarExpanded) 164.dp else 50.dp, tween(220))
    Column(
        modifier = Modifier.width(width).fillMaxHeight().background(ModernTheme.sidebar).padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = viewModel::toggleSidebar, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Menu, null, tint = ModernTheme.textSecondary)
            }
            sidebarItem(viewModel, MainPage.TUNNELS, Icons.Default.Share, "隧道管理")
            sidebarItem(viewModel, MainPage.PURCHASE, Icons.Default.ShoppingCart, "购买服务")
            sidebarItem(viewModel, MainPage.KEY_MANAGEMENT, Icons.Default.VpnKey, "密钥管理")
            sidebarItem(viewModel, MainPage.TUTORIAL, Icons.Default.Info, "使用教程")
            sidebarItem(viewModel, MainPage.USER_CENTER, Icons.Default.AccountCircle, "用户中心")
        }
        sidebarItem(viewModel, MainPage.SETTINGS, Icons.Default.Settings, "设置")
    }
}

@Composable
fun sidebarItem(viewModel: NeoLinkViewModel, page: MainPage, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val selected = viewModel.uiState.currentPage == page
    val bg by animateColorAsState(
        if (selected) ModernTheme.accent.copy(alpha = 0.18f) else Color.Transparent,
        tween(180)
    )
    Box(
        modifier = Modifier.fillMaxWidth().height(38.dp)
            .background(bg, ModernTheme.shapeSmall)
            .clickable { viewModel.setPage(page) }
    ) {
        if (selected) {
            Box(Modifier.align(Alignment.CenterStart).width(4.dp).height(24.dp).background(ModernTheme.accent, RoundedCornerShape(2.dp)))
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (selected) ModernTheme.accent else ModernTheme.textSecondary, modifier = Modifier.size(18.dp))
            AnimatedVisibility(viewModel.uiState.sidebarExpanded) {
                Row {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        label,
                        color = if (selected) ModernTheme.textPrimary else ModernTheme.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

@Composable
fun userCenterPage(viewModel: NeoLinkViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sectionCard {
            sectionTitle("用户中心")
            Spacer(Modifier.height(10.dp))
            Text("用户: ${viewModel.authenticatedEmail}", color = ModernTheme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("实名认证: ${if (viewModel.authState.isVerified) "已完成" else "未完成"}", color = if (viewModel.authState.isVerified) ModernTheme.success else ModernTheme.error, fontSize = 13.sp)
            Text("密钥总数: ${viewModel.totalKeyCount}", color = ModernTheme.textSecondary, fontSize = 13.sp)
        }
        Button(onClick = viewModel::logout, shape = ModernTheme.shapeSmall, colors = ButtonDefaults.buttonColors(backgroundColor = ModernTheme.error), elevation = ButtonDefaults.elevation(0.dp, 0.dp)) {
            Text("退出登录", color = Color.White, modifier = Modifier)
        }
    }
}

@Composable
fun settingsPage(viewModel: NeoLinkViewModel) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            sectionTitle("设置")
            Text(
                "这些设置会影响登录服务、节点列表和所有隧道的默认连接行为。",
                color = ModernTheme.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            sectionCard {
                sectionTitle("服务地址")
                Spacer(Modifier.height(10.dp))
                configSettingField(
                    label = "NAS 服务地址",
                    configKey = "NAS_URL",
                    description = "用于登录、注册、实名认证，以及拉取当前账号已授权的节点。",
                    value = viewModel.settingsState.nasUrl,
                    onValueChange = viewModel::updateNasUrl,
                    placeholder = "https://nas.example.com"
                )
                Spacer(Modifier.height(12.dp))
                configSettingField(
                    label = "节点列表地址",
                    configKey = "NKM_NODELIST_URL",
                    description = "用于获取当前在线节点。请求失败时会回退到本地节点缓存。",
                    value = viewModel.settingsState.nkmNodeListUrl,
                    onValueChange = viewModel::updateNkmNodeListUrl,
                    placeholder = "https://node.example.com/client/nodelist"
                )
            }

            sectionCard {
                sectionTitle("连接行为")
                Spacer(Modifier.height(10.dp))
                configSettingField(
                    label = "连接本地服务时使用的代理",
                    configKey = "PROXY_IP_TO_LOCAL_SERVER",
                    description = "隧道连接本地下游服务时使用；留空表示直连。",
                    value = viewModel.settingsState.proxyIPToLocalServer,
                    onValueChange = viewModel::updateProxyIPToLocalServer,
                    placeholder = "socks->127.0.0.1:7890"
                )
                Spacer(Modifier.height(12.dp))
                configSettingField(
                    label = "连接 NeoProxyServer 时使用的代理",
                    configKey = "PROXY_IP_TO_NEO_SERVER",
                    description = "隧道连接远端服务器时使用；留空表示直连。",
                    value = viewModel.settingsState.proxyIPToNeoServer,
                    onValueChange = viewModel::updateProxyIPToNeoServer,
                    placeholder = "socks->127.0.0.1:7890"
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    configSettingField(
                        label = "心跳间隔",
                        configKey = "HEARTBEAT_PACKET_DELAY",
                        description = "单位毫秒，数值越小检测越及时。",
                        value = viewModel.settingsState.heartbeatPacketDelay,
                        onValueChange = viewModel::updateHeartbeatPacketDelay,
                        placeholder = "1000",
                        modifier = Modifier.weight(1f)
                    )
                    configSettingField(
                        label = "重连等待",
                        configKey = "RECONNECTION_INTERVAL",
                        description = "自动重连失败后的等待秒数。",
                        value = viewModel.settingsState.reconnectionIntervalSeconds,
                        onValueChange = viewModel::updateReconnectionInterval,
                        placeholder = "30",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                modernCheckbox("自动检查更新", viewModel.settingsState.enableAutoUpdate, viewModel::updateEnableAutoUpdate)
                Spacer(Modifier.height(4.dp))
                Text("ENABLE_AUTO_UPDATE", color = ModernTheme.textSecondary.copy(alpha = 0.72f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

        sectionCard {
            sectionTitle("应用信息")
            Spacer(Modifier.height(10.dp))
            labelText("壳版本")
            Text(viewModel.appVersion, color = ModernTheme.textPrimary, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            if (viewModel.settingsState.message.isNotBlank()) {
                Text(
                    viewModel.settingsState.message,
                    color = if (viewModel.settingsState.message.contains("已保存")) ModernTheme.success else ModernTheme.error,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = viewModel::saveSettings, shape = ModernTheme.shapeSmall, colors = ButtonDefaults.buttonColors(backgroundColor = ModernTheme.success), elevation = ButtonDefaults.elevation(0.dp, 0.dp)) {
                Text("保存设置", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier)
            }
        }
    }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

@Composable
private fun configSettingField(
    label: String,
    configKey: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        labelText(label)
        Spacer(Modifier.height(2.dp))
        Text(configKey, color = ModernTheme.textSecondary.copy(alpha = 0.72f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
        Text(description, color = ModernTheme.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(6.dp))
        modernTextField(value, onValueChange, placeholder = placeholder)
    }
}
