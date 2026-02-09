package neoproxy.neolink.gui

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.PointerByReference

object DirectXChecker {
    /**
     * 探测系统是否真的支持 DirectX 12
     * 原理：尝试加载 d3d12.dll 并调用 D3D12CreateDevice。
     * 如果硬件太老或驱动不支持，此调用会直接返回非零错误码。
     */
    fun isDirectX12Supported(): Boolean {
        return try {
            val d3d12 = Native.load("d3d12", D3D12Lib::class.java)
            // 尝试创建一个虚拟设备，不传具体的适配器（null）
            // 0x12100 (D3D_FEATURE_LEVEL_11_0) 是 DX12 的最低运行要求
            val hr = d3d12.D3D12CreateDevice(null, 0x12100, WinNT.HANDLEByReference().pointer, PointerByReference())
            val success = hr.toInt() == 0
            println("[硬件探测] DirectX 12 核心组件调用测试: ${if (success) "通过" else "拒绝 (HRESULT=${hr.toInt()})"}")
            success
        } catch (e: Throwable) {
            println("[硬件探测] 无法加载 d3d12.dll 或调用失败，判定为老旧硬件: ${e.message}")
            false
        }
    }

    interface D3D12Lib : com.sun.jna.Library {
        // HRESULT D3D12CreateDevice(IUnknown *pAdapter, D3D_FEATURE_LEVEL MinimumFeatureLevel, REFIID riid, void **ppDevice)
        fun D3D12CreateDevice(
            pAdapter: com.sun.jna.Pointer?,
            minimumLevel: Int,
            riid: com.sun.jna.Pointer?,
            ppDevice: PointerByReference?
        ): WinNT.HRESULT
    }
}