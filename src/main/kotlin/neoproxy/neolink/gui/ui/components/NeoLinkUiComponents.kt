package neoproxy.neolink.gui.ui.components
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import neoproxy.neolink.gui.model.TrafficPoint
import neoproxy.neolink.gui.ui.theme.ModernTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TrafficChartTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
val TotalTrafficChartColor = Color(0xFF7C3AED)
val TunnelTrafficChartColor = Color(0xFF22D3EE)
private val SectionMarkerDefaultColor = Color(0xFF8B5CF6)

@Composable
fun inlineDropdown(
    expanded: Boolean,
    selectedText: String,
    emptyText: String,
    selectedColor: Color = ModernTheme.textPrimary,
    height: androidx.compose.ui.unit.Dp = 32.dp,
    leadingIcon: (@Composable () -> Unit)? = null,
    selectedTrailing: (@Composable () -> Unit)? = null,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val arrowRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(150)
    )
    Column(modifier = Modifier.fillMaxWidth().zIndex(if (expanded) 2f else 0f)) {
        Row(
            Modifier.fillMaxWidth().height(height).background(ModernTheme.surfaceRaised, ModernTheme.shapeSmall)
                .border(1.dp, if (expanded) ModernTheme.accent else ModernTheme.border, ModernTheme.shapeSmall)
                .clip(ModernTheme.shapeSmall)
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon?.invoke()
            if (leadingIcon != null) Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedText.ifBlank { emptyText }, color = selectedColor, fontSize = 13.sp, modifier = Modifier)
                if (selectedTrailing != null) {
                    Spacer(Modifier.width(8.dp))
                    selectedTrailing()
                }
            }
            Icon(
                Icons.Default.KeyboardArrowDown,
                null,
                tint = ModernTheme.textSecondary,
                modifier = Modifier.size(18.dp).rotate(arrowRotation)
            )
        }
        AnimatedVisibility(expanded, enter = expandVertically(animationSpec = androidx.compose.animation.core.tween(180)) + fadeIn(), exit = shrinkVertically(animationSpec = androidx.compose.animation.core.tween(180)) + fadeOut()) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(ModernTheme.panelBrush, ModernTheme.shapeSmall)
                    .border(1.dp, ModernTheme.borderStrong, ModernTheme.shapeSmall)
                    .clip(ModernTheme.shapeSmall)
                    .padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

@Composable
fun inlineDropdownItem(
    primary: String,
    secondary: String = "",
    primaryColor: Color = ModernTheme.textPrimary,
    backgroundColor: Color = Color.Transparent,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Column(
        Modifier.fillMaxWidth()
            .background(if (isHovered && enabled) ModernTheme.surfaceHover else backgroundColor)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            if (leading != null) {
                Spacer(Modifier.width(8.dp))
            }
            Text(
                primary,
                color = if (enabled) primaryColor else ModernTheme.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
        if (secondary.isNotBlank()) {
            Text(secondary, color = ModernTheme.textSecondary, fontSize = 11.sp, modifier = Modifier)
        }
    }
}

@Composable
fun sectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(ModernTheme.panelBrush, ModernTheme.shapeMedium)
            .border(1.dp, ModernTheme.border, ModernTheme.shapeMedium).padding(12.dp),
        content = content
    )
}

@Composable
fun sectionTitle(text: String, color: Color = SectionMarkerDefaultColor) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(3.dp, 14.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            color = ModernTheme.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
        )
    }
}

