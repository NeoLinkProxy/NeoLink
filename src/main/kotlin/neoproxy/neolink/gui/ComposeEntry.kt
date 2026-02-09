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
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.delay
import java.awt.Dimension
import java.io.PrintStream
import java.util.*
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource

/**
 * 全局渲染状态
 */
object RenderState {
    var isSoftwareFallback by mutableStateOf(false)
}

/**
 * 基于功能的硬件探测器
 */
object DirectXProber {
    interface D3D12Lib : com.sun.jna.Library {
        fun D3D12CreateDevice(
            pAdapter: Pointer?,
            minLevel: Int,
            riid: Pointer?,
            ppDev: PointerByReference?
        ): WinNT.HRESULT
    }

    fun canSupportDX12(): Boolean {
        return try {
            val d3d12 = Native.load("d3d12", D3D12Lib::class.java)
            // 尝试创建 Level 11_0 级别的设备
            val hr = d3d12.D3D12CreateDevice(null, 0xb000, null, null)
            hr.toInt() >= 0
        } catch (e: Throwable) {
            false
        }
    }
}

fun main(args: Array<String>) {
    // 1. 硬件探测：不看型号，只看 DX12 能不能跑通
    val isCapable = DirectXProber.canSupportDX12()
    if (!isCapable) {
        println("[启动] 探测到显卡能力不足，强制进入软件渲染模式。")
        System.setProperty("skiko.renderApi", "SOFTWARE")
        RenderState.isSoftwareFallback = true
    } else {
        println("[启动] 硬件支持 DirectX 12，开启高性能模式。")
        System.setProperty("skiko.renderApi", "DIRECTX")
    }

    // 2. 日志劫持 (二道防线)
    val originalErr = System.err
    System.setErr(object : PrintStream(originalErr) {
        override fun write(buf: ByteArray, off: Int, len: Int) {
            val msg = String(buf, off, len)
            if (msg.contains("RenderException") || msg.contains("DirectX12") || msg.contains("Failed to choose")) {
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

        Window(
            onCloseRequest = { viewModel.stopService(); exitApplication() },
            state = windowState,
            title = "NeoLink 内网穿透客户端",
            icon = appIcon,
            undecorated = true,
            transparent = true,
            resizable = true
        ) {
            window.minimumSize = Dimension(720, 480)

            // 初始背景：降级用实体色，正常用全透
            window.background = if (RenderState.isSoftwareFallback) {
                java.awt.Color(18, 18, 20)
            } else {
                java.awt.Color(0, 0, 0, 0)
            }

            // 监听降级：如果运行中崩溃，立即实体化背景
            LaunchedEffect(RenderState.isSoftwareFallback) {
                if (RenderState.isSoftwareFallback) {
                    window.background = java.awt.Color(18, 18, 20)
                    window.revalidate()
                    window.repaint()
                }
            }

            LaunchedEffect(Unit) {
                // 1. 先启动业务逻辑
                viewModel.initialize(args)

                // 2. 【核心修复】延迟注入亚克力
                // 在 RTX 5080 上，必须等 Skiko 的 DirectX 上下文彻底创建完（约 500ms）再注入特效
                if (!RenderState.isSoftwareFallback) {
                    delay(500)
                    WindowsEffects.applyAcrylicIfPossible(window)

                    // 3. 【双重保险】如果注入失败或窗口刷新了，2秒后再确认一次
                    delay(1500)
                    if (!WindowsEffects.isEffectApplied) {
                        WindowsEffects.applyAcrylicIfPossible(window)
                    }
                }
            }

            neoLinkMainScreen(
                windowState = windowState,
                viewModel = viewModel,
                appIcon = appIcon,
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