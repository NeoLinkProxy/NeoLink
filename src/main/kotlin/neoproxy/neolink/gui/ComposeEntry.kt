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
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource

fun main(args: Array<String>) {
    // 1. 环境预检：获取真实 Build 号
    val buildNumber = WindowsEffects.getRealBuildNumber()

    // 2. 决定是否使用“现代窗口”模式 (Win11 且硬件加速正常)
    // 如果是 Win10 (19045等) 或更老，useModernWindow 将为 false
    val useModernWindow = buildNumber >= 22000

    // 3. 如果是老电脑，强制锁定软件渲染以保全 UI 显示，防止 DX12 报错导致黑屏
    if (!useModernWindow) {
        System.setProperty("skiko.renderApi", "SOFTWARE")
    } else {
        // 高性能机器尝试开启 DirectX 加速
        System.setProperty("skiko.renderApi", "DIRECTX")
    }

    Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
    customizeSwingLook()

    application {
        // 定义全局状态
        val viewModel = remember { NeoLinkViewModel() }
        val appIcon = painterResource("logo.png")

        // 【核心修复】在这里定义 windowState，确保下面的 Window 和 neoLinkMainScreen 都能引用到它
        val windowState = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(920.dp, 650.dp)
        )

        Window(
            onCloseRequest = {
                viewModel.stopService()
                exitApplication()
            },
            state = windowState, // 引用上面定义的变量
            title = "NeoLink 内网穿透客户端",
            icon = appIcon,
            // 老电脑模式 (useModernWindow = false)：
            // undecorated = false (使用原生标题栏), transparent = false (不透明)
            // 这能 100% 保证在 HD3000 等显卡上 UI 不消失
            undecorated = useModernWindow,
            transparent = useModernWindow,
            resizable = true
        ) {
            // 设置窗口最小尺寸
            window.minimumSize = Dimension(720, 480)

            // 根据模式设置底层 AWT 窗口背景
            if (useModernWindow) {
                window.background = java.awt.Color(0, 0, 0, 0)
            } else {
                window.background = java.awt.Color(18, 18, 20)
            }

            LaunchedEffect(Unit) {
                if (useModernWindow) {
                    // Win11 模式下尝试应用亚克力特效
                    var retry = 0
                    while (System.getProperty("skiko.renderApi") == null && retry < 5) {
                        kotlinx.coroutines.delay(200)
                        retry++
                    }
                    WindowsEffects.applyAcrylicIfPossible(window)
                }

                // 初始化业务逻辑
                viewModel.initialize(args)
            }

            // 调用主界面，传入所有必要参数
            neoLinkMainScreen(
                windowState = windowState,
                viewModel = viewModel,
                appIcon = appIcon,
                isModern = useModernWindow, // 告诉 UI 是否需要画自定义标题栏
                onExit = ::exitApplication
            )
        }
    }
}

fun customizeSwingLook() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        val bg = ColorUIResource(30, 30, 32)
        val fg = ColorUIResource(220, 220, 220)
        val accent = ColorUIResource(59, 130, 246)
        val border = ColorUIResource(60, 60, 60)

        val keys = arrayOf(
            "PopupMenu.background", bg,
            "PopupMenu.foreground", fg,
            "PopupMenu.border", javax.swing.BorderFactory.createLineBorder(border),
            "MenuItem.background", bg,
            "MenuItem.foreground", fg,
            "MenuItem.selectionBackground", accent,
            "MenuItem.selectionForeground", ColorUIResource(255, 255, 255)
        )
        val defaults = UIManager.getDefaults()
        for (i in keys.indices step 2) {
            defaults[keys[i]] = keys[i + 1]
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}