package neoproxy.neolink.gui

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef

/**
 * NeoLink 全链路环境预检器
 *
 * 核心职责：
 * 1. 检测系统是否接受 DWM（Desktop Window Manager）透明背板请求
 * 2. 用实测结果决定是否允许 DirectX + 透明亚克力组合
 * 3. 防止在 RDP、旧版 Windows 或基础显卡驱动环境下出现透明窗口风险
 *
 * 检测逻辑：
 * 1. 不再检测硬件名称或 ID（避免误判）
 * 2. 直接创建不可见临时窗口，实测 DWM 是否接受亚克力 (Attr 38) 指令
 * 3. 如果实测返回 0 (成功)，说明当前系统至少接受目标 DWM 背板属性
 *
 * 检测结果：
 * - allowsAcrylicDirectX = true: 允许使用 DirectX + 透明亚克力窗口
 * - allowsAcrylicDirectX = false: 使用 Software + 不透明窗口
 *
 * @author NeoProxy Team
 * @since 5.11.0
 */
object NeoLinkPreFlightChecker {

    data class CheckResult(
        val allowsAcrylicDirectX: Boolean,
        val description: String
    )

    fun runFullCheck(): CheckResult {
        println(">>>>>> [起飞预检] 正在进行 DWM 渲染能力实测 >>>>>>")

        // 核心：创建一个 0x0 像素的不可见测试窗口
        var hwnd: WinDef.HWND? = null
        return try {
            val user32 = User32.INSTANCE
            val dwm = WindowsDwm.load()

            hwnd = user32.CreateWindowEx(
                0, "Static", "NeoLink_Probe",
                0, 0, 0, 0, 0,
                null, null, null, null
            )

            val probeHwnd = hwnd
            if (probeHwnd == null) {
                // 如果创建窗口都失败了，保险起见走软件模式
                CheckResult(false, "无法创建测试窗口")
            } else {
                // 实测申请 Desktop Acrylic 对应的系统背板。只要 DWM 拒绝，就走不透明软件模式。
                val hrAcrylic = WindowsDwm.setIntAttribute(
                    dwm,
                    probeHwnd,
                    WindowsDwm.DWMWA_SYSTEMBACKDROP_TYPE,
                    WindowsDwm.DWMSBT_TRANSIENTWINDOW
                )
                val res = hrAcrylic.toInt()

                if (WindowsDwm.succeeded(hrAcrylic)) {
                    println("[预检] DWM 属性测试成功 (系统支持透明合成背板)")
                    CheckResult(true, "DWM 实测通过")
                } else {
                    println("[预检] DWM 属性测试拒绝: HRESULT $res (可能是 RDP、旧版 Windows 或基础显卡驱动)")
                    CheckResult(false, "DWM 拒绝特效请求 (HRESULT $res)")
                }
            }
        } catch (e: Throwable) {
            println("[预检] 实测过程中发生异常: ${e.message}")
            CheckResult(false, "DWM 实测异常")
        } finally {
            val createdWindow = hwnd
            if (createdWindow != null) {
                try {
                    User32.INSTANCE.DestroyWindow(createdWindow)
                } catch (_: Throwable) {
                    // 预检失败时也不能让清理异常覆盖真正的降级原因。
                }
            }
        }
    }
}
