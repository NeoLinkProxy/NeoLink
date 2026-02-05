package neoproxy.neolink.gui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

// ComposeEntry.kt

fun main(args: Array<String>) {
    application {
        val viewModel = remember { NeoLinkViewModel() }

        // 1. 加载资源文件中的 logo.png
        // 注意：painterResource 在 Compose 里返回的是非空的 Painter
        val appIcon = painterResource("logo.png")

        val windowState = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(950.dp, 700.dp)
        )

        Window(
            onCloseRequest = {
                viewModel.stopService()
                exitApplication()
            },
            state = windowState,
            undecorated = true, // 去掉系统标题栏
            transparent = false,
            title = "NeoLink - 内网穿透客户端",
            icon = appIcon, // 设置任务栏图标
            resizable = true
        ) {
            // 设置窗口最小尺寸 (Swing 兼容代码)
            window.minimumSize = Dimension(600, 400)

            LaunchedEffect(Unit) {
                viewModel.initialize(args)
            }

            // 2. 调用主界面，传入加载好的 appIcon
            // 使用反引号框起来以符合你的要求
            `NeoLinkMainScreen`(windowState, viewModel, appIcon, ::exitApplication)
        }
    }
}