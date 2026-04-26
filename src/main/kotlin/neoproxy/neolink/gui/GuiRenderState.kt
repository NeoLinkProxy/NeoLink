package neoproxy.neolink.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * GUI 渲染决策的单一状态源。
 *
 * 这里刻意把「渲染后端」和「窗口背板」拆开：亚克力不可用并不总是等价于
 * 当前进程已经成功切到了 SOFTWARE。状态建模必须反映真实约束，否则 UI 层会把
 * 一个半透明窗口误认为已经安全降级。
 */
object RenderState {
    var decision by mutableStateOf(GuiRenderDecision.directXAcrylic("尚未完成 GUI 渲染预检"))
        private set

    val isSoftwareFallback: Boolean
        get() = decision.backend == GuiRenderBackend.SOFTWARE

    val isOpaqueFallback: Boolean
        get() = decision.backdrop == GuiWindowBackdrop.OPAQUE

    val shouldUseTransparentWindow: Boolean
        get() = decision.backdrop == GuiWindowBackdrop.ACRYLIC

    val canApplyDwmEffects: Boolean
        get() = decision.backdrop == GuiWindowBackdrop.ACRYLIC

    val fallbackReason: String
        get() = decision.reason

    fun useDirectXAcrylic(reason: String) {
        decision = GuiRenderDecision.directXAcrylic(reason)
        System.setProperty("skiko.renderApi", GuiRenderBackend.DIRECTX.skikoValue)
    }

    fun useSoftwareOpaque(reason: String, forcedByUser: Boolean = false) {
        decision = GuiRenderDecision.softwareOpaque(reason, forcedByUser)
        System.setProperty("skiko.renderApi", GuiRenderBackend.SOFTWARE.skikoValue)
    }

    fun disableEffectsForCurrentProcess(reason: String) {
        if (decision.backdrop == GuiWindowBackdrop.OPAQUE) return

        /*
         * 不在这里宣称当前 Skiko Canvas 已经换成 SOFTWARE。窗口创建后再改
         * skiko.renderApi 不一定能影响已初始化的渲染后端；但我们可以立即关闭
         * 透明背板，让 UI 进入不透明安全态。
         */
        decision = decision.copy(
            backdrop = GuiWindowBackdrop.OPAQUE,
            reason = reason
        )
    }
}

enum class GuiRenderBackend(val skikoValue: String) {
    DIRECTX("DIRECTX"),
    SOFTWARE("SOFTWARE")
}

enum class GuiWindowBackdrop {
    ACRYLIC,
    OPAQUE
}

data class GuiRenderDecision(
    val backend: GuiRenderBackend,
    val backdrop: GuiWindowBackdrop,
    val reason: String,
    val forcedByUser: Boolean = false
) {
    companion object {
        fun directXAcrylic(reason: String) = GuiRenderDecision(
            backend = GuiRenderBackend.DIRECTX,
            backdrop = GuiWindowBackdrop.ACRYLIC,
            reason = reason
        )

        fun softwareOpaque(reason: String, forcedByUser: Boolean = false) = GuiRenderDecision(
            backend = GuiRenderBackend.SOFTWARE,
            backdrop = GuiWindowBackdrop.OPAQUE,
            reason = reason,
            forcedByUser = forcedByUser
        )
    }
}
