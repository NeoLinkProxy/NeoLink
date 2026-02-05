package neoproxy.neolink.gui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import org.w3c.dom.Element
import java.awt.Cursor
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

// --- 颜色定义 (1:1 复刻 JavaFX CSS) ---
val BgColor = Color(0xFF0C0C0C)
val InputBgColor = Color(0xFF202020)
val InputBorderColor = Color(0xFF555555)
val TextColor = Color(0xFFCCCCCC)
val PrimaryBlue = Color(0xFF0078D4)
val TitleBarHover = Color(0xFF333333)
val CloseRed = Color(0xFFE81123)

@Composable
fun WindowScope.`NeoLinkMainScreen`(
    windowState: WindowState,
    viewModel: NeoLinkViewModel,
    appIcon: Painter,
    onExit: () -> Unit
) {
    MaterialTheme(
        colors = darkColors(
            background = BgColor,
            surface = BgColor,
            primary = PrimaryBlue,
            onBackground = TextColor,
            onSurface = TextColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .border(1.dp, Color(0xFF333333))
        ) {
            // 1. 自定义标题栏 (Top) - 传入 appIcon
            `CustomTitleBar`(windowState, appIcon, onExit)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // 连接设置 (Top Section)
                `ConnectionSection`(viewModel)

                Spacer(modifier = Modifier.height(24.dp))

                // 高级设置 (Accordion)
                `AdvancedSettingsSection`(viewModel)

                Spacer(modifier = Modifier.height(16.dp))

                // 日志区域 (Log Console)
                `LogConsoleSection`(viewModel)

                Spacer(modifier = Modifier.height(16.dp))

                // 底部按钮 (Bottom Bar)
                `BottomBar`(viewModel)
            }
        }
    }
}

// --- 标题栏组件 ---
@Composable
fun WindowScope.`CustomTitleBar`(
    windowState: WindowState,
    appIcon: Painter,
    onExit: () -> Unit
) {
    WindowDraggableArea {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(BgColor)
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 复刻 JavaFX logoView 样式
            Image(
                painter = appIcon,
                contentDescription = "Logo",
                modifier = Modifier
                    .height(26.dp)
                    .padding(end = 10.dp),
                contentScale = ContentScale.Fit
            )

            Text(
                "NeoLink - 内网穿透客户端",
                color = TextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.weight(1f))

            // 窗口控制按钮
            `TitleBarButton`("—") { windowState.isMinimized = true }
            `TitleBarButton`(if (windowState.placement == WindowPlacement.Maximized) "❐" else "□") {
                windowState.placement = if (windowState.placement == WindowPlacement.Maximized)
                    WindowPlacement.Floating else WindowPlacement.Maximized
            }
            `TitleBarButton`("✕", isClose = true) { onExit() }
        }
    }
}

@Composable
fun `TitleBarButton`(text: String, isClose: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(
                if (isHovered) (if (isClose) CloseRed else TitleBarHover) else Color.Transparent
            )
            .clickable(onClick = onClick, interactionSource = interactionSource, indication = null),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextColor, fontSize = 14.sp)
    }
}

@Composable
fun `ConnectionSection`(vm: NeoLinkViewModel) {
    Column {
        `GroupTitle`("连接设置")
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            `LabeledComponent`("远程服务器:") {
                if (vm.nodeList.isNotEmpty()) {
                    `NodeSelector`(vm)
                } else {
                    `CustomTextField`(
                        value = vm.remoteDomain,
                        onValueChange = { vm.remoteDomain = it },
                        width = 220.dp
                    )
                }
            }

            `LabeledComponent`("本地端口:") {
                `CustomTextField`(
                    value = vm.localPort,
                    onValueChange = { if (it.all { c -> c.isDigit() }) vm.localPort = it },
                    width = 160.dp
                )
            }

            `LabeledComponent`("访问密钥:") {
                `CustomTextField`(
                    value = vm.accessKey,
                    onValueChange = { vm.accessKey = it },
                    isPassword = true,
                    width = 220.dp
                )
            }
        }
    }
}

