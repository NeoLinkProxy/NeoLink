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

    fun getRealBuildNumber(): Int {
        return try {
            val ntdll = NativeLibrary.getInstance("ntdll")
            val rtlGetVersion = ntdll.getFunction("RtlGetVersion")
            val mem = Memory(284).apply { clear(); setInt(0, 284) }
            val result = rtlGetVersion.invokeInt(arrayOf(mem))
            if (result == 0) {
                val build = mem.getInt(12)
                println("[WindowsEffects] DEBUG: 探测到真实 Build 号 = $build")
                build
            } else {
                println("[WindowsEffects] DEBUG: ntdll 调用返回非零值: $result")
                0
            }
        } catch (e: Exception) {
            println("[WindowsEffects] DEBUG: Build 探测崩溃: ${e.message}")
            0
        }
    }

    fun applyAcrylicIfPossible(window: Window) {
        println("\n>>>>>> NeoLink 视觉效果全链路自检 >>>>>>")

        val build = getRealBuildNumber()
        val renderApi = System.getProperty("skiko.renderApi")?.uppercase()

        println("[WindowsEffects] DEBUG: 系统 Build = $build")
        println("[WindowsEffects] DEBUG: 渲染引擎 (Skiko) = $renderApi")

        // 1. 拦截检查
        if (build < 22621) {
            println("[WindowsEffects] DEBUG: 拦截 -> 系统版本过低 (低于 Win11 22H2)")
            isEffectApplied = false
            return
        }

        if (renderApi == "SOFTWARE") {
            println("[WindowsEffects] DEBUG: 拦截 -> 当前处于软件渲染模式，为保全老机器 UI，拒绝开启透明")
            isEffectApplied = false
            return
        }

        try {
            val hwndPtr = Native.getWindowPointer(window)
            if (hwndPtr == null) {
                println("[WindowsEffects] DEBUG: 错误 -> 无法获取窗口句柄 HWND")
                return
            }
            val hwnd = WinDef.HWND(hwndPtr)
            val dwm = Native.load("dwmapi", DwmLib::class.java)

            // 2. 尝试设置深色模式
            val hrDark = dwm.DwmSetWindowAttribute(hwnd, 20, IntByReference(1).pointer, 4)
            println("[WindowsEffects] DEBUG: 深色模式设置 (Attr 20) HRESULT = ${hrDark.toInt()}")

            // 3. 尝试设置圆角
            val hrCorner = dwm.DwmSetWindowAttribute(hwnd, 33, IntByReference(2).pointer, 4)
            println("[WindowsEffects] DEBUG: 圆角裁剪设置 (Attr 33) HRESULT = ${hrCorner.toInt()}")

            // 4. 尝试应用亚克力 (高风险操作)
            val hrAcrylic = dwm.DwmSetWindowAttribute(hwnd, 38, IntByReference(3).pointer, 4)
            println("[WindowsEffects] DEBUG: 亚克力设置 (Attr 38) HRESULT = ${hrAcrylic.toInt()}")

            // 5. 结论判定
            // 只要 hrAcrylic 为 0，说明 Windows 已经成功在 DWM 层开启了亚克力
            isEffectApplied = (hrAcrylic.toInt() == 0)

            println("[WindowsEffects] DEBUG: 最终 isEffectApplied 状态 = $isEffectApplied")

        } catch (e: Throwable) {
            println("[WindowsEffects] DEBUG: 严重异常 -> JNA 崩溃: ${e.message}")
            e.printStackTrace()
            isEffectApplied = false
        }
        println("<<<<<< 自检结束 <<<<<<\n")
    }

    interface DwmLib : com.sun.jna.Library {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, dwAttribute: Int, pvAttribute: Pointer, cbAttribute: Int): WinNT.HRESULT
    }
}