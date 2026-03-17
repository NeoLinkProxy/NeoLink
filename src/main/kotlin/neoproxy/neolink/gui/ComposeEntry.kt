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
import neoproxy.neolink.ConfigOperator
import neoproxy.neolink.NeoLink
import java.awt.Dimension
import java.io.PrintStream
import java.util.*
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import kotlin.system.exitProcess

object RenderState {
    var isSoftwareFallback by mutableStateOf(false)
}

fun main(args: Array<String>) {
    val checkResult = NeoLinkPreFlightChecker.runFullCheck()

    if (checkResult.isHardwareOk) {
        System.setProperty("skiko.renderApi", "DIRECTX")
        RenderState.isSoftwareFallback = false
    } else {
        System.setProperty("skiko.renderApi", "SOFTWARE")
        RenderState.isSoftwareFallback = true
    }

    // 确保在任何组件（包括异步任务）调用 NeoLink.say() 之前，loggist 已经实例化
    ConfigOperator.initEnvironment()
    NeoLink.initializeLogger()
    NeoLink.detectLanguage()

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

        val useTransparentWindow = !RenderState.isSoftwareFallback

        val closeApp = {
            viewModel.stopService()
            exitApplication()
            exitProcess(0)
        }

        Window(
            onCloseRequest = closeApp,
            state = windowState,
            title = "NeoLink 内网穿透客户端",
            icon = appIcon,
            undecorated = true,
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
                // [优化点]：确保 ViewModel 初始化具备容错性
                // 即使 NodeFetcher 还在后台写文件，viewModel 也应有逻辑跳过或延迟加载节点
                viewModel.initialize(args)

                if (useTransparentWindow) {
                    delay(500)
                    WindowsEffects.applyAcrylicIfPossible(window)
                }
            }

            neoLinkMainScreen(
                windowState = windowState,
                viewModel = viewModel,
                appIcon = appIcon,
                onExit = closeApp
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