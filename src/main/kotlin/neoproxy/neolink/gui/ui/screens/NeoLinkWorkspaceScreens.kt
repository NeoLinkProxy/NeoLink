package neoproxy.neolink.gui.ui.screens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import neoproxy.neolink.gui.model.MainPage
import neoproxy.neolink.gui.state.NeoLinkViewModel
import neoproxy.neolink.gui.ui.components.labelText
import neoproxy.neolink.gui.ui.components.modernTextField
import neoproxy.neolink.gui.ui.components.sectionCard
import neoproxy.neolink.gui.ui.components.sectionTitle
import neoproxy.neolink.gui.ui.theme.ModernTheme

private val SidebarPrimaryTextIconBaselineOffset = (-3).dp
private val SidebarSettingsTextIconBaselineOffset = (-4).dp
private val MaterialButtonTextBaselineOffset = (-2).dp

@Composable
fun workspaceScreen(viewModel: NeoLinkViewModel, onAlert: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxSize()) {
        sidebar(viewModel)
        Divider(Modifier.fillMaxHeight().width(1.dp), color = ModernTheme.divider.copy(alpha = 0.72f))
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.TopCenter) {
            Box(modifier = Modifier.fillMaxWidth().widthIn(max = 1000.dp)) {
                when (viewModel.uiState.currentPage) {
                    MainPage.TUNNELS -> tunnelManagementPage(viewModel, onAlert)
                    MainPage.USER_CENTER -> userCenterPage(viewModel)
                    MainPage.SETTINGS -> settingsPage(viewModel)
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
                        // Compose Desktop 在 Windows 上的字体边界会让文本看起来低于矢量图标。
                        // 侧边栏图标行保留 7.x 的视觉基线补偿。
                        modifier = Modifier.offset(y = sidebarTextBaselineOffset(page))
                    )
                }
            }
        }
    }
}

private fun sidebarTextBaselineOffset(page: MainPage) =
    if (page == MainPage.SETTINGS) SidebarSettingsTextIconBaselineOffset else SidebarPrimaryTextIconBaselineOffset

@Composable
fun userCenterPage(viewModel: NeoLinkViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sectionCard {
            sectionTitle("用户中心")
            Spacer(Modifier.height(10.dp))
            Text("用户: ${viewModel.authenticatedEmail}", color = ModernTheme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("实名认证: 已完成", color = ModernTheme.success, fontSize = 13.sp)
            Text("密钥总数: ${viewModel.totalKeyCount}", color = ModernTheme.textSecondary, fontSize = 13.sp)
        }
        Button(onClick = viewModel::logout, shape = ModernTheme.shapeSmall, colors = ButtonDefaults.buttonColors(backgroundColor = ModernTheme.error), elevation = ButtonDefaults.elevation(0.dp, 0.dp)) {
            Text("退出登录", color = Color.White, modifier = Modifier.offset(y = MaterialButtonTextBaselineOffset))
        }
    }
}

@Composable
fun settingsPage(viewModel: NeoLinkViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sectionCard {
            sectionTitle("设置")
            Spacer(Modifier.height(10.dp))
            labelText("NAS_URL")
            modernTextField(viewModel.authState.nasUrl, viewModel::updateNasUrl, placeholder = "https://nas.example.com")
            Spacer(Modifier.height(12.dp))
            labelText("壳版本")
            Text(viewModel.appVersion, color = ModernTheme.textPrimary, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = viewModel::saveSettings, shape = ModernTheme.shapeSmall, colors = ButtonDefaults.buttonColors(backgroundColor = ModernTheme.primary), elevation = ButtonDefaults.elevation(0.dp, 0.dp)) {
                Text("保存设置", color = Color.White, modifier = Modifier.offset(y = MaterialButtonTextBaselineOffset))
            }
        }
    }
}
