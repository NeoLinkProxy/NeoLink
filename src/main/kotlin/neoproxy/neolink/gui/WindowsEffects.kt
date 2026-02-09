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