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
        println("\n==== NeoLink 视觉效果全链路调试 ====")

        // 1. 核心防护：检测 Skiko 渲染引擎
        // 这是为了保护 HD 4000 等老旧显卡。如果报错回退到 SOFTWARE，直接放弃，防止隐身。
        val renderApi = System.getProperty("skiko.renderApi")?.uppercase()
        println("[DEBUG] 当前渲染引擎: $renderApi")

        if (renderApi == "SOFTWARE") {
            println("[DEBUG] 结果: 检测到软件渲染模式，为保全界面可见性，已自动禁用亚克力。")
            isEffectApplied = false
            return
        }

        try {
            val hwndPtr = Native.getWindowPointer(window)
            if (hwndPtr == null) {
                println("[DEBUG] 错误: 窗口句柄获取失败")
                return
            }
            val hwnd = WinDef.HWND(hwndPtr)
            val dwm = Native.load("dwmapi", DwmLib::class.java)

            // 2. 沉浸式深色模式 (Attribute 20)
            dwm.DwmSetWindowAttribute(hwnd, 20, IntByReference(1).pointer, 4)

            // 3. 物理圆角裁剪 (Attribute 33)
            dwm.DwmSetWindowAttribute(hwnd, 33, IntByReference(2).pointer, 4)

            // 4. 关键：尝试开启亚克力 (Attribute 38)
            // 在 RTX 5080 + Win11 上，这个调用会成功返回 0
            // 在不支持该属性的系统上，它会返回错误码
            val hr = dwm.DwmSetWindowAttribute(hwnd, 38, IntByReference(3).pointer, 4)
            println("[DEBUG] DWM 亚克力设置 HRESULT: ${hr.toInt()}")

            // 只要 API 返回 0，说明系统层面已经接受并开启了亚克力
            isEffectApplied = (hr.toInt() == 0)

            println("[DEBUG] 结论: ${if (isEffectApplied) "亚克力已激活！" else "系统拒绝了亚克力请求。"}")

        } catch (e: Throwable) {
            println("[DEBUG] 严重错误: JNA 调用过程崩溃")
            e.printStackTrace()
            isEffectApplied = false
        }
        println("==== 视觉效果调试结束 ====\n")
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