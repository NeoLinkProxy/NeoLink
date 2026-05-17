package neoproxy.neolink.gui.ui.screens
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import neoproxy.neolink.gui.platform.RenderState
import neoproxy.neolink.gui.state.NeoLinkViewModel
import neoproxy.neolink.gui.ui.components.collectIsHoveredAsState
import neoproxy.neolink.gui.ui.components.modalInputBarrier
import neoproxy.neolink.gui.ui.components.primaryButton
import neoproxy.neolink.gui.ui.theme.ModernContextMenuRepresentation
import neoproxy.neolink.gui.ui.theme.ModernTheme
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

@Composable
fun WindowScope.neoLinkMainScreen(
    windowState: WindowState,
    viewModel: NeoLinkViewModel,
    appIcon: Painter,
    onExit: () -> Unit
) {
    val customTextSelectionColors = TextSelectionColors(
        handleColor = ModernTheme.primary,
        backgroundColor = ModernTheme.primary.copy(alpha = 0.35f)
    )
    var alertMessage by remember { mutableStateOf<String?>(null) }
    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val currentShape = if (isMaximized || RenderState.isOpaqueFallback) RectangleShape else ModernTheme.shapeWindow

    MaterialTheme(
        colors = darkColors(
            background = Color.Transparent,
            surface = ModernTheme.surface,
            primary = ModernTheme.primary,
            onBackground = ModernTheme.textPrimary,
            onSurface = ModernTheme.textPrimary
        )
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
            Box(modifier = Modifier.fillMaxSize().clip(currentShape)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    shape = currentShape,
                    border = if (!isMaximized && !RenderState.isOpaqueFallback) BorderStroke(1.dp, ModernTheme.borderSoft) else null
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(ModernTheme.backgroundBrush)) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            customTitleBar(windowState, appIcon, onExit)
                            if (viewModel.authState.isAccountLocked) {
                                accountLockedScreen(viewModel)
                            } else if (!viewModel.authState.isAuthenticated) {
                                authScreen(viewModel)
                            } else {
                                workspaceScreen(viewModel, onAlert = { alertMessage = it })
                            }
                        }
                        alertMessage?.let { message ->
                            modernAlertDialog("参数验证未通过", message, onDismiss = { alertMessage = null })
                        }
                        if (viewModel.authState.isAuthenticated && !viewModel.authState.isAccountLocked) {
                            nasGlobalDialogs(viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun modernAlertDialog(title: String, message: String, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .modalInputBarrier()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(360.dp).background(ModernTheme.panelBrush, ModernTheme.shapeMedium)
                .border(1.dp, ModernTheme.borderStrong, ModernTheme.shapeMedium).padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = ModernTheme.error, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    color = ModernTheme.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(message, color = ModernTheme.textSecondary, fontSize = 13.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(24.dp))
            primaryButton("返回修改", false, onClick = onDismiss)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WindowScope.customTitleBar(windowState: WindowState, appIcon: Painter, onExit: () -> Unit) {
    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val toggleMaximize = {
        windowState.placement = if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
    }
    DisposableEffect(window, isMaximized) {
        val listener = object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (
                    event.button == MouseEvent.BUTTON1 &&
                    event.clickCount == 2 &&
                    event.y in 0 until TitleBarHeightPx &&
                    event.x < window.width - WindowControlButtonsWidthPx
                ) {
                    toggleMaximize()
                }
            }
        }

        window.addMouseListener(listener)
        onDispose { window.removeMouseListener(listener) }
    }

    Box(Modifier.fillMaxWidth().height(32.dp).background(Color.Transparent)) {
        WindowDraggableArea(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = WindowControlButtonWidth * WindowControlButtonCount)
        ) {
            Row(Modifier.fillMaxSize().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(appIcon, "Logo", Modifier.size(23.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(10.dp))
                Text(
                    "NeoLink 内网穿透客户端",
                    color = ModernTheme.textSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier
                )
            }
        }
        Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            windowControlButton(onClick = { windowState.isMinimized = true }) { color ->
                drawLine(color, Offset(18.dp.toPx(), 16.dp.toPx()), Offset(28.dp.toPx(), 16.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            windowControlButton(onClick = toggleMaximize) { color ->
                if (isMaximized) {
                    drawRect(color, Offset(20.dp.toPx(), 11.dp.toPx()), Size(9.dp.toPx(), 9.dp.toPx()), style = Stroke(1.dp.toPx()))
                    drawRect(color, Offset(17.dp.toPx(), 14.dp.toPx()), Size(9.dp.toPx(), 9.dp.toPx()), style = Stroke(1.dp.toPx()))
                } else {
                    drawRect(color, Offset(18.dp.toPx(), 12.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()), style = Stroke(1.dp.toPx()))
                }
            }
            windowControlButton(isClose = true, onClick = onExit) { color ->
                drawLine(color, Offset(18.dp.toPx(), 11.dp.toPx()), Offset(28.dp.toPx(), 21.dp.toPx()), strokeWidth = 1.dp.toPx())
                drawLine(color, Offset(18.dp.toPx(), 21.dp.toPx()), Offset(28.dp.toPx(), 11.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

private val WindowControlButtonWidth = 46.dp
private const val WindowControlButtonCount = 3
private const val TitleBarHeightPx = 32
private const val WindowControlButtonsWidthPx = 46 * WindowControlButtonCount

@Composable
fun windowControlButton(isClose: Boolean = false, onClick: () -> Unit, drawIcon: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bg = when {
        isHovered && isClose -> Color(0xFFE81123)
        isHovered -> Color(0xFF333333)
        else -> Color.Transparent
    }
    val fg = if (isHovered && isClose) Color.White else ModernTheme.textSecondary
    Box(Modifier.width(WindowControlButtonWidth).fillMaxHeight().background(bg).clickable(onClick = onClick, interactionSource = interactionSource, indication = null), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawIcon(fg) }
    }
}
