package neoproxy.neolink.gui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TrafficChartTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
val TotalTrafficChartColor = Color(0xFF7C3AED)
val TunnelTrafficChartColor = ModernTheme.primary

@Composable
fun inlineDropdown(
    expanded: Boolean,
    selectedText: String,
    emptyText: String,
    selectedColor: Color = ModernTheme.textPrimary,
    leadingIcon: (@Composable () -> Unit)? = null,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().zIndex(if (expanded) 2f else 0f)) {
        Row(
            Modifier.fillMaxWidth().height(34.dp).background(ModernTheme.inputBackground, ModernTheme.shapeSmall)
                .border(1.dp, if (expanded) ModernTheme.primary else ModernTheme.border, ModernTheme.shapeSmall)
                .clip(ModernTheme.shapeSmall)
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon?.invoke()
            if (leadingIcon != null) Spacer(Modifier.width(8.dp))
            Text(selectedText.ifBlank { emptyText }, color = selectedColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.KeyboardArrowDown,
                null,
                tint = ModernTheme.textSecondary,
                modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f)
            )
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(Color(0xF21E1E20), ModernTheme.shapeSmall)
                    .border(1.dp, ModernTheme.border, ModernTheme.shapeSmall)
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
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    Column(
        Modifier.fillMaxWidth()
            .background(if (isHovered && enabled) ModernTheme.surfaceHover else Color.Transparent)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(primary, color = if (enabled) primaryColor else ModernTheme.textSecondary, fontSize = 13.sp)
        if (secondary.isNotBlank()) {
            Text(secondary, color = ModernTheme.textSecondary, fontSize = 11.sp)
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

@Composable
fun sectionTitle(text: String, color: Color = ModernTheme.primary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(3.dp, 14.dp).offset(y = 2.5.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            color = ModernTheme.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = (-1).dp)
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
                    color = if (ratio == 0f) ModernTheme.primary.copy(alpha = 0.72f) else ModernTheme.border.copy(alpha = 0.72f),
                    start = Offset(chartLeft, y),
                    end = Offset(size.width - chartRight, y),
                    strokeWidth = if (ratio == 0f) 1.4.dp.toPx() else 1.dp.toPx()
                )
            }
            listOf(0.25f, 0.75f).forEach { ratio ->
                val y = chartBottomY - chartHeight * ratio
                drawLine(
                    color = ModernTheme.border.copy(alpha = 0.42f),
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
                val areaPath = Path().apply {
                    moveTo(chartOffsets.first().x, chartBottomY)
                    chartOffsets.forEach { offset -> lineTo(offset.x, offset.y) }
                    lineTo(chartOffsets.last().x, chartBottomY)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.32f), lineColor.copy(alpha = 0.0f)),
                        startY = chartTop,
                        endY = chartBottomY
                    )
                )
                drawPath(linePath, color = lineColor, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Column(
            modifier = Modifier.align(Alignment.TopStart).height(72.dp),
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
    Text(text, color = ModernTheme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.offset(y = (-3).dp))
}

@Composable
fun modernTextField(value: String, onValueChange: (String) -> Unit, placeholder: String = "", isPassword: Boolean = false, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val commonTextStyle = TextStyle(color = ModernTheme.textPrimary, fontSize = 13.sp, lineHeight = 16.sp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = commonTextStyle,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        cursorBrush = SolidColor(ModernTheme.primary),
        decorationBox = { innerTextField ->
            Box(
                Modifier.fillMaxWidth().height(34.dp).background(ModernTheme.inputBackground, ModernTheme.shapeSmall)
                    .border(1.dp, if (isFocused) ModernTheme.primary else ModernTheme.border, ModernTheme.shapeSmall).padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) Text(placeholder, color = Color.Gray.copy(alpha = 0.5f), fontSize = 12.sp)
                innerTextField()
            }
        },
        modifier = modifier.onFocusChanged { isFocused = it.isFocused }
    )
}

@Composable
fun modernCheckbox(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.clip(ModernTheme.shapeSmall).clickable { onCheckedChange(!checked) }.padding(vertical = 2.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(16.dp).background(if (checked) ModernTheme.primary else Color.Transparent, RoundedCornerShape(3.dp))
                .border(1.dp, if (checked) ModernTheme.primary else ModernTheme.textSecondary, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text, color = ModernTheme.textPrimary, fontSize = 12.sp)
    }
}

@Composable
fun compactToggle(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val bg = if (checked) ModernTheme.primary.copy(alpha = 0.22f) else ModernTheme.inputBackground
    val fg = if (checked) ModernTheme.primary else ModernTheme.textSecondary
    Box(Modifier.height(28.dp).width(48.dp).background(bg, ModernTheme.shapeSmall).border(1.dp, if (checked) ModernTheme.primary else ModernTheme.border, ModernTheme.shapeSmall).clickable { onCheckedChange(!checked) }, contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun primaryButton(text: String, loading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !loading, modifier = Modifier.fillMaxWidth().height(38.dp), shape = ModernTheme.shapeSmall, colors = ButtonDefaults.buttonColors(backgroundColor = ModernTheme.primary), elevation = ButtonDefaults.elevation(0.dp, 0.dp)) {
        Text(if (loading) "处理中..." else text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun secondaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(34.dp), shape = ModernTheme.shapeSmall, colors = ButtonDefaults.buttonColors(backgroundColor = ModernTheme.surfaceHover), elevation = ButtonDefaults.elevation(0.dp, 0.dp)) {
        Text(text, color = ModernTheme.textPrimary, fontSize = 13.sp)
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
