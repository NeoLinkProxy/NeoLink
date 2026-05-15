package neoproxy.neolink.gui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import neoproxy.neolink.gui.model.NasKey
import neoproxy.neolink.gui.ui.theme.ModernTheme
import java.util.Locale

private val FormalKeyPurple = Color(0xFF7C3AED)
private val MetricLabelBaselineOffset = (-1).dp
private val MetricValueBaselineOffset = (-1).dp
private val KeyTypeBadgeTextBaselineOffset = (-1.5).dp

@Composable
fun keyParameterGrid(key: NasKey, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ModernTheme.inputBackground.copy(alpha = 0.42f), ModernTheme.shapeSmall)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        keyMetricText(
            label = "剩余流量",
            value = "${formatKeyTrafficMiB(key.balanceMiB)} MiB",
            color = ModernTheme.success,
            modifier = Modifier.weight(1f)
        )
        keyMetricText(
            label = "到期时间",
            value = key.expire.ifBlank { "N/A" },
            color = ModernTheme.accentLight,
            modifier = Modifier.weight(1.18f)
        )
        keyMetricText(
            label = "带宽",
            value = formatKeyBandwidth(key.rate),
            color = keyTypeColor(key),
            modifier = Modifier.weight(0.82f)
        )
    }
}

@Composable
fun keyTypeBadge(key: NasKey) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .background(keyTypeBackground(key), RoundedCornerShape(4.dp))
            .border(1.dp, keyTypeColor(key).copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            key.displayType,
            color = keyTypeColor(key),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = KeyTypeBadgeTextBaselineOffset)
        )
    }
}

@Composable
private fun keyMetricText(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            color = ModernTheme.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.offset(y = MetricLabelBaselineOffset)
        )
        Text(
            value,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(y = MetricValueBaselineOffset)
        )
    }
}

fun keySummaryText(key: NasKey, prefix: String = ""): String {
    val flow = formatKeyTrafficMiB(key.balanceMiB)
    val expire = key.expire.ifBlank { "N/A" }
    val bandwidth = formatKeyBandwidth(key.rate)
    return "${prefix}流量 $flow MiB / 到期 $expire / 带宽 $bandwidth"
}

fun formatKeyTrafficMiB(value: Double, decimals: Int = 3): String {
    val safeDecimals = decimals.coerceIn(0, 6)
    return String.format(Locale.ROOT, "%.${safeDecimals}f", value)
}

fun formatKeyBandwidth(value: String): String {
    val normalized = value.trim()
    if (normalized.isBlank() || normalized == "N/A" || normalized == "-") {
        return "N/A"
    }
    return if (normalized.contains("bps", ignoreCase = true)) normalized else "$normalized Mbps"
}

fun keyTypeColor(key: NasKey): Color {
    return if (key.isTrial) ModernTheme.success else FormalKeyPurple
}

private fun keyTypeBackground(key: NasKey): Color {
    return keyTypeColor(key).copy(alpha = if (key.isTrial) 0.16f else 0.18f)
}
