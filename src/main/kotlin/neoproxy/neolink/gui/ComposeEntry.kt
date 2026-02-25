package neoproxy.neolink.gui

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import java.awt.Dimension
import java.io.PrintStream
import java.util.*
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import kotlin.system.exitProcess

/**
 * 全局渲染状态
 */
object RenderState {
    var isSoftwareFallback by mutableStateOf(false)
}

fun main(args: Array<String>) {
    // 1. 调用“隐形窗口”功能实测
    val checkResult = NeoLinkPreFlightChecker.runFullCheck()

    // 2. 根据实测结果强制配置环境
    if (checkResult.isHardwareOk) {
        println("[系统配置] ✅ 实测支持合成特效，启用 DIRECTX 模式。")
        System.setProperty("skiko.renderApi", "DIRECTX")
        RenderState.isSoftwareFallback = false
    } else {
        println("[系统配置] ❌ 实测不支持合成特效 (原因: ${checkResult.description})")
        println("[系统配置] 强制进入 SOFTWARE 模式，关闭透明属性以解决点击穿透问题。")
        System.setProperty("skiko.renderApi", "SOFTWARE")
        RenderState.isSoftwareFallback = true
    }

    // 3. 日志劫持 (二道防线)
    val originalErr = System.err
    System.setErr(object : PrintStream(originalErr) {
        override fun write(buf: ByteArray, off: Int, len: Int) {
            val msg = String(buf, off, len)
            if (msg.contains("RenderException") || msg.contains("DirectX12")) {
                if (!RenderState.isSoftwareFallback) {
                    RenderState.isSoftwareFallback = true
                    System.setProperty("skiko.renderApi", "SOFTWARE")
                }
            }
            super.write(buf, off, len)
        }
    })

    Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
    customizeSwingLook()

    application {
        val viewModel = remember { NeoLinkViewModel() }
        val appIcon = painterResource("logo.png")
        val windowState = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(920.dp, 650.dp)
        )

        // 这里的 useTransparentWindow 是解决穿透问题的命根子
        val useTransparentWindow = !RenderState.isSoftwareFallback

        // 核心修复：统一定义彻底退出的闭包逻辑，停止服务 -> 退出 Compose -> 强杀 JVM 进程
        val closeApp = {
            viewModel.stopService()
            exitApplication()
            exitProcess(0)
        }

        Window(
            onCloseRequest = closeApp, // 修复 1: 绑定右上角系统原生关闭事件
            state = windowState,
            title = "NeoLink 内网穿透客户端",
            icon = appIcon,
            undecorated = true,
            // 如果实测不支持 DWM 亚克力，这里就是 false (实心窗口)，鼠标就点不穿了
            transparent = useTransparentWindow,
            resizable = true
        ) {
            window.minimumSize = Dimension(720, 480)

            window.background = if (useTransparentWindow) {
                java.awt.Color(0, 0, 0, 0)
            } else {
                java.awt.Color(18, 18, 20)
            }

            LaunchedEffect(RenderState.isSoftwareFallback) {
                if (RenderState.isSoftwareFallback) {
                    window.background = java.awt.Color(18, 18, 20)
                    window.revalidate()
                    window.repaint()
                }
            }

            LaunchedEffect(Unit) {
                viewModel.initialize(args)

                // 如果预检通过了，我们再在主窗口上正式应用特效
                if (useTransparentWindow) {
                    delay(500)
                    WindowsEffects.applyAcrylicIfPossible(window)
                }
            }

            neoLinkMainScreen(
                windowState = windowState,
                viewModel = viewModel,
                appIcon = appIcon,
                onExit = closeApp // 修复 2: 绑定自定义 TitleBar 上的 X 按钮事件
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
            "PopupMenu.background", bg, "PopupMenu.foreground", fg,
            "PopupMenu.border", javax.swing.BorderFactory.createLineBorder(border),
            "MenuItem.background", bg, "MenuItem.foreground", fg,
            "MenuItem.selectionBackground", accent, "MenuItem.selectionForeground", ColorUIResource(255, 255, 255)
        )
        val defaults = UIManager.getDefaults()
        for (i in keys.indices step 2) defaults[keys[i]] = keys[i + 1]
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