@Composable
fun trafficChart(points: List<TrafficPoint>, modifier: Modifier = Modifier, lineColor: Color = TunnelTrafficChartColor) {
    val nowSecond = points.lastOrNull()?.second ?: Instant.now().epochSecond
    val startSecond = nowSecond - 9
    val bySecond = points.associateBy { it.second }
    val windowPoints = (startSecond..nowSecond).map { second -> bySecond[second] ?: TrafficPoint(second, 0L) }
    val maxBytes = windowPoints.maxOfOrNull { it.bytes }?.coerceAtLeast(1024L) ?: 1024L
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val chartLeft = 46.dp.toPx()
            val chartRight = 4.dp.toPx()
            val chartTop = 8.dp.toPx()
            val chartBottom = 16.dp.toPx()
            val chartWidth = (size.width - chartLeft - chartRight).coerceAtLeast(1f)
            val chartHeight = (size.height - chartTop - chartBottom).coerceAtLeast(1f)
            val chartBottomY = chartTop + chartHeight

            listOf(0f, 0.5f, 1f).forEach { ratio ->
                val y = chartBottomY - chartHeight * ratio
                drawLine(
                    color = if (ratio == 0f) ModernTheme.accent.copy(alpha = 0.55f) else ModernTheme.border.copy(alpha = 0.35f),
                    start = Offset(chartLeft, y),
                    end = Offset(size.width - chartRight, y),
                    strokeWidth = if (ratio == 0f) 1.4.dp.toPx() else 1.dp.toPx()
                )
            }
            listOf(0.25f, 0.75f).forEach { ratio ->
                val y = chartBottomY - chartHeight * ratio
                drawLine(
                    color = ModernTheme.border.copy(alpha = 0.18f),
                    start = Offset(chartLeft, y),
                    end = Offset(size.width - chartRight, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (windowPoints.size >= 2) {
                val step = chartWidth / (windowPoints.size - 1).coerceAtLeast(1)
                val chartOffsets = windowPoints.mapIndexed { index, point ->
                    val x = chartLeft + step * index
                    val y = chartBottomY - (point.bytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f) * chartHeight
                    Offset(x, y)
                }
                val linePath = Path().apply {
                    chartOffsets.forEachIndexed { index, offset ->
                        if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
                    }
                }
                drawPath(linePath, color = lineColor, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .padding(top = TrafficChartTopPadding, bottom = TrafficChartBottomPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            trafficAxisLabel("${formatBytes(maxBytes)}/s")
            trafficAxisLabel("${formatBytes(maxBytes / 2)}/s")
            trafficAxisLabel("0 B/s")
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 46.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            trafficAxisLabel(formatTrafficChartSecond(startSecond))
            trafficAxisLabel(formatTrafficChartSecond(nowSecond))
        }
    }
}

private val TrafficChartTopPadding = 8.dp
private val TrafficChartBottomPadding = 16.dp

private fun formatTrafficChartSecond(second: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochSecond(second), ZoneId.systemDefault()).format(TrafficChartTimeFormatter)

@Composable
private fun trafficAxisLabel(text: String) {
    Text(text, color = ModernTheme.textSecondary.copy(alpha = 0.72f), fontSize = 9.sp)
}

@Composable
fun fieldColumn(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    Column(modifier) {
        labelText(label)
        Spacer(Modifier.height(4.dp))
        modernTextField(value, onValueChange)
    }
}

@Composable
fun labelText(text: String) {
    Text(text, color = ModernTheme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier)
}

@Composable
fun modernTextField(value: String, onValueChange: (String) -> Unit, placeholder: String = "", isPassword: Boolean = false, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val commonTextStyle = TextStyle(color = ModernTheme.textPrimary, fontSize = 13.sp, lineHeight = 16.sp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = commonTextStyle,
        visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        cursorBrush = SolidColor(ModernTheme.accent),
        decorationBox = { innerTextField ->
            Box(
                Modifier.fillMaxWidth().height(34.dp).background(ModernTheme.inputBackground, ModernTheme.shapeSmall)
                    .border(1.dp, if (isFocused) ModernTheme.accent else ModernTheme.border, ModernTheme.shapeSmall).padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                color = Color.Gray.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                modifier = Modifier
                            )
                        }
                        innerTextField()
                    }
                    if (isPassword) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.size(18.dp).clip(ModernTheme.shapeSmall).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                isPasswordVisible = !isPasswordVisible
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码",
                                tint = if (isFocused) ModernTheme.accent else ModernTheme.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.onFocusChanged { isFocused = it.isFocused }
    )
}

@Composable
fun modernCheckbox(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    var visualChecked by remember { mutableStateOf(checked) }
    var clickPulseToken by remember { mutableStateOf(0) }
    var clickPulseActive by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        visualChecked = checked
    }
    LaunchedEffect(clickPulseToken) {
        if (clickPulseToken > 0) {
            clickPulseActive = true
            delay(160)
            clickPulseActive = false
        }
    }
    val checkScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when {
            isPressed || clickPulseActive -> 0.9f
            visualChecked -> 1f
            else -> 0.8f
        },
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 300f)
    )
    val boxBackground = when {
        visualChecked -> ModernTheme.accent.copy(alpha = if (isPressed || clickPulseActive) 0.82f else if (isHovered) 0.30f else 0.22f)
        isPressed || clickPulseActive -> ModernTheme.accent.copy(alpha = 0.18f)
        isHovered -> ModernTheme.surfaceHover
        else -> ModernTheme.surfaceRaised
    }
    val borderColor = when {
        visualChecked -> ModernTheme.accent.copy(alpha = if (isPressed || clickPulseActive) 1f else 0.82f)
        isPressed || clickPulseActive -> ModernTheme.accent.copy(alpha = 0.75f)
        else -> ModernTheme.borderStrong
    }
    Row(
        Modifier.clip(ModernTheme.shapeSmall)
            .clickable(interactionSource = interactionSource, indication = null) {
                val next = !visualChecked
                visualChecked = next
                clickPulseToken++
                onCheckedChange(next)
            }
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(18.dp).background(boxBackground, RoundedCornerShape(4.dp))
                .border(1.5.dp, borderColor, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (visualChecked) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp).graphicsLayer { scaleX = checkScale; scaleY = checkScale }
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(text, color = ModernTheme.textPrimary, fontSize = 12.sp, modifier = Modifier)
    }
}

