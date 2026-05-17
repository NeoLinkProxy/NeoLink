package neoproxy.neolink.gui.ui.theme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import neoproxy.neolink.gui.platform.RenderState
import neoproxy.neolink.gui.platform.WindowsEffects


object ModernTheme {
    val background: Color
        get() = if (RenderState.isOpaqueFallback) {
            Color(0xFF101115)
        } else if (WindowsEffects.isEffectApplied) {
            Color(0xCC101115)
        } else {
            Color(0xFF101115)
        }

    val surface: Color
        get() = if (RenderState.isOpaqueFallback || !WindowsEffects.isEffectApplied) {
            Color(0xFF181A20)
        } else {
            Color(0xD4181A20)
        }

    val sidebar: Color
        get() = if (RenderState.isOpaqueFallback || !WindowsEffects.isEffectApplied) {
            Color(0xFF15171D)
        } else {
            Color(0xD015171D)
        }
    val surfaceHover = Color(0xFF222632)
    val surfaceRaised = Color(0xFF20232B)
    val surfaceSunken = Color(0xFF121419)
    val border = Color(0xFF2A2F3A)
    val borderSoft = Color(0x1FFFFFFF)
    val borderStrong = Color(0xFF3A4252)
    val primary = Color(0xFF6B7280)
    val primaryDeep = Color(0xFF374151)
    val primaryGlow = Color(0xFF4B5563)
    val accent = Color(0xFF3B82F6)
    val accentLight = Color(0xFF60A5FA)
    val accentDark = Color(0xFF2563EB)
    val textPrimary = Color(0xFFE7EAF0)
    val textSecondary = Color(0xFFA6ADBB)
    val success = Color(0xFF10B981)
    val warning = Color(0xFFFACC15)
    val error = Color(0xFFEF4444)
    val inputBackground = Color(0xFF12151B)
    val terminalBg = Color(0xFF0F0F10)
    val divider = Color(0xFF242A35)

    val backgroundBrush: Color
        get() = background

    val panelBrush: Color
        get() = if (RenderState.isOpaqueFallback) {
            Color(0xFF181A20)
        } else {
            surface
        }

    val recessedBrush: Color
        get() = Color(0xFF0B0D12)

    val primaryWash: Color
        get() = Color.Transparent

    val shapeWindow = RoundedCornerShape(8.dp)
    val shapeMedium = RoundedCornerShape(10.dp)
    val shapeSmall = RoundedCornerShape(6.dp)
}

@OptIn(ExperimentalFoundationApi::class)
val ModernContextMenuRepresentation = object : ContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status
        if (status is ContextMenuState.Status.Open) {
            Popup(
                offset = IntOffset(status.rect.left.toInt(), status.rect.top.toInt()),
                onDismissRequest = { state.status = ContextMenuState.Status.Closed },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = ModernTheme.shapeMedium,
                    color = ModernTheme.surfaceRaised,
                    elevation = 8.dp,
                    border = BorderStroke(1.dp, ModernTheme.borderStrong),
                    modifier = Modifier.width(IntrinsicSize.Max)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        items().forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { item.onClick(); state.status = ContextMenuState.Status.Closed }
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.label, color = ModernTheme.textPrimary, fontSize = 13.sp, modifier = Modifier)
                            }
                        }
                    }
                }
            }
        }
    }
}
