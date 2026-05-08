package neoproxy.neolink.gui

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

object ModernTheme {
    val background: Color
        get() = if (RenderState.isOpaqueFallback) {
            Color(0xFF121214)
        } else if (WindowsEffects.isEffectApplied) {
            Color(0xCC121214)
        } else {
            Color(0xFF121214)
        }

    val surface: Color
        get() = if (RenderState.isOpaqueFallback || !WindowsEffects.isEffectApplied) {
            Color(0xFF1E1E20)
        } else {
            Color(0xCC1E1E20)
        }

    val sidebar: Color
        get() = if (RenderState.isOpaqueFallback || !WindowsEffects.isEffectApplied) {
            Color(0xFF1E1E20)
        } else {
            Color(0xCC1E1E20)
        }
    val surfaceHover = Color(0xFF252528)
    val border = Color(0xFF2C2C2E)
    val primary = Color(0xFF3B82F6)
    val textPrimary = Color(0xFFE4E4E7)
    val textSecondary = Color(0xFFA1A1AA)
    val success = Color(0xFF10B981)
    val warning = Color(0xFFFACC15)
    val error = Color(0xFFEF4444)
    val inputBackground = Color(0xFF18181B)
    val terminalBg = Color(0xFF0F0F10)
    val divider = Color(0xFF27272A)

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
                onDismissRequest = { state.status = ContextMenuState.Status.Closed }
            ) {
                Surface(
                    shape = ModernTheme.shapeMedium,
                    color = Color(0xFF1E1E20),
                    elevation = 8.dp,
                    border = BorderStroke(1.dp, ModernTheme.border),
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
                                Text(item.label, color = ModernTheme.textPrimary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
