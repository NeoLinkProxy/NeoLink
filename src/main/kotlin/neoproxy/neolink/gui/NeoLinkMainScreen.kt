package neoproxy.neolink.gui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * NeoLink 现代感主题配置
 * 核心逻辑：基于全链路硬件检测结果 (WindowsEffects.isEffectApplied) 动态调整 UI 表现
 */
object ModernTheme {
    // 降级模式检测：如果 RenderState.isSoftwareFallback 为 true，说明已经切换到了 SOFTWARE 渲染
    // 此时必须使用不透明背景，否则软件渲染无法正确处理 Alpha 通道
    val background: Color
        get() = if (RenderState.isSoftwareFallback) {
            Color(0xFF121214) // 100% 不透明，防止消失
        } else if (WindowsEffects.isEffectApplied) {
            Color(0xCC121214)
        } else {
            Color(0xFF121214)
        }

    val surface: Color
        get() = if (RenderState.isSoftwareFallback || !WindowsEffects.isEffectApplied)
            Color(0xFF1E1E20)
        else
            Color(0xCC1E1E20)

    val surfaceHover = Color(0xFF252528)
    val border = Color(0xFF2C2C2E)
    val primary = Color(0xFF3B82F6)
    val textPrimary = Color(0xFFE4E4E7)
    val textSecondary = Color(0xFFA1A1AA)
    val success = Color(0xFF10B981)
    val error = Color(0xFFEF4444)
    val inputBackground = Color(0xFF18181B)
    val terminalBg = Color(0xFF0F0F10)
    val divider = Color(0xFF27272A)

    val shapeWindow = RoundedCornerShape(8.dp)
    val shapeMedium = RoundedCornerShape(10.dp)
    val shapeSmall = RoundedCornerShape(6.dp)
}

