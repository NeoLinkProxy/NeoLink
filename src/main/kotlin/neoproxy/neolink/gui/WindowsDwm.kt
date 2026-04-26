package neoproxy.neolink.gui

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference

/**
 * Windows DWM API 的最小封装。
 *
 * DWM 属性值跨预检和真实窗口注入复用，集中定义可以避免注释、调用参数和
 * Windows SDK 语义漂移。这里仍保留 Int 常量，是因为 JNA 直接调用 Win32 API
 * 时需要传递原始 DWMWINDOWATTRIBUTE / enum 值。
 */
object WindowsDwm {
    const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    const val DWMWA_SYSTEMBACKDROP_TYPE = 38

    const val TRUE = 1

    const val DWMWCP_ROUND = 2

    /*
     * DWM_SYSTEMBACKDROP_TYPE.DWMSBT_TRANSIENTWINDOW。
     * Windows 11 当前会把它表现为 Desktop Acrylic，但代码按官方语义命名，
     * 防止未来维护者把「表现效果」误当作稳定 ABI 名称。
     */
    const val DWMSBT_TRANSIENTWINDOW = 3

    fun load(): DwmLib = Native.load("dwmapi", DwmLib::class.java)

    fun setIntAttribute(
        dwm: DwmLib,
        hwnd: WinDef.HWND,
        attribute: Int,
        value: Int
    ): WinNT.HRESULT = dwm.DwmSetWindowAttribute(
        hwnd,
        attribute,
        IntByReference(value).pointer,
        Int.SIZE_BYTES
    )

    fun succeeded(hr: WinNT.HRESULT): Boolean = hr.toInt() == 0

    interface DwmLib : Library {
        fun DwmSetWindowAttribute(
            hwnd: WinDef.HWND,
            dwAttribute: Int,
            pvAttribute: Pointer,
            cbAttribute: Int
        ): WinNT.HRESULT
    }
}
