package neoproxy.neolink.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF80DEEA),
    tertiary = Color(0xFFA5D6A7),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    // Android 端是高频控制面板，不是营销页；亮色主题以可信蓝作为唯一主动作色，
    // 让“连接 / 焦点 / 可复制隧道地址”在表单、状态卡和日志里保持一致。
    primary = Color(0xFF0052FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF1FF),
    onPrimaryContainer = Color(0xFF002B85),

    // 青色只承担节点、网络辅助信息的角色，避免与主连接按钮争夺注意力。
    secondary = Color(0xFF007C92),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F7FA),
    onSecondaryContainer = Color(0xFF003A45),

    // 绿色仅表达“已连接 / 成功”，不作为品牌主色，避免把危险操作误读为正向操作。
    tertiary = Color(0xFF108C3D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7F7ED),
    onTertiaryContainer = Color(0xFF063D1D),

    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF061B31),
    surface = Color.White,
    onSurface = Color(0xFF061B31),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF5B616E),

    outline = Color(0xFFD8E1EA),
    outlineVariant = Color(0xFFE8EEF5),

    error = Color(0xFFCF202F),
    onError = Color.White,
    errorContainer = Color(0xFFFFECEF),
    onErrorContainer = Color(0xFF7A0610),
)

@Composable
fun NeoLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