/**
 * 自定义深色圆角右键菜单实现
 */
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
                            val interactionSource = remember { MutableInteractionSource() }
                            val isHovered by interactionSource.collectIsHoveredAsState()
                            val displayLabel = when (item.label) {
                                "Copy" -> "复制"
                                "Cut" -> "剪切"
                                "Paste" -> "粘贴"
                                "Select All" -> "全选"
                                else -> item.label
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = { item.onClick(); state.status = ContextMenuState.Status.Closed }
                                    )
                                    .background(if (isHovered) ModernTheme.surfaceHover else Color.Transparent)
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = displayLabel, color = ModernTheme.textPrimary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WindowScope.neoLinkMainScreen(
    windowState: WindowState,
    viewModel: NeoLinkViewModel,
    appIcon: Painter,
    onExit: () -> Unit
) {
    val customTextSelectionColors = TextSelectionColors(
        handleColor = Color(0xFFBD93F9),
        backgroundColor = ModernTheme.primary.copy(alpha = 0.5f)
    )

    var showValidationError by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf("") }
    var isCustomAddressMode by remember { mutableStateOf(false) }

    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val currentShape = if (isMaximized) RectangleShape else ModernTheme.shapeWindow

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(currentShape)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    // 这里的颜色会根据 isSoftwareFallback 自动切换
                    color = ModernTheme.background,
                    shape = currentShape,
                    border = if (!isMaximized) BorderStroke(1.dp, Color(0x1AFFFFFF)) else null
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            customTitleBar(windowState, appIcon, onExit)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 1000.dp)
                                        .align(Alignment.CenterHorizontally)
                                ) {
                                    sectionCard {
                                        connectionSection(
                                            viewModel,
                                            isCustomAddressMode,
                                            onModeChange = { isCustomAddressMode = it })
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    advancedSettingsSection(viewModel)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    logConsoleSection(viewModel)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    bottomBar(viewModel, isCustomAddressMode) { msg ->
                                        validationMessage = msg
                                        showValidationError = true
                                    }
                                }
                            }
                        }
                        if (showValidationError) {
                            modernAlertDialog(
                                title = "参数验证未通过",
                                message = validationMessage,
                                onDismiss = { showValidationError = false }
                            )
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
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(Color(0xFF1E1E20), ModernTheme.shapeMedium) // 弹窗背景保持不透明
                .border(1.dp, ModernTheme.border, ModernTheme.shapeMedium)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = ModernTheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, color = ModernTheme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = ModernTheme.textSecondary, fontSize = 13.sp, lineHeight = 20.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(38.dp),
                shape = RoundedCornerShape(19.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = ModernTheme.primary),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Text("返回修改", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun sectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(ModernTheme.surface, ModernTheme.shapeMedium)
            .border(1.dp, ModernTheme.border, ModernTheme.shapeMedium).padding(12.dp),
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WindowScope.customTitleBar(windowState: WindowState, appIcon: Painter, onExit: () -> Unit) {
    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val toggleMaximize = {
        windowState.placement = if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(Color.Transparent)
    ) {
        WindowDraggableArea(
            modifier = Modifier.fillMaxSize().combinedClickable(
                onClick = {},
                onDoubleClick = toggleMaximize,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = appIcon,
                    contentDescription = "Logo",
                    modifier = Modifier.size(23.dp).offset(y = (3).dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "NeoLink 内网穿透客户端",
                    color = ModernTheme.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            windowControlButton(onClick = { windowState.isMinimized = true }) { color ->
                drawLine(
                    color,
                    Offset(18.dp.toPx(), 16.dp.toPx()),
                    Offset(28.dp.toPx(), 16.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }

            windowControlButton(onClick = toggleMaximize) { color ->
                if (isMaximized) {
                    drawRect(
                        color,
                        topLeft = Offset(20.dp.toPx(), 12.dp.toPx()),
                        size = Size(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(1.dp.toPx())
                    )
                    drawRect(
                        ModernTheme.background, // 使用当前背景色遮挡
                        topLeft = Offset(17.dp.toPx(), 15.dp.toPx()),
                        size = Size(9.dp.toPx(), 9.dp.toPx())
                    )
                    drawRect(
                        color,
                        topLeft = Offset(18.dp.toPx(), 16.dp.toPx()),
                        size = Size(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(1.dp.toPx())
                    )
                } else {
                    drawRect(
                        color,
                        topLeft = Offset(18.dp.toPx(), 12.dp.toPx()),
                        size = Size(10.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(1.dp.toPx())
                    )
                }
            }

            windowControlButton(isClose = true, onClick = { onExit() }) { color ->
                drawLine(
                    color,
                    Offset(18.dp.toPx(), 11.dp.toPx()),
                    Offset(28.dp.toPx(), 21.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color,
                    Offset(18.dp.toPx(), 21.dp.toPx()),
                    Offset(28.dp.toPx(), 11.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

@Composable
fun windowControlButton(
    isClose: Boolean = false,
    onClick: () -> Unit,
    drawIcon: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bg = when {
        isHovered && isClose -> Color(0xFFE81123)
        isHovered -> Color(0xFF333333)
        else -> Color.Transparent
    }
    val fg = if (isHovered && isClose) Color.White else ModernTheme.textSecondary

    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(bg)
            .clickable(onClick = onClick, interactionSource = interactionSource, indication = null),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawIcon(fg) }
    }
}

@Composable
fun connectionSection(viewModel: NeoLinkViewModel, isCustomMode: Boolean, onModeChange: (Boolean) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(3.dp, 14.dp).offset(y = 2.5.dp)
                    .background(ModernTheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "连接配置",
                color = ModernTheme.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(0.618f)) {
                labelText("远程服务器地址")
                Spacer(modifier = Modifier.height(4.dp))
                nodeSelector(viewModel, isCustomMode, onModeChange)
            }
            Column(modifier = Modifier.weight(0.382f)) {
                labelText("本地端口")
                Spacer(modifier = Modifier.height(4.dp))
                modernTextField(
                    value = viewModel.localPort,
                    onValueChange = { if (it.all { c -> c.isDigit() }) viewModel.localPort = it },
                    placeholder = "8080"
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            labelText("访问密钥 (Token)")
            Spacer(modifier = Modifier.height(4.dp))
            modernTextField(
                value = viewModel.accessKey,
                onValueChange = { viewModel.accessKey = it },
                placeholder = "请输入连接密钥",
                isPassword = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun nodeSelector(viewModel: NeoLinkViewModel, isCustomMode: Boolean, onModeChange: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    if (isCustomMode) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            modernTextField(
                value = viewModel.remoteDomain,
                onValueChange = { viewModel.remoteDomain = it },
                placeholder = "输入 IP 或域名",
                modifier = Modifier.fillMaxWidth()
            )
            IconButton(onClick = { onModeChange(false) }, modifier = Modifier.size(34.dp).padding(end = 4.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "返回",
                    tint = ModernTheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(34.dp)
                    .background(ModernTheme.inputBackground, ModernTheme.shapeSmall)
                    .border(1.dp, ModernTheme.border, ModernTheme.shapeSmall).clip(ModernTheme.shapeSmall)
                    .clickable { expanded = true }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                viewModel.selectedNode?.let { node ->
                    Box(
                        modifier = Modifier.size(18.dp).offset(y = 1.dp),
                        contentAlignment = Alignment.Center
                    ) { svgIcon(node.iconSvg, size = 16.dp) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        node.name,
                        color = ModernTheme.textPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f).offset(y = (-2).dp)
                    )
                } ?: Text(
                    "选择节点",
                    color = ModernTheme.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = ModernTheme.textSecondary,
                    modifier = Modifier.rotate(rotationState)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF1E1E20)) // 下拉菜单背景保持不透明
                    .border(1.dp, ModernTheme.border, RoundedCornerShape(4.dp)).width(300.dp)
            ) {
                viewModel.nodeList.forEach { node ->
                    DropdownMenuItem(
                        onClick = { viewModel.selectNode(node); expanded = false },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(20.dp),
                                contentAlignment = Alignment.Center
                            ) { svgIcon(node.iconSvg, size = 16.dp) }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(node.name, color = ModernTheme.textPrimary, fontSize = 13.sp)
                        }
                    }
                }
                Divider(color = ModernTheme.divider, thickness = 1.dp)
                DropdownMenuItem(
                    onClick = { onModeChange(true); expanded = false },
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = ModernTheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "自定义地址...",
                            color = ModernTheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun advancedSettingsSection(viewModel: NeoLinkViewModel) {
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().background(ModernTheme.surface, ModernTheme.shapeMedium)
            .border(1.dp, ModernTheme.border, ModernTheme.shapeMedium).clip(ModernTheme.shapeMedium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ModernTheme.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "高级设置",
                color = ModernTheme.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.offset(x = (-3).dp, y = (-2).dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (isExpanded) "收起" else "展开",
                color = ModernTheme.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.offset(x = (-1).dp, y = (-2).dp)
            )
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                Divider(color = ModernTheme.divider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        labelText("本地域名"); Spacer(modifier = Modifier.height(4.dp)); modernTextField(
                        viewModel.localDomain,
                        { viewModel.localDomain = it },
                        placeholder = "localhost"
                    )
                    }
                    Column(Modifier.weight(1f)) {
                        labelText("Hook端口"); Spacer(modifier = Modifier.height(4.dp)); modernTextField(
                        viewModel.hostHookPort,
                        { viewModel.hostHookPort = it })
                    }
                    Column(Modifier.weight(1f)) {
                        labelText("连接端口"); Spacer(modifier = Modifier.height(4.dp)); modernTextField(
                        viewModel.hostConnectPort,
                        { viewModel.hostConnectPort = it })
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        labelText("传输协议"); Spacer(modifier = Modifier.height(6.dp))
                        // 🔴 实时生效：修改时同步更新 NeoLink 静态变量
                        modernCheckbox("启用 TCP", viewModel.isTcpEnabled) {
                            viewModel.isTcpEnabled = it
                            neoproxy.neolink.NeoLink.isDisableTCP = !it
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        modernCheckbox("启用 UDP", viewModel.isUdpEnabled) {
                            viewModel.isUdpEnabled = it
                            neoproxy.neolink.NeoLink.isDisableUDP = !it
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        modernCheckbox("真实IP (PPv2)", viewModel.isPpv2Enabled) {
                            viewModel.isPpv2Enabled = it
                            neoproxy.neolink.NeoLink.enableProxyProtocol = it
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        labelText("其他"); Spacer(modifier = Modifier.height(6.dp))
                        modernCheckbox("自动重连", viewModel.isAutoReconnect) {
                            viewModel.isAutoReconnect = it
                            neoproxy.neolink.NeoLink.enableAutoReconnect = it
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        modernCheckbox("调试模式", viewModel.isDebugMode) {
                            viewModel.isDebugMode = it
                            neoproxy.neolink.NeoLink.isDebugMode = it
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        modernCheckbox("显示详情", viewModel.isShowConnection) {
                            viewModel.isShowConnection = it
                            neoproxy.neolink.NeoLink.showConnection = it
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ColumnScope.logConsoleSection(viewModel: NeoLinkViewModel) {
    val statusColor by animateColorAsState(
        targetValue = if (viewModel.isRunning) ModernTheme.success else ModernTheme.error,
        animationSpec = tween(durationMillis = 500)
    )

    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(3.dp, 14.dp).offset(y = 2.5.dp)
                    .background(statusColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "运行日志",
                color = ModernTheme.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            // 提示用户可以缩放
            Text(
                "Ctrl+滚轮调节字号",
                color = ModernTheme.textSecondary.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        val listState = rememberLazyListState()

        LaunchedEffect(viewModel.logMessages.size) {
            if (viewModel.logMessages.isNotEmpty()) {
                listState.scrollToItem(viewModel.logMessages.size - 1)
            }
        }

        CompositionLocalProvider(
            LocalContextMenuRepresentation provides ModernContextMenuRepresentation,
            LocalTextSelectionColors provides LocalTextSelectionColors.current
        ) {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ModernTheme.terminalBg, ModernTheme.shapeMedium)
                        .border(1.dp, ModernTheme.border, ModernTheme.shapeSmall)
                        .clip(ModernTheme.shapeMedium)
                        // 🔴 核心功能：监听鼠标滚轮实现缩放
                        .onPointerEvent(PointerEventType.Scroll) { event ->
                            if (event.keyboardModifiers.isCtrlPressed) {
                                val delta = event.changes.first().scrollDelta.y
                                // 向上滚 delta 为负，向下滚为正
                                val newSize = if (delta < 0) {
                                    (viewModel.logFontSize.value + 1f).coerceAtMost(30f)
                                } else {
                                    (viewModel.logFontSize.value - 1f).coerceAtLeast(8f)
                                }
                                viewModel.logFontSize = newSize.sp
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        items(viewModel.logMessages) { msg ->
                            val highlightedText = remember(msg, viewModel.logFontSize) {
                                highlightLogMessage(msg)
                            }

                            androidx.compose.material.Text(
                                text = highlightedText,
                                color = ModernTheme.textPrimary,
                                // 🔴 核心功能：使用动态字号
                                fontSize = viewModel.logFontSize,
                                fontFamily = FontFamily.Monospace,
                                style = TextStyle(
                                    // 自动计算行高，保持视觉比例
                                    lineHeight = (viewModel.logFontSize.value * 1.35f).sp,
                                    letterSpacing = 0.sp
                                ),
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 完整高亮逻辑函数（包含异常红色、MB/日期蓝色、IP域名紫色、缩进修复）
 */
private fun highlightLogMessage(original: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.AnnotatedString {
    val newText = original.text.replace("\t", "    ")
    val builder = androidx.compose.ui.text.AnnotatedString.Builder(newText)

    // 保留原有 [INFO] 颜色
    val tabIndex = original.text.indexOf('\t')
    original.spanStyles.forEach { range ->
        if (tabIndex == -1 || range.end < tabIndex) {
            builder.addStyle(range.item, range.start, range.end)
        }
    }

    val colorPurple = Color(0xFFE040FB)
    val colorBlue = Color(0xFF40C4FF)
    val colorRed = Color(0xFFFF5252)

    // A. 红色：异常头部、堆栈、源码引用
    val patternExHeader = "\\b[\\w\\.]+(?:Exception|Error)(?::\\s*.*)?"
    val patternStackTrace = "\\bat\\s+[\\w\\.\\$/<> ]+(?:\\(.*?\\))?"
    val patternSourceInfo = "\\((?:Unknown Source|[\\w\\.]+\\.java:\\d+)\\)"
    val regexException = Regex("($patternExHeader|$patternStackTrace|$patternSourceInfo)")

    // B. 蓝色：MB、日期范围
    val patternMB = "\\d+(?:\\.\\d+)?\\s*MB"
    val patternDateRange = "\\d{4}/\\d{1,2}/\\d{1,2}-\\d{1,2}:\\d{2}"
    val regexBlue = Regex("($patternMB|$patternDateRange)")

    // C. 紫色：IP、域名
    val ipv6Bracketed = "\\[[a-fA-F0-9:]+\\](?::\\d+)?"
    val ipv6Raw = "(?:[a-fA-F0-9]{1,4}:){1,7}[a-fA-F0-9]{1,4}"
    val ipv4 = "\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?"
    val domain = "(?:localhost|(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,})(?::\\d+)?"
    val regexPurple = Regex("($ipv6Bracketed|$ipv6Raw|$ipv4|$domain)")
    val timePattern = Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?$")

    // 1. 红色应用
    for (match in regexException.findAll(newText)) {
        builder.addStyle(
            SpanStyle(color = colorRed, fontWeight = FontWeight.Bold),
            match.range.first,
            match.range.last + 1
        )
    }

    // 2. 蓝色应用
    for (match in regexBlue.findAll(newText)) {
        builder.addStyle(
            SpanStyle(color = colorBlue, fontWeight = FontWeight.Bold),
            match.range.first,
            match.range.last + 1
        )
    }

    // 3. 紫色应用（含避让逻辑）
    for (match in regexPurple.findAll(newText)) {
        val start = match.range.first
        if (timePattern.matches(match.value)) continue

        val lineStart = newText.lastIndexOf('\n', start).let { if (it == -1) 0 else it }
        val lineEnd = newText.indexOf('\n', start).let { if (it == -1) newText.length else it }
        val lineContent = newText.substring(lineStart, lineEnd)

        if (lineContent.contains("Exception") || lineContent.contains("Error") || lineContent.trimStart()
                .startsWith("at ")
        ) {
            continue
        }
        builder.addStyle(SpanStyle(color = colorPurple, fontWeight = FontWeight.Bold), start, match.range.last + 1)
    }

    return builder.toAnnotatedString()
}

@Composable
fun bottomBar(viewModel: NeoLinkViewModel, isCustomMode: Boolean, onValidationError: (String) -> Unit) {
    val actionBtnBgColor by animateColorAsState(
        targetValue = if (viewModel.isRunning) ModernTheme.error else ModernTheme.primary,
        animationSpec = tween(durationMillis = 500)
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (viewModel.isRunning) ModernTheme.success else ModernTheme.textSecondary,
        animationSpec = tween(durationMillis = 500)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.size(6.dp).offset(y = 2.5.dp).background(
                    indicatorColor,
                    CircleShape
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (viewModel.isRunning) "服务运行中" else "服务已停止",
                color = ModernTheme.textSecondary,
                fontSize = 12.sp,
            )
        }

        Button(
            onClick = {
                if (viewModel.isRunning) {
                    viewModel.stopService()
                } else {
                    val errors = mutableListOf<String>()
                    if (isCustomMode && viewModel.remoteDomain.isBlank()) errors.add("远程服务器地址不能为空")
                    if (!isCustomMode && viewModel.selectedNode == null) errors.add("请选择一个远程连接节点")
                    if (viewModel.localPort.isBlank()) errors.add("本地端口不能为空")
                    if (viewModel.accessKey.isBlank()) errors.add("访问密钥 (Token) 不能为空")

                    if (errors.isNotEmpty()) {
                        onValidationError(errors.joinToString("\n") { "• $it" })
                    } else {
                        viewModel.startService()
                    }
                }
            },
            shape = ModernTheme.shapeSmall,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = actionBtnBgColor,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp),
            modifier = Modifier.height(34.dp).width(100.dp)
        ) {
            Text(
                if (viewModel.isRunning) "停止服务" else "立即启动",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun labelText(text: String) {
    Text(
        text,
        color = ModernTheme.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.offset(y = (-4).dp)
    )
}

@Composable
fun modernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val commonTextStyle = TextStyle(
        color = ModernTheme.textPrimary,
        fontSize = 13.sp,
        lineHeight = 16.sp
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = commonTextStyle,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        cursorBrush = SolidColor(ModernTheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(ModernTheme.inputBackground, ModernTheme.shapeSmall)
                    .border(
                        width = 1.dp,
                        color = if (isFocused) ModernTheme.primary else ModernTheme.border,
                        shape = ModernTheme.shapeSmall
                    )
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier.offset(y = (-1).dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = commonTextStyle.copy(
                                color = Color.Gray.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.offset(y = (-0.5).dp)
                        )
                    }
                    innerTextField()
                }
            }
        },
        modifier = modifier.onFocusChanged { isFocused = it.isFocused }
    )
}

@Composable
fun modernCheckbox(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.clip(ModernTheme.shapeSmall).clickable { onCheckedChange(!checked) }
        .padding(vertical = 2.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(16.dp)
                .background(if (checked) ModernTheme.primary else Color.Transparent, RoundedCornerShape(3.dp)).border(
                    1.dp,
                    if (checked) ModernTheme.primary else ModernTheme.textSecondary,
                    RoundedCornerShape(3.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = ModernTheme.textPrimary, fontSize = 12.sp, modifier = Modifier.offset(y = (-4).dp))
    }
}

@Composable
fun svgIcon(svgContent: String?, size: androidx.compose.ui.unit.Dp) {
    if (svgContent.isNullOrBlank()) {
        Canvas(modifier = Modifier.size(size)) { drawCircle(Color(0xFF3B82F6), style = Stroke(width = 2f)) }; return
    }
    val drawInstructions = remember(svgContent) {
        try {
            val factory = DocumentBuilderFactory.newInstance();
            val builder = factory.newDocumentBuilder();
            val doc = builder.parse(ByteArrayInputStream(svgContent.toByteArray()));
            val root = doc.documentElement
            val vbAttr = root.getAttribute("viewBox").split(Regex("[\\s,]+"))
            val viewBox = if (vbAttr.size == 4) Rect(
                vbAttr[0].toFloat(),
                vbAttr[1].toFloat(),
                vbAttr[2].toFloat(),
                vbAttr[3].toFloat()
            ) else Rect(
                0f,
                0f,
                root.getAttribute("width").toFloatOrNull() ?: 900f,
                root.getAttribute("height").toFloatOrNull() ?: 600f
            )
            val ops = mutableListOf<DrawOp>(); parseSvgLayer(root, ops); Pair(viewBox, ops)
        } catch (_: Exception) {
            null
        }
    }
    if (drawInstructions != null) {
        val (viewBox, ops) = drawInstructions
        Canvas(modifier = Modifier.size(size)) {
            val scaleX = size.toPx() / viewBox.width;
            val scaleY = size.toPx() / viewBox.height;
            val finalScale = minOf(scaleX, scaleY)
            val drawWidth = viewBox.width * finalScale;
            val drawHeight = viewBox.height * finalScale
            val offsetX = (size.toPx() - drawWidth) / 2;
            val offsetY = (size.toPx() - drawHeight) / 2
            translate(left = offsetX, top = offsetY) {
                scale(
                    finalScale,
                    finalScale,
                    pivot = Offset.Zero
                ) {
                    ops.forEach { op ->
                        when (op) {
                            is DrawOp.PathOp -> drawPath(op.path, op.color); is DrawOp.RectOp -> drawRect(
                            op.color,
                            op.topLeft,
                            op.size
                        ); is DrawOp.CircleOp -> drawCircle(op.color, op.radius, op.center)
                        }
                    }
                }
            }
        }
    }
}

sealed class DrawOp {
    data class PathOp(val path: Path, val color: Color) : DrawOp();
    data class RectOp(val topLeft: Offset, val size: Size, val color: Color) : DrawOp();
    data class CircleOp(val center: Offset, val radius: Float, val color: Color) : DrawOp()
}

fun parseSvgLayer(element: Element, ops: MutableList<DrawOp>) {
    val children = element.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
            val el = node as Element;
            val colorStr = el.getAttribute("fill").ifBlank { "#000000" }
            val color = try {
                val c = Color(
                    java.awt.Color.decode(
                        colorStr.replace(
                            "'",
                            ""
                        )
                    ).rgb
                ).copy(alpha = 1f); if (c == Color.Black) ModernTheme.textPrimary else c
            } catch (_: Exception) {
                ModernTheme.textPrimary
            }
            when (el.tagName.lowercase()) {
                "path" -> {
                    val d = el.getAttribute("d"); if (d.isNotBlank()) ops.add(
                        DrawOp.PathOp(
                            PathParser().parsePathString(d).toPath(), color
                        )
                    )
                }

                "rect" -> ops.add(
                    DrawOp.RectOp(
                        Offset(
                            el.getAttribute("x").toFloatOrNull() ?: 0f,
                            el.getAttribute("y").toFloatOrNull() ?: 0f
                        ),
                        Size(
                            el.getAttribute("width").toFloatOrNull() ?: 0f,
                            el.getAttribute("height").toFloatOrNull() ?: 0f
                        ),
                        color
                    )
                )

                "circle" -> ops.add(
                    DrawOp.CircleOp(
                        Offset(
                            el.getAttribute("cx").toFloatOrNull() ?: 0f,
                            el.getAttribute("cy").toFloatOrNull() ?: 0f
                        ), el.getAttribute("r").toFloatOrNull() ?: 0f, color
                    )
                )

                "g" -> parseSvgLayer(el, ops)
            }
        }
    }
}