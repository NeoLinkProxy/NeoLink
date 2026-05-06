package neoproxy.neolink.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import neoproxy.neolink.android.viewmodel.TunnelViewModel

/**
 * 底部导航项定义。
 * 使用 sealed class 保证路由和图标的编译期安全。
 */
sealed class NavItem(val route: String, val label: String, val icon: ImageVector) {
    data object Home : NavItem("home", "主页", Icons.Default.Home)
    data object Settings : NavItem("settings", "设置", Icons.Default.Settings)
    data object Logs : NavItem("logs", "日志", Icons.AutoMirrored.Filled.List)
}

private val navItems = listOf(NavItem.Home, NavItem.Settings, NavItem.Logs)

/**
 * 导航图：主页、设置、日志三个目的地。
 * ViewModel 在此层获取，保证三个页面共享同一实例。
 */
@Composable
fun NeoLinkNavHost(
    notificationPermissionGranted: Boolean,
    requestNotificationPermission: () -> Unit
) {
    val navController = rememberNavController()
    // 在 NavHost 层级获取 ViewModel，三个 Screen 共享同一实例
    val tunnelViewModel: TunnelViewModel = viewModel()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                navItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                // 避免重复入栈：回到起始目的地
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavItem.Home.route) {
                HomeScreen(
                    viewModel = tunnelViewModel,
                    notificationPermissionGranted = notificationPermissionGranted,
                    requestNotificationPermission = requestNotificationPermission
                )
            }
            composable(NavItem.Settings.route) {
                SettingsScreen(viewModel = tunnelViewModel)
            }
            composable(NavItem.Logs.route) {
                LogsScreen(viewModel = tunnelViewModel)
            }
        }
    }
}
