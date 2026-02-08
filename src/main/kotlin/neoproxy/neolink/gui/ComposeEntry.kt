package neoproxy.neolink.gui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.util.*

fun main(args: Array<String>) {
    // 强制引导系统尝试 DirectX
    System.setProperty("skiko.renderApi", "DIRECTX")

    Locale.setDefault(Locale.SIMPLIFIED_CHINESE)

    application {
        val viewModel = remember { NeoLinkViewModel() }
        val appIcon = painterResource("logo.png")
        val windowState = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(920.dp, 650.dp)
        )

        Window(
            onCloseRequest = {
                viewModel.stopService()
                exitApplication()
            },
            state = windowState,
            undecorated = true,
            transparent = true,
            title = "NeoLink",
            icon = appIcon,
            resizable = true
        ) {
            window.minimumSize = Dimension(720, 480)
            // 初始背景完全透明，交由 Compose 渲染
            window.background = java.awt.Color(0, 0, 0, 0)

            LaunchedEffect(Unit) {
                // 【关键修复】循环等待 Skiko 确定渲染 API 类型 (最多等 2 秒)
                // 解决 RTX 5080 探测到 null 的问题
                var retry = 0
                while (System.getProperty("skiko.renderApi") == null && retry < 10) {
                    kotlinx.coroutines.delay(200)
                    retry++
                }

                // 应用视觉效果
                WindowsEffects.applyAcrylicIfPossible(window)
                viewModel.initialize(args)
            }

            neoLinkMainScreen(windowState, viewModel, appIcon, ::exitApplication)
        }
    }
}