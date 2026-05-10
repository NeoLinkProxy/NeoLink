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
import kotlinx.coroutines.delay
import neoproxy.neolink.cli.ClientConsole
import neoproxy.neolink.config.ConfigOperator
import neoproxy.neolink.config.LanguageData
import neoproxy.neolink.state.RuntimeState
import java.awt.Dimension
import java.io.PrintStream
import java.util.Locale
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import kotlin.system.exitProcess

/**
 * GUI 应用入口。
 *
 * <p>设计原因：
 * 桌面 GUI 明确只使用中文，因此入口会在输出任何面向用户的日志之前先锁定运行时语言。
 * 初始化过程也做了包装，避免无效配置或参数把整个窗口生命周期直接打崩。</p>
 */
fun main(args: Array<String>) {
    val isNoEffectMode = args.contains("--no-effect")

    if (isNoEffectMode) {
        RenderState.useSoftwareOpaque("用户通过 --no-effect 显式禁用 GUI 特效", forcedByUser = true)
        println("[启动模式] 已启用 --no-effect 参数，强制使用软件渲染模式（无特效）")
    } else {
        val checkResult = NeoLinkPreFlightChecker.runFullCheck()
        if (checkResult.allowsAcrylicDirect3D) {
            RenderState.useDirect3DAcrylic(checkResult.description)
        } else {
            RenderState.useSoftwareOpaque(checkResult.description)
        }
    }

    ConfigOperator.initEnvironment()
    RuntimeState.setLanguageData(LanguageData.getChineseLanguage())
    ClientConsole.initializeLogger(false)

    val originalErr = System.err
    System.setErr(object : PrintStream(originalErr) {
        override fun write(buf: ByteArray, off: Int, len: Int) {
            val msg = String(buf, off, len)
            if (msg.contains("RenderException") || msg.contains("DirectX12")) {
                if (!RenderState.isOpaqueFallback) {
                    WindowsEffects.markEffectUnavailable()
                    RenderState.disableEffectsForCurrentProcess("Skiko 渲染异常，当前进程关闭透明背景")
                    System.setProperty("skiko.renderApi", GuiRenderBackend.SOFTWARE.skikoValue)
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

        val useTransparentWindow = RenderState.shouldUseTransparentWindow

        val closeApp = {
            viewModel.dispose()
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

            LaunchedEffect(RenderState.decision) {
                if (RenderState.isOpaqueFallback) {
                    window.background = java.awt.Color(18, 18, 20)
                    window.revalidate()
                    window.repaint()
                }
            }

            LaunchedEffect(Unit) {
                try {
                    viewModel.initialize(args)
                } catch (e: Exception) {
                    viewModel.appendLog("[System] GUI 初始化失败：${e.message ?: e.javaClass.simpleName}")
                }

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
        for (i in keys.indices step 2) {
            defaults[keys[i]] = keys[i + 1]
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
