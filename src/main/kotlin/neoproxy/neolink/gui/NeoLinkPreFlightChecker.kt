package neoproxy.neolink.gui

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference

/**
 * NeoLink 全链路环境预检器 (功能实测版)
 * 核心逻辑：
 * 1. 不再检测硬件名称或 ID。
 * 2. 直接创建一个不可见的临时窗口，实测 DWM 是否接受亚克力 (Attr 38) 指令。
 * 3. 如果实测返回 0 (成功)，说明系统具备完整的合成能力，不会出现点击穿透。
 */
object NeoLinkPreFlightChecker {

    data class CheckResult(
        val isHardwareOk: Boolean,
        val description: String
    )

    fun runFullCheck(): CheckResult {
        println(">>>>>> [起飞预检] 正在进行 DWM 渲染能力实测 >>>>>>")

        // 核心：创建一个 0x0 像素的不可见测试窗口
        return try {
            val user32 = User32.INSTANCE
            val dwm = Native.load("dwmapi", DwmLib::class.java)

            val hwnd = user32.CreateWindowEx(
                0, "Static", "NeoLink_Probe",
                0, 0, 0, 0, 0,
                null, null, null, null
            )

            if (hwnd == null) {
                // 如果创建窗口都失败了，保险起见走软件模式
                CheckResult(false, "无法创建测试窗口")
            } else {
                // 实测申请亚克力材质 (Attribute 38)
                // 3 是 DWM_SYSTEMBACKDROP_TYPE.DWM_MSBT_ACRYLIC
                val hrAcrylic = dwm.DwmSetWindowAttribute(hwnd, 38, IntByReference(3).pointer, 4)
                val res = hrAcrylic.toInt()

                // 立即销毁测试窗口
                user32.DestroyWindow(hwnd)

                if (res == 0) {
                    println("[预检] DWM 属性测试成功 ✅ (系统支持合成特效)")
                    CheckResult(true, "DWM 实测通过")
                } else {
                    println("[预检] DWM 属性测试拒绝: HRESULT $res ❌ (可能是 RDP 或基础显卡驱动)")
                    CheckResult(false, "DWM 拒绝特效请求 (HRESULT $res)")
                }
            }
        } catch (e: Throwable) {
            println("[预检] 实测过程中发生异常: ${e.message}")
            CheckResult(false, "DWM 实测异常")
        }
    }

    interface DwmLib : Library {
        fun DwmSetWindowAttribute(
            hwnd: WinDef.HWND,
            dwAttribute: Int,
            pvAttribute: Pointer,
            cbAttribute: Int
        ): WinNT.HRESULT
    }
}