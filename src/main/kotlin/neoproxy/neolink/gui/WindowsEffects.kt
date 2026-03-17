package neoproxy.neolink.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import java.awt.Window

/**
 * Windows 特效管理器
 *
 * 核心职责：
 * 1. 在 Windows 系统上应用 DWM 亚克力/云母特效
 * 2. 设置窗口为深色模式
 * 3. 根据预检结果决定是否应用特效
 *
 * 设计特点：
 * - 使用 JNA 调用 Windows DWM API
 * - 支持深色模式基底设置
 * - 自动检测软件回退模式，避免无效操作
 *
 * 特效类型：
 * - 亚克力 (Acrylic): 半透明模糊背景
 * - 云母 (Mica): 基于桌面背景的材质效果
 *
 * @author NeoProxy Team
 * @since 5.11.0
 */
object WindowsEffects {
    var isEffectApplied by mutableStateOf(false)
        private set

    fun applyAcrylicIfPossible(window: Window) {
        // 如果预检结果已经判定为软件降级，这里直接跳过
        if (RenderState.isSoftwareFallback) return

        try {
            val hwndPtr = Native.getWindowPointer(window) ?: return
            val hwnd = WinDef.HWND(hwndPtr)

            // 设置 Java AWT 窗口背景透明
            window.background = java.awt.Color(0, 0, 0, 0)

            val dwm = Native.load("dwmapi", DwmLib::class.java)

            // 1. 设置深色模式基底 (Attr 20)
            dwm.DwmSetWindowAttribute(hwnd, 20, IntByReference(1).pointer, 4)
            // 2. 设置圆角 (Attr 33)
            dwm.DwmSetWindowAttribute(hwnd, 33, IntByReference(2).pointer, 4)
            // 3. 申请亚克力材质 (Attr 38)
            val hr = dwm.DwmSetWindowAttribute(hwnd, 38, IntByReference(3).pointer, 4)

            if (hr.toInt() == 0) {
                isEffectApplied = true
                window.repaint()
                println("[视觉注入] 亚克力特效已激活。")
            }
        } catch (e: Throwable) {
            isEffectApplied = false
        }
    }

    interface DwmLib : com.sun.jna.Library {
        fun DwmSetWindowAttribute(
            hwnd: WinDef.HWND,
            dwAttribute: Int,
            pvAttribute: Pointer,
            cbAttribute: Int
        ): WinNT.HRESULT
    }
}