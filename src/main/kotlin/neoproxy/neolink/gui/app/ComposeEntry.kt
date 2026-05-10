package neoproxy.neolink.gui.app
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
import neoproxy.neolink.config.ConfigOperator
import neoproxy.neolink.config.LanguageData
import neoproxy.neolink.gui.platform.GuiRenderBackend
import neoproxy.neolink.gui.platform.RenderState
import neoproxy.neolink.gui.platform.WindowsEffects
import neoproxy.neolink.gui.state.NeoLinkViewModel
import neoproxy.neolink.gui.ui.screens.neoLinkMainScreen
import neoproxy.neolink.state.RuntimeState
import neoproxy.neolink.util.Debugger.debugOperation
import java.awt.Dimension
import java.io.PrintStream
import java.util.Locale
import javax.swing.UIManager
import javax.swing.JOptionPane
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
    val singleInstanceGuard = try {
        NeoLinkSingleInstanceGuard.acquire()
    } catch (e: Exception) {
        showStartupNotice("NeoLink 启动失败", "无法创建单实例锁：${e.message ?: e.javaClass.simpleName}")
        return
    }
    if (singleInstanceGuard == null) {
        showStartupNotice("NeoLink 已在运行", "新版桌面 UI 采用单实例模型，请先关闭正在运行的 NeoLink。")
        return
    }

    RuntimeState.setLanguageData(LanguageData.getChineseLanguage())

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

    try {
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
            singleInstanceGuard.close()
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
    } finally {
        singleInstanceGuard.close()
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
        debugOperation(e)
    }
}

fun showStartupNotice(title: String, message: String) {
    customizeSwingLook()
    JOptionPane.showMessageDialog(null, message, title, JOptionPane.WARNING_MESSAGE)
}
