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

    /**
     * 终极 Build 号探测：直接读取 ntdll 内存
     * 解决 Kotlin Structure 初始化报错和 Java 属性返回 10.0 的问题
     */
    private fun getRealBuildNumber(): Int {
        return try {
            val ntdll = NativeLibrary.getInstance("ntdll")
            val rtlGetVersion = ntdll.getFunction("RtlGetVersion")
            // OSVERSIONINFOEXW 结构体大小为 284 字节
            val mem = Memory(284)
            mem.clear()
            mem.setInt(0, 284) // dwOSVersionInfoSize

            val result = rtlGetVersion.invokeInt(arrayOf(mem))
            if (result == 0) {
                val build = mem.getInt(12) // dwBuildNumber 在结构体中的偏移量
                println("[WindowsEffects] 探测到真实系统 Build: $build")
                build
            } else 0
        } catch (e: Exception) {
            println("[WindowsEffects] Build 探测异常: ${e.message}")
            0
        }
    }

    fun applyAcrylicIfPossible(window: Window) {
        println("\n==== NeoLink 视觉效果全链路调试 ====")

        val build = getRealBuildNumber()
        val renderApi = System.getProperty("skiko.renderApi")?.uppercase()
        println("[WindowsEffects] 当前渲染引擎: $renderApi")

        // 1. 系统版本拦截 (Win11 22H2 Build 22621)
        if (build < 22621) {
            println("[WindowsEffects] 拦截: 系统 Build $build 过低，不支持亚克力。")
            isEffectApplied = false
            return
        }

        // 2. 硬件加速拦截 (专门对付 HD3000/4000)
        // 如果渲染器明确变成了 SOFTWARE，说明显卡不支持硬件加速，坚决不开启
        if (renderApi == "SOFTWARE") {
            println("[WindowsEffects] 拦截: 显卡工作在软件渲染模式，已禁用特效。")
            isEffectApplied = false
            return
        }

        try {
            val hwndPtr = Native.getWindowPointer(window) ?: return
            val hwnd = WinDef.HWND(hwndPtr)
            val dwm = Native.load("dwmapi", DwmLib::class.java)

            // 设置深色模式
            dwm.DwmSetWindowAttribute(hwnd, 20, IntByReference(1).pointer, 4)
            // 设置物理圆角
            dwm.DwmSetWindowAttribute(hwnd, 33, IntByReference(2).pointer, 4)

            // 应用亚克力 (属性 38)
            val hr = dwm.DwmSetWindowAttribute(hwnd, 38, IntByReference(3).pointer, 4)

            // 只要 API 返回 0，且不是确定的软件渲染，我们就认为 RTX 显卡已经成功激活
            isEffectApplied = (hr.toInt() == 0)

            println("[WindowsEffects] DWM 调用结果 HRESULT: ${hr.toInt()}")
            println("[WindowsEffects] 结论: ${if (isEffectApplied) "亚克力已激活" else "应用失败"}")

        } catch (e: Throwable) {
            println("[WindowsEffects] JNA 调用异常: ${e.message}")
            isEffectApplied = false
        }
        println("==== 调试结束 ====\n")
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