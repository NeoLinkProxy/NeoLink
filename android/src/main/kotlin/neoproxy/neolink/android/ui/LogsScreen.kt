package neoproxy.neolink.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import neoproxy.neolink.android.viewmodel.TunnelViewModel

private val LogWarning = Color(0xFFF4B000)

/**
 * 日志页面：实时展示隧道运行日志。
 * 不同前缀的日志使用不同颜色区分优先级，方便快速定位问题。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: TunnelViewModel) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 新日志到来时自动滚动到底部（仅当用户已经在底部时）
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            // 判断是否已经接近底部，避免用户往上查看时被强制拉回
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val shouldAutoScroll = lastVisible >= logs.size - 3
            if (shouldAutoScroll) {
                listState.animateScrollToItem(logs.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.Delete, contentDescription = "清除日志")
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            // 空状态提示
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "暂无日志",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(logs, key = { index, log -> "${index}_$log" }) { _, log ->
                    LogItem(log)
                }
            }
        }
    }
}

/**
 * 单条日志渲染。
 * 根据前缀分配颜色：错误跟随主题 error，服务端消息跟随主蓝，成功/信息跟随成功绿。
 * 这些 token 与主页状态卡保持一致，排障时用户不会在不同页面重新学习颜色含义。
 */
@Composable
private fun LogItem(log: String) {
    val color = when {
        log.startsWith("[ERROR]") || log.startsWith("[FATAL]") -> MaterialTheme.colorScheme.error
        log.startsWith("[SERVER]") -> MaterialTheme.colorScheme.primary
        log.startsWith("[INFO]") -> MaterialTheme.colorScheme.tertiary
        log.startsWith("[+]") -> MaterialTheme.colorScheme.tertiary
        log.startsWith("[-]") -> LogWarning
        else -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = log,
        color = color,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    )
}