@Composable
fun compactToggle(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    var visualChecked by remember { mutableStateOf(checked) }
    var clickPulseToken by remember { mutableStateOf(0) }
    var clickPulseActive by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        visualChecked = checked
    }
    LaunchedEffect(clickPulseToken) {
        if (clickPulseToken > 0) {
            clickPulseActive = true
            delay(160)
            clickPulseActive = false
        }
    }
    val bg = when {
        visualChecked -> ModernTheme.success.copy(alpha = if (isPressed || clickPulseActive) 0.42f else if (isHovered) 0.30f else 0.22f)
        isPressed || clickPulseActive -> ModernTheme.accent.copy(alpha = 0.24f)
        isHovered -> ModernTheme.surfaceHover
        else -> ModernTheme.inputBackground
    }
    val borderColor = when {
        visualChecked -> ModernTheme.success
        isPressed || clickPulseActive -> ModernTheme.accent
        isHovered -> ModernTheme.borderStrong
        else -> ModernTheme.borderStrong
    }
    Box(
        Modifier.height(28.dp).width(50.dp).background(bg, ModernTheme.shapeSmall)
            .border(1.dp, borderColor, ModernTheme.shapeSmall)
            .clip(ModernTheme.shapeSmall)
            .clickable(interactionSource = interactionSource, indication = null) {
                val next = !visualChecked
                visualChecked = next
                clickPulseToken++
                onCheckedChange(next)
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (visualChecked) ModernTheme.success else ModernTheme.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier)
    }
}

@Composable
fun primaryButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = when {
        loading -> ModernTheme.surfaceHover
        isPressed -> ModernTheme.success.copy(alpha = 0.82f)
        isHovered -> ModernTheme.success.copy(alpha = 0.92f)
        else -> ModernTheme.success
    }
    val borderColor = when {
        loading -> ModernTheme.border
        isPressed -> ModernTheme.success.copy(alpha = 0.76f)
        isHovered -> Color.White.copy(alpha = 0.55f)
        else -> ModernTheme.success.copy(alpha = 0.72f)
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(38.dp)
            .background(backgroundColor, ModernTheme.shapeSmall)
            .border(1.dp, borderColor, ModernTheme.shapeSmall)
            .clip(ModernTheme.shapeSmall)
            .clickable(enabled = !loading, interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("处理中...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier)
            }
        } else {
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier)
        }
    }
}

@Composable
fun secondaryButton(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor = when {
        isPressed -> ModernTheme.success.copy(alpha = 0.82f)
        isHovered -> ModernTheme.success.copy(alpha = 0.92f)
        else -> ModernTheme.success
    }
    val borderColor = when {
        isPressed -> ModernTheme.success.copy(alpha = 0.76f)
        isHovered -> Color.White.copy(alpha = 0.55f)
        else -> ModernTheme.success.copy(alpha = 0.72f)
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(34.dp)
            .background(backgroundColor, ModernTheme.shapeSmall)
            .border(1.dp, borderColor, ModernTheme.shapeSmall)
            .clip(ModernTheme.shapeSmall)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier)
    }
}

fun Modifier.modalInputBarrier(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            // 自绘模态层必须成为指针事件的终点。放在 Final pass 消费事件，
            // 让弹窗内部按钮先正常处理点击，同时阻断事件继续穿透到下层界面。
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.forEach { it.consume() }
        }
    }
}

@Composable
fun inlineIconButton(
    checked: Boolean = false,
    enabledTint: Color = ModernTheme.accent,
    size: Dp = 24.dp,
    showContainer: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    var clickPulseToken by remember { mutableStateOf(0) }
    var clickPulseActive by remember { mutableStateOf(false) }
    LaunchedEffect(clickPulseToken) {
        if (clickPulseToken > 0) {
            clickPulseActive = true
            delay(160)
            clickPulseActive = false
        }
    }
    val backgroundColor = when {
        !showContainer -> Color.Transparent
        checked -> enabledTint.copy(alpha = if (isPressed || clickPulseActive) 0.28f else if (isHovered) 0.20f else 0.14f)
        isPressed || clickPulseActive -> enabledTint.copy(alpha = 0.18f)
        isHovered -> ModernTheme.surfaceHover
        else -> ModernTheme.surfaceRaised
    }
    val borderColor = when {
        !showContainer -> Color.Transparent
        checked -> enabledTint.copy(alpha = if (isPressed || clickPulseActive) 1f else 0.78f)
        isPressed || clickPulseActive -> enabledTint.copy(alpha = 0.88f)
        isHovered -> ModernTheme.borderStrong
        else -> ModernTheme.borderStrong
    }
    Box(
        Modifier.size(size)
            .background(backgroundColor, ModernTheme.shapeSmall)
            .then(if (showContainer) Modifier.border(1.dp, borderColor, ModernTheme.shapeSmall) else Modifier)
            .clip(ModernTheme.shapeSmall)
            .clickable(interactionSource = interactionSource, indication = null) {
                clickPulseToken++
                coroutineScope.launch {
                    withFrameNanos { }
                    delay(48)
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.key(isPressed, isHovered, checked) {
            Box(contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return "%.2f %s".format(value, units[unit])
}