@Composable
fun `NodeSelector`(vm: NeoLinkViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .width(220.dp)
                .height(30.dp)
                .background(InputBgColor, RoundedCornerShape(3.dp))
                .border(1.dp, InputBorderColor, RoundedCornerShape(3.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            vm.selectedNode?.let { node ->
                `SvgIcon`(node.iconSvg, size = 18.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(node.name, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            } ?: Text("选择节点", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.weight(1f))

            Text("▼", color = Color.Gray, fontSize = 10.sp)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF2B2B2B)).width(220.dp)
        ) {
            vm.nodeList.forEach { node ->
                DropdownMenuItem(onClick = {
                    vm.selectNode(node)
                    expanded = false
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        `SvgIcon`(node.iconSvg, size = 16.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(node.name, color = TextColor, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun `AdvancedSettingsSection`(vm: NeoLinkViewModel) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .clickable { isExpanded = !isExpanded }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (isExpanded) "▼" else "▶", color = TextColor, fontSize = 10.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("高级设置", color = TextColor, fontSize = 14.sp)
        }

        if (isExpanded) {
            Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    `LabelText`("本地域名:", 100.dp)
                    `CustomTextField`(vm.localDomain, { vm.localDomain = it }, width = 200.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    `LabelText`("服务端口:", 100.dp)
                    `CustomTextField`(vm.hostHookPort, { vm.hostHookPort = it }, width = 200.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    `LabelText`("连接端口:", 100.dp)
                    `CustomTextField`(vm.hostConnectPort, { vm.hostConnectPort = it }, width = 200.dp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    `LabelText`("协议启用:", 100.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        `CustomCheckbox`("启用TCP", vm.isTcpEnabled) { vm.isTcpEnabled = it }
                        `CustomCheckbox`("启用UDP", vm.isUdpEnabled) { vm.isUdpEnabled = it }
                        `CustomCheckbox`("真实IP透传", vm.isPpv2Enabled) { vm.isPpv2Enabled = it }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    `LabelText`("自动重连:", 100.dp)
                    `CustomCheckbox`("启用自动重连", vm.isAutoReconnect) { vm.isAutoReconnect = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    `LabelText`("日志设置:", 100.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        `CustomCheckbox`("调试模式", vm.isDebugMode) { vm.isDebugMode = it }
                        `CustomCheckbox`("显示连接", vm.isShowConnection) { vm.isShowConnection = it }
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.`LogConsoleSection`(vm: NeoLinkViewModel) {
    Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
        `GroupTitle`("运行日志")
        Spacer(modifier = Modifier.height(8.dp))

        val listState = rememberLazyListState()

        LaunchedEffect(vm.logMessages.size) {
            if (vm.logMessages.isNotEmpty()) {
                listState.scrollToItem(vm.logMessages.size - 1)
            }
        }

        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050505))
                    .border(1.dp, Color(0xFF1E1E1E))
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(vm.logMessages) { msg ->
                        Text(
                            text = msg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            style = TextStyle(
                                lineHeight = 18.sp,
                                letterSpacing = 0.5.sp
                            ),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun `BottomBar`(vm: NeoLinkViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { vm.startService() },
            enabled = !vm.isRunning,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = PrimaryBlue,
                disabledBackgroundColor = Color(0xFF333333)
            ),
            modifier = Modifier.height(36.dp)
        ) {
            Text("启动服务", color = if (vm.isRunning) Color.Gray else Color.White)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Button(
            onClick = { vm.stopService() },
            enabled = vm.isRunning,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFF333333),
                disabledBackgroundColor = Color(0xFF222222)
            ),
            modifier = Modifier.height(36.dp)
        ) {
            Text("停止服务", color = if (!vm.isRunning) Color.Gray else Color.White)
        }
    }
}

// --- 通用小组件 ---

@Composable
fun `GroupTitle`(text: String) {
    Text(text, color = PrimaryBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun `LabeledComponent`(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextColor, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(8.dp))
        content()
    }
}

@Composable
fun `LabelText`(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text, color = TextColor, fontSize = 13.sp, modifier = Modifier.width(width))
}

@Composable
fun `CustomTextField`(
    value: String,
    onValueChange: (String) -> Unit,
    width: androidx.compose.ui.unit.Dp,
    isPassword: Boolean = false
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        cursorBrush = SolidColor(Color.White),
        modifier = Modifier
            .width(width)
            .height(30.dp)
            .background(InputBgColor, RoundedCornerShape(3.dp))
            .border(1.dp, InputBorderColor, RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
fun `CustomCheckbox`(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(if (checked) PrimaryBlue else InputBgColor, RoundedCornerShape(4.dp))
                .border(2.dp, if (checked) PrimaryBlue else InputBorderColor, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Text("✔", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun `SvgIcon`(svgContent: String?, size: androidx.compose.ui.unit.Dp) {
    if (svgContent.isNullOrBlank()) {
        Canvas(modifier = Modifier.size(size)) { drawCircle(Color(0xFF00FF88)) }
        return
    }

    val drawInstructions = remember(svgContent) {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(svgContent.toByteArray()))
            val root = doc.documentElement

            val vbAttr = root.getAttribute("viewBox").split(Regex("[\\s,]+"))
            val viewBox = if (vbAttr.size == 4) {
                Rect(vbAttr[0].toFloat(), vbAttr[1].toFloat(), vbAttr[2].toFloat(), vbAttr[3].toFloat())
            } else {
                val w = root.getAttribute("width").toFloatOrNull() ?: 900f
                val h = root.getAttribute("height").toFloatOrNull() ?: 600f
                Rect(0f, 0f, w, h)
            }

            val instructions = mutableListOf<`DrawOp`>()
            `parseSvgLayer`(root, instructions)
            Pair(viewBox, instructions)
        } catch (e: Exception) {
            null
        }
    }

    if (drawInstructions != null) {
        val (viewBox, ops) = drawInstructions
        Canvas(modifier = Modifier.size(size)) {
            val scaleX = size.toPx() / viewBox.width
            val scaleY = size.toPx() / viewBox.height
            val finalScale = minOf(scaleX, scaleY)

            scale(finalScale, finalScale, pivot = Offset.Zero) {
                ops.forEach { op ->
                    when (op) {
                        is `DrawOp`.PathOp -> drawPath(op.path, op.color)
                        is `DrawOp`.RectOp -> drawRect(op.color, op.topLeft, op.size)
                        is `DrawOp`.CircleOp -> drawCircle(op.color, op.radius, op.center)
                    }
                }
            }
        }
    }
}

sealed class `DrawOp` {
    data class PathOp(val path: Path, val color: Color) : `DrawOp`()
    data class RectOp(val topLeft: Offset, val size: Size, val color: Color) : `DrawOp`()
    data class CircleOp(val center: Offset, val radius: Float, val color: Color) : `DrawOp`()
}

fun `parseSvgLayer`(element: Element, ops: MutableList<`DrawOp`>) {
    val children = element.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
            val el = node as Element
            val colorStr = el.getAttribute("fill").ifBlank { "#000000" }
            val color = try {
                Color(java.awt.Color.decode(colorStr.replace("'", "")).rgb).copy(alpha = 1f)
            } catch (e: Exception) {
                Color.Black
            }

            when (el.tagName.lowercase()) {
                "path" -> {
                    val d = el.getAttribute("d")
                    if (d.isNotBlank()) {
                        val path = PathParser().parsePathString(d).toPath()
                        ops.add(`DrawOp`.PathOp(path, color))
                    }
                }
                "rect" -> {
                    val x = el.getAttribute("x").toFloatOrNull() ?: 0f
                    val y = el.getAttribute("y").toFloatOrNull() ?: 0f
                    val w = el.getAttribute("width").toFloatOrNull() ?: 0f
                    val h = el.getAttribute("height").toFloatOrNull() ?: 0f
                    ops.add(`DrawOp`.RectOp(Offset(x, y), Size(w, h), color))
                }
                "circle" -> {
                    val cx = el.getAttribute("cx").toFloatOrNull() ?: 0f
                    val cy = el.getAttribute("cy").toFloatOrNull() ?: 0f
                    val r = el.getAttribute("r").toFloatOrNull() ?: 0f
                    ops.add(`DrawOp`.CircleOp(Offset(cx, cy), r, color))
                }
                "g" -> `parseSvgLayer`(el, ops)
            }
        }
    }
}