package neoproxy.neolink.gui

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.map

/**
 * 鼠标悬停状态收集器
 *
 * 核心职责：
 * 1. 监听 Compose 交互源的悬停事件
 * 2. 将悬停状态转换为可观察的 State
 * 3. 用于实现悬停效果的 UI 组件
 *
 * 使用场景：
 * - 按钮悬停效果
 * - 列表项悬停高亮
 * - 自定义悬停交互
 *
 * @return 悬停状态的 State，true 表示鼠标正在悬停
 * @author NeoProxy Team
 * @since 5.0.0
 */
@Composable
fun InteractionSource.collectIsHoveredAsState(): State<Boolean> {
    val isHovered = remember { mutableStateOf(false) }
    LaunchedEffect(this) {
        interactions.map { interaction ->
            when (interaction) {
                is HoverInteraction.Enter -> true
                is HoverInteraction.Exit -> false
                else -> null
            }
        }.collect {
            if (it != null) isHovered.value = it
        }
    }
    return isHovered
}