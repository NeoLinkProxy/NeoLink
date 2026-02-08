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
    Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
    // 删除了 System.setProperty("skiko.renderApi")，让 RTX 5080 自动发挥性能

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
            transparent = true, // 必须开启
            title = "NeoLink",
            icon = appIcon,
            resizable = true
        ) {
            window.minimumSize = Dimension(720, 480)
            window.background = java.awt.Color(0, 0, 0, 0)

            LaunchedEffect(Unit) {
                // RTX 5080 这里 100ms 延迟足够了
                kotlinx.coroutines.delay(100)
                WindowsEffects.applyAcrylicIfPossible(window)
                viewModel.initialize(args)
            }

            neoLinkMainScreen(windowState, viewModel, appIcon, ::exitApplication)
        }
    }
}