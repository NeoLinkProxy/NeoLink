package neoproxy.neolink.gui.platform
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import neoproxy.neolink.state.RuntimeState
import neoproxy.neolink.util.LogSink
import java.awt.Window

/**
 * Windows 特效管理器
 *
 * 核心职责：
 * 1. 在 Windows 系统上应用 DWM 亚克力特效
 * 2. 设置窗口为深色模式
 * 3. 根据预检和运行时状态决定是否应用特效
 *
 * 设计特点：
 * - 使用 JNA 调用 Windows DWM 接口
 * - 支持深色模式基底设置
 * - 自动检测不透明安全态，避免无效操作
 *
 * 特效类型：
 * - 亚克力：半透明模糊背景
 *
 * @author NeoProxy Team
 * @since 5.11.0
 */
object WindowsEffects {
    var isEffectApplied by mutableStateOf(false)
        private set

    fun markEffectUnavailable() {
        isEffectApplied = false
    }

    fun applyAcrylicIfPossible(window: Window): Boolean {
        // 如果预检或运行时状态已经进入不透明安全态，这里直接跳过
        if (!RenderState.canApplyDwmEffects) {
            isEffectApplied = false
            return false
        }

        try {
            val hwndPtr = Native.getWindowPointer(window)
                ?: return disableEffects(window, "无法获取真实窗口句柄")
            val hwnd = WinDef.HWND(hwndPtr)

            // 设置 Java AWT 窗口背景透明
            window.background = java.awt.Color(0, 0, 0, 0)

            val dwm = WindowsDwm.load()

            // 1. 设置深色模式基底 (Attr 20)
            WindowsDwm.setIntAttribute(
                dwm,
                hwnd,
                WindowsDwm.DWMWA_USE_IMMERSIVE_DARK_MODE,
                WindowsDwm.TRUE
            )
            // 2. 设置圆角 (Attr 33)
            WindowsDwm.setIntAttribute(
                dwm,
                hwnd,
                WindowsDwm.DWMWA_WINDOW_CORNER_PREFERENCE,
                WindowsDwm.DWMWCP_ROUND
            )
            // 3. 申请亚克力材质 (Attr 38)
            val hr = WindowsDwm.setIntAttribute(
                dwm,
                hwnd,
                WindowsDwm.DWMWA_SYSTEMBACKDROP_TYPE,
                WindowsDwm.DWMSBT_TRANSIENTWINDOW
            )

            if (WindowsDwm.succeeded(hr)) {
                isEffectApplied = true
                window.repaint()
                logUi("亚克力特效已激活。")
                return true
            }

            return disableEffects(window, "真实窗口拒绝亚克力背板 (HRESULT ${hr.toInt()})")
        } catch (e: Throwable) {
            return disableEffects(window, "真实窗口注入异常: ${e.message ?: e.javaClass.name}")
        }
    }

    private fun disableEffects(window: Window, reason: String): Boolean {
        isEffectApplied = false
        RenderState.disableEffectsForCurrentProcess(reason)
        window.background = java.awt.Color(18, 18, 20)
        window.revalidate()
        window.repaint()
        logUi("亚克力特效未启用，已切换不透明安全态：$reason")
        return false
    }

    private fun logUi(message: String) {
        RuntimeState.logSink()?.log(LogSink.Level.INFO, "UI", message)
            ?: System.out.println("[UI] $message")
    }
}
