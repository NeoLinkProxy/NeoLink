package neoproxy.neolink.gui

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.map

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