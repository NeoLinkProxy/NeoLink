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
import neoproxy.neolink.NeoLink
import neoproxy.neolink.config.ConfigOperator
import java.awt.Dimension
import java.io.PrintStream
import java.util.*
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import kotlin.system.exitProcess

/**
 * GUI 应用程序入口点
 *
 * 核心职责：
 * 1. 执行 DWM 透明背板预检
 * 2. 配置图形界面渲染决策（DirectX + 亚克力，或 Software + 不透明）
 * 3. 初始化日志系统和语言设置
 * 4. 设置 Swing 外观
 * 5. 创建并显示主窗口
 *
 * 渲染策略：
 * - 优先使用 DirectX + 亚克力窗口
 * - 检测到 DWM 或透明窗口风险时自动回退到 Software + 不透明窗口
 * - 捕获并处理渲染异常
 *
 * @param args 命令行参数
 */
fun main(args: Array<String>) {
    // 检查是否显式指定无特效模式
    val isNoEffectMode = args.contains("--no-effect")

    if (isNoEffectMode) {
        // 显式禁用特效，强制使用 Software
        RenderState.useSoftwareOpaque("用户通过 --no-effect 显式禁用 GUI 特效", forcedByUser = true)
        println("[启动模式] 已启用 --no-effect 参数，强制使用软件渲染模式（无特效）")
    } else {
        val checkResult = NeoLinkPreFlightChecker.runFullCheck()

        if (checkResult.allowsAcrylicDirectX) {
            RenderState.useDirectXAcrylic(checkResult.description)
        } else {
            RenderState.useSoftwareOpaque(checkResult.description)
        }
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
                if (!RenderState.isOpaqueFallback) {
                    WindowsEffects.markEffectUnavailable()
                    RenderState.disableEffectsForCurrentProcess("Skiko 渲染异常，当前进程关闭透明背板")
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
                // [优化点]：确保 ViewModel 初始化具备容错性
                // 节点拉取失败或缓存缺失时，ViewModel 应保持界面可用。
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
