package neoproxy.neolink.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import java.awt.Window

object WindowsEffects {
    var isEffectApplied by mutableStateOf(false)
        private set

    fun disableEffects() {
        isEffectApplied = false
    }

    /**
     * 获取真实 Build 号仅用于辅助排错日志
     */
    fun getRealBuildNumber(): Int {
        return try {
            val ntdll = NativeLibrary.getInstance("ntdll")
            val rtlGetVersion = ntdll.getFunction("RtlGetVersion")
            val mem = Memory(284).apply { clear(); setInt(0, 284) }
            val result = rtlGetVersion.invokeInt(arrayOf(mem))
            if (result == 0) mem.getInt(12) else 0
        } catch (e: Exception) {
            0
        }
    }

    fun applyAcrylicIfPossible(window: Window) {
        if (RenderState.isSoftwareFallback) {
            println(">>> [视觉自检] 检测到处于软件渲染模式，跳过亚克力申请。")
            return
        }

        println("\n>>>>>> [核心排查] 视觉特效全链路自检 >>>>>>")

        try {
            val hwndPtr = Native.getWindowPointer(window)
            if (hwndPtr == null) {
                println("[自检] 错误：无法获取 HWND 句柄")
                return
            }
            val hwnd = WinDef.HWND(hwndPtr)

            // 【补救措施】在申请 DWM 属性前，再次确保 Java 窗口背景是透明的
            // 防止 Skiko 在启动过程中重置了 AWT 窗口的透明状态
            window.background = java.awt.Color(0, 0, 0, 0)

            val dwm = Native.load("dwmapi", DwmLib::class.java)

            // 1. 设置深色模式基底 (Attr 20) - 决定亚克力是黑色还是白色
            dwm.DwmSetWindowAttribute(hwnd, 20, IntByReference(1).pointer, 4)

            // 2. 设置圆角 (Attr 33)
            dwm.DwmSetWindowAttribute(hwnd, 33, IntByReference(2).pointer, 4)

            // 3. 申请亚克力材质 (Attr 38)
            val hrAcrylic = dwm.DwmSetWindowAttribute(hwnd, 38, IntByReference(3).pointer, 4)
            val res = hrAcrylic.toInt()

            if (res == 0) {
                isEffectApplied = true
                // 【核心修复】成功后强制重绘窗口，迫使 DWM 重新计算背景模糊
                window.repaint()
                println("[自检] 亚克力特效已成功注入并激活。")
            } else {
                println("[自检] 硬件或驱动拒绝亚克力请求 (HRESULT: $res)")
                isEffectApplied = false
            }

        } catch (e: Throwable) {
            println("[自检] 致命异常: ${e.message}")
            isEffectApplied = false
        }
        println(">>>>>> [核心排查] 自检流程结束 >>>>>>\n")
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