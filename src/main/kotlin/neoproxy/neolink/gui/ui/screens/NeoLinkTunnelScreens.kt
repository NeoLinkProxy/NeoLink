package neoproxy.neolink.gui.ui.screens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.InputSource
import top.ceroxe.api.net.TcpPingUtil
import neoproxy.neolink.gui.model.NasKey
import neoproxy.neolink.gui.model.NasNode
import neoproxy.neolink.gui.model.TrafficPoint
import neoproxy.neolink.gui.model.TunnelCardState
import neoproxy.neolink.gui.model.TunnelRuntimeUiState
import neoproxy.neolink.gui.state.NeoLinkViewModel
import neoproxy.neolink.gui.ui.components.TunnelTrafficChartColor
import neoproxy.neolink.gui.ui.components.TotalTrafficChartColor
import neoproxy.neolink.gui.ui.components.collectIsHoveredAsState
import neoproxy.neolink.gui.ui.components.compactToggle
import neoproxy.neolink.gui.ui.components.fieldColumn
import neoproxy.neolink.gui.ui.components.formatBytes
import neoproxy.neolink.gui.ui.components.inlineDropdown
import neoproxy.neolink.gui.ui.components.inlineDropdownItem
import neoproxy.neolink.gui.ui.components.labelText
import neoproxy.neolink.gui.ui.components.modernCheckbox
import neoproxy.neolink.gui.ui.components.modernTextField
import neoproxy.neolink.gui.ui.components.inlineIconButton
import neoproxy.neolink.gui.ui.components.primaryButton
import neoproxy.neolink.gui.ui.components.secondaryButton
import neoproxy.neolink.gui.ui.components.sectionCard
import neoproxy.neolink.gui.ui.components.sectionTitle
import neoproxy.neolink.gui.ui.components.trafficChart
import neoproxy.neolink.gui.ui.theme.ModernTheme
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

@Composable
fun tunnelManagementPage(viewModel: NeoLinkViewModel, onAlert: (String) -> Unit) {
    LaunchedEffect(Unit) { viewModel.refreshKeys() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            metricsRow(viewModel)
        }
        item {
            trafficCollapsible("实时总流量", viewModel.uiState.totalTrafficExpanded, viewModel.totalTrafficPoints, viewModel.totalTrafficBytes, viewModel::toggleTotalTraffic)
        }
        itemsIndexed(viewModel.tunnels, key = { _, tunnel -> tunnel.id }) { index, tunnel ->
            tunnelCard(
                viewModel,
                tunnel,
                onAlert,
                onToggleExpanded = {
                    val shouldReveal = !tunnel.expanded
                    viewModel.toggleTunnelExpanded(tunnel.id)
                    if (shouldReveal) {
                        coroutineScope.launch {
                            val itemIndex = index + TunnelListTunnelStartIndex
                            listState.animateScrollToItem(itemIndex)
                            delay(TunnelExpandRevealDelayMs)
                            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
                            val viewportBottom = listState.layoutInfo.viewportEndOffset
                            val hiddenBottom = itemInfo?.let { it.offset + it.size - viewportBottom } ?: 0
                            if (hiddenBottom > 0) {
                                listState.animateScrollBy(hiddenBottom.toFloat() + TunnelExpandRevealPaddingPx)
                            }
                        }
                    }
                }
            )
        }
        if (viewModel.creatableTunnelCount > 0) {
            item {
                createTunnelEntry(viewModel)
            }
        }
    }
    if (viewModel.uiState.createDialogVisible) {
        createTunnelDialog(viewModel, onAlert)
    }
}

@Composable
fun metricsRow(viewModel: NeoLinkViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        metricCard("可创建隧道数", viewModel.creatableTunnelCount.toString(), Modifier.weight(1f))
        metricCard("活跃隧道数", viewModel.activeTunnelCount.toString(), Modifier.weight(1f))
        metricCard("外部连接总数", viewModel.totalExternalConnections.toString(), Modifier.weight(1f))
    }
}

@Composable
fun metricCard(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier.height(82.dp).background(ModernTheme.panelBrush, ModernTheme.shapeMedium)
            .border(1.dp, ModernTheme.border, ModernTheme.shapeMedium).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ModernTheme.textSecondary, fontSize = 12.sp, modifier = Modifier.offset(y = MetricCardTextBaselineOffset))
        Text(value, color = ModernTheme.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = MetricCardValueBaselineOffset))
    }
}

@Composable
fun trafficCollapsible(title: String, expanded: Boolean, points: List<TrafficPoint>, totalBytes: Long, onToggle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(ModernTheme.panelBrush, ModernTheme.shapeMedium)
            .border(1.dp, ModernTheme.border, ModernTheme.shapeMedium).clip(ModernTheme.shapeMedium)
    ) {
        collapsibleHeaderRow(
            onToggle = onToggle,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ModernTheme.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = ModernTheme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.offset(x = (-3).dp, y = (-2).dp))
            Spacer(Modifier.weight(1f))
            Text(
                formatBytes(totalBytes),
                color = ModernTheme.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.offset(x = (-1).dp, y = (-2).dp)
            )
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                Divider(color = ModernTheme.divider, thickness = 1.dp)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(150.dp).background(ModernTheme.recessedBrush, ModernTheme.shapeSmall).border(1.dp, ModernTheme.border, ModernTheme.shapeSmall)) {
            trafficChart(points, Modifier.fillMaxSize().padding(10.dp), lineColor = TotalTrafficChartColor)
                }
            }
        }
    }
}

@Composable
fun tunnelCard(
    viewModel: NeoLinkViewModel,
    tunnel: TunnelCardState,
    onAlert: (String) -> Unit,
    onToggleExpanded: () -> Unit = { viewModel.toggleTunnelExpanded(tunnel.id) }
) {
    val runtime = viewModel.tunnelRuntime[tunnel.id] ?: TunnelRuntimeUiState()
    val key = viewModel.keys.firstOrNull { it.alias == tunnel.keyAlias }
    val expanded = tunnel.expanded
    val statusBarColor by animateColorAsState(
        targetValue = if (runtime.running) ModernTheme.success else ModernTheme.error,
        animationSpec = tween(220)
    )
    val nameFocusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxWidth().background(ModernTheme.panelBrush, ModernTheme.shapeMedium)
            .border(1.dp, ModernTheme.border, ModernTheme.shapeMedium)
            .clip(ModernTheme.shapeMedium)
    ) {
        val toggleExpanded = onToggleExpanded
        collapsibleHeaderRow(
            onToggle = toggleExpanded,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
            headerClickable = false,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(3.dp, 18.dp).offset(y = TunnelStatusBarBaselineOffset)
                    .background(statusBarColor, RoundedCornerShape(2.dp))
            )
            tunnelNameEditor(
                tunnel.name,
                { viewModel.updateTunnelName(tunnel.id, it) },
                nameFocusRequester = nameFocusRequester
            )
            Spacer(
                Modifier.weight(1f)
                    .height(TunnelHeaderControlHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = toggleExpanded
                    )
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("本地端口", color = ModernTheme.textSecondary, fontSize = 11.sp, modifier = Modifier.offset(y = TunnelHeaderPortLabelBaselineOffset))
                compactTunnelPortField(tunnel.localPort, { viewModel.updateTunnelLocalPort(tunnel.id, it) }, modifier = Modifier.width(76.dp))
            }
            compactToggle("TCP", tunnel.tcpEnabled) { viewModel.updateTunnelTcp(tunnel.id, it) }
            compactToggle("UDP", tunnel.udpEnabled) { viewModel.updateTunnelUdp(tunnel.id, it) }
            inlineIconButton(
                checked = runtime.running,
                enabledTint = ModernTheme.success,
                size = TunnelHeaderActionButtonSize,
                showContainer = false,
                onClick = {
                val error = viewModel.toggleTunnelRunning(tunnel.id)
                if (error != null) onAlert(error)
            }) {
                if (runtime.running) {
                    Icon(StopIcon, null, tint = ModernTheme.error, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Default.PlayArrow, null, tint = ModernTheme.success, modifier = Modifier.size(20.dp))
                }
            }
            inlineIconButton(
                checked = false,
                enabledTint = ModernTheme.error,
                size = TunnelHeaderActionButtonSize,
                showContainer = false,
                onClick = { viewModel.deleteTunnel(tunnel.id) }
            ) {
                Icon(Icons.Default.Delete, null, tint = ModernTheme.error, modifier = Modifier.size(20.dp))
            }
            tunnelHeaderExpandedIndicator(expanded, toggleExpanded)
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Divider(color = ModernTheme.divider)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    metricCard("本隧道连接", runtime.activeConnections.toString(), Modifier.width(120.dp))
                    Box(Modifier.weight(1f).height(88.dp).background(ModernTheme.recessedBrush, ModernTheme.shapeSmall).border(1.dp, ModernTheme.border, ModernTheme.shapeSmall)) {
                        trafficChart(
                            if (runtime.running) runtime.trafficPoints else emptyList(),
                            Modifier.fillMaxSize().padding(8.dp),
                            lineColor = TunnelTrafficChartColor
                        )
                    }
                    remainingTrafficRing(tunnel, runtime)
                }
                key?.let { keyInfo ->
                    nodeSelectorForTunnel(viewModel, tunnel, keyInfo)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    fieldColumn("本地域名", tunnel.localDomain, { viewModel.updateTunnelLocalDomain(tunnel.id, it) }, Modifier.weight(1f))
                    fieldColumn("Hook端口", tunnel.hookPort, { viewModel.updateTunnelHookPort(tunnel.id, it) }, Modifier.weight(1f))
                    fieldColumn("连接端口", tunnel.connectPort, { viewModel.updateTunnelConnectPort(tunnel.id, it) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    modernCheckbox("自动重连", tunnel.autoReconnect) { viewModel.updateTunnelAutoReconnect(tunnel.id, it) }
                    modernCheckbox("显示详细", tunnel.showConnection) { viewModel.updateTunnelShowConnection(tunnel.id, it) }
                    modernCheckbox("PPv2", tunnel.ppv2Enabled) { viewModel.updateTunnelPpv2(tunnel.id, it) }
                    modernCheckbox("调试模式", tunnel.debugMode) { viewModel.updateTunnelDebug(tunnel.id, it) }
                }
                tunnelLog(runtime, viewModel)
            }
        }
    }
}

@Composable
fun tunnelHeaderExpandedIndicator(expanded: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier.size(TunnelHeaderControlHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = ModernTheme.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private val TunnelHeaderControlHeight = 28.dp
private val TunnelHeaderActionButtonSize = 28.dp

@Composable
fun tunnelNameEditor(value: String, onValueChange: (String) -> Unit, nameFocusRequester: FocusRequester) {
    val visibleText = value.ifBlank { "隧道" }
    val fieldWidth = (visibleText.length * 14).dp.coerceIn(44.dp, 138.dp)
    val buttonSize = 24.dp
    val gap = 6.dp
    Row(
        modifier = Modifier.width(fieldWidth + gap + buttonSize).height(TunnelHeaderControlHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = ModernTheme.textPrimary, fontSize = 13.sp, lineHeight = 16.sp),
            singleLine = true,
            cursorBrush = SolidColor(ModernTheme.primary),
            decorationBox = { innerTextField ->
                Box(Modifier.width(fieldWidth).height(TunnelHeaderControlHeight), contentAlignment = Alignment.CenterStart) {
                    // 标题文本与矢量图标按钮同排显示；上移文本以保留 7.x 视觉基线，
                    // 不直接依赖 Windows 上按字体边界计算的居中结果。
                    Box(modifier = Modifier.offset(y = CompactFieldBaselineOffset), contentAlignment = Alignment.CenterStart) {
                        innerTextField()
                    }
                }
            },
            modifier = Modifier.width(fieldWidth).focusRequester(nameFocusRequester)
        )
        inlineIconButton(
            checked = false,
            enabledTint = ModernTheme.accent,
            onClick = { nameFocusRequester.requestFocus() }
        ) {
            Icon(Icons.Default.Edit, "重命名隧道", tint = ModernTheme.textSecondary, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
fun compactTunnelPortField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = ModernTheme.textPrimary, fontSize = 12.sp, lineHeight = 14.sp),
        singleLine = true,
        cursorBrush = SolidColor(ModernTheme.accent),
        decorationBox = { innerTextField ->
            Box(
                Modifier.fillMaxWidth()
                    .height(TunnelHeaderControlHeight)
                    .background(ModernTheme.inputBackground, ModernTheme.shapeSmall)
                    .border(1.dp, if (isFocused) ModernTheme.accent else ModernTheme.border, ModernTheme.shapeSmall)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // 紧凑输入框沿用与标准输入框一致的 7.x 基线上移补偿；
                // 否则在 Windows 字体度量下，字形会略低于相邻控件。
                Box(modifier = Modifier.offset(y = CompactPortFieldBaselineOffset), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            "端口",
                            color = Color.Gray.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            modifier = Modifier.offset(y = CompactFieldPlaceholderBaselineOffset)
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
fun collapsibleHeaderRow(
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    headerClickable: Boolean = true,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) ModernTheme.surfaceHover else Color.Transparent,
        animationSpec = tween(160)
    )
    Row(
        modifier = modifier
            .background(backgroundColor, ModernTheme.shapeSmall)
            .then(
                if (headerClickable) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onToggle
                    )
                } else {
                    Modifier.hoverable(interactionSource)
                }
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
        content = content
    )
}

@Composable
fun nodeSelectorForTunnel(viewModel: NeoLinkViewModel, tunnel: TunnelCardState, key: NasKey) {
    var expanded by remember { mutableStateOf(false) }
    val onlineNodes = key.onlineNodes
    val selected = onlineNodes.firstOrNull { it.nodeId == tunnel.selectedNodeId }
    val pingResults = rememberNodePingResults(expanded, onlineNodes)
    Column {
        labelText("节点选择")
        inlineDropdown(
            expanded = expanded,
            selectedText = tunnel.selectedNodeName.ifBlank { "选择节点" },
            emptyText = "无可用节点",
            height = 28.dp,
            leadingIcon = selected?.let { node -> { nodeSvgIcon(node, 16.dp) } },
            onToggle = { expanded = !expanded }
        ) {
            onlineNodes.forEach { node ->
                inlineDropdownItem(
                    primary = node.displayName,
                    secondary = "${node.address}:${node.hookPort}/${node.connectPort}",
                    primaryColor = ModernTheme.success,
                    leading = { nodeSvgIcon(node, 16.dp) },
                    trailing = { nodeLatencyText(pingResults[node.pingKey()]) }
                ) {
                    viewModel.selectTunnelNode(tunnel.id, node)
                    expanded = false
                }
            }
        }
    }
}

@Composable
fun remainingTrafficRing(tunnel: TunnelCardState, runtime: TunnelRuntimeUiState) {
    val initialBalanceMiB = tunnel.keyInitialBalanceMiB.takeIf { it.isFinite() && it > 0.0 }
        ?: tunnel.keyBalanceMiB.takeIf { it.isFinite() && it > 0.0 }
        ?: 0.0
    val totalBytes = (initialBalanceMiB * 1024.0 * 1024.0).toLong().coerceAtLeast(0L)
    val latestBalanceBytes = (tunnel.keyBalanceMiB * 1024.0 * 1024.0).toLong().coerceAtLeast(0L)
    val remainingBytes = (latestBalanceBytes - runtime.trafficSinceBalanceSyncBytes).coerceAtLeast(0L)
    val progress = if (totalBytes <= 0L) 0f else (remainingBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    Column(Modifier.width(120.dp).height(88.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(48.dp)) {
            val stroke = 5.dp.toPx()
            drawCircle(ModernTheme.border, style = Stroke(stroke))
            drawArc(ModernTheme.success, -90f, 360f * progress, false, style = Stroke(stroke))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "%.3f MiB".format(remainingBytes / 1024.0 / 1024.0),
            color = ModernTheme.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.offset(y = RemainingTrafficTextBaselineOffset)
        )
    }
}

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun tunnelLog(runtime: TunnelRuntimeUiState, viewModel: NeoLinkViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(runtime.logs.size) {
        if (runtime.logs.isNotEmpty()) listState.scrollToItem(runtime.logs.size - 1)
    }
    Box(
        Modifier.fillMaxWidth().height(150.dp).background(ModernTheme.recessedBrush, ModernTheme.shapeSmall)
            .border(1.dp, ModernTheme.border, ModernTheme.shapeSmall)
            .onPointerEvent(PointerEventType.Scroll) { event ->
                if (event.keyboardModifiers.isCtrlPressed) {
                    val delta = event.changes.first().scrollDelta.y
                    viewModel.logFontSize = if (delta < 0) (viewModel.logFontSize.value + 1f).coerceAtMost(30f).sp else (viewModel.logFontSize.value - 1f).coerceAtLeast(8f).sp
                }
            }
    ) {
        SelectionContainer {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp)) {
                items(runtime.logs) { msg ->
                    val highlightedMessage = remember(msg) { highlightLogMessage(msg) }
                    Text(
                        highlightedMessage,
                        fontSize = viewModel.logFontSize,
                        fontFamily = FontFamily.Monospace,
                        color = ModernTheme.textPrimary,
                        style = TextStyle(lineHeight = (viewModel.logFontSize.value * 1.35f).sp, letterSpacing = 0.sp)
                    )
                }
            }
        }
    }
}

private fun highlightLogMessage(original: AnnotatedString): AnnotatedString {
    val normalizedText = original.text.replace("\t", "    ")
    val builder = AnnotatedString.Builder(normalizedText)

    original.spanStyles.forEach { range ->
        val safeStart = range.start.coerceIn(0, normalizedText.length)
        val safeEnd = range.end.coerceIn(safeStart, normalizedText.length)
        if (safeStart < safeEnd) {
            builder.addStyle(range.item, safeStart, safeEnd)
        }
    }

    val exceptionColor = Color(0xFFFF5252)
    val blueColor = Color(0xFF40C4FF)
    val purpleColor = Color(0xFFE040FB)

    val exceptionHeader = "\\b[\\w\\.]+(?:Exception|Error)(?::\\s*.*)?"
    val stackTrace = "\\bat\\s+[\\w\\.\\$/<> ]+(?:\\(.*?\\))?"
    val sourceInfo = "\\((?:Unknown Source|[\\w\\.]+\\.java:\\d+)\\)"
    Regex("($exceptionHeader|$stackTrace|$sourceInfo)").findAll(normalizedText).forEach { match ->
        builder.addStyle(
            SpanStyle(color = exceptionColor, fontWeight = FontWeight.Bold),
            match.range.first,
            match.range.last + 1
        )
    }

    val trafficAmount = "\\d+(?:\\.\\d+)?\\s*(?:B|KiB|MiB|GiB|TiB|KB|MB|GB|TB)"
    val dateRange = "\\d{4}/\\d{1,2}/\\d{1,2}-\\d{1,2}:\\d{2}"
    Regex("($trafficAmount|$dateRange)").findAll(normalizedText).forEach { match ->
        builder.addStyle(
            SpanStyle(color = blueColor, fontWeight = FontWeight.Bold),
            match.range.first,
            match.range.last + 1
        )
    }

    val ipv6Bracketed = "\\[[a-fA-F0-9:]+\\](?::\\d+)?"
    val ipv6Raw = "(?:[a-fA-F0-9]{1,4}:){1,7}[a-fA-F0-9]{1,4}"
    val ipv4 = "\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?"
    val domain = "(?:localhost|(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,})(?::\\d+)?"
    val timeOnly = Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?$")
    Regex("($ipv6Bracketed|$ipv6Raw|$ipv4|$domain)").findAll(normalizedText).forEach { match ->
        if (timeOnly.matches(match.value)) return@forEach
        val lineStart = normalizedText.lastIndexOf('\n', match.range.first).let { if (it == -1) 0 else it }
        val lineEnd = normalizedText.indexOf('\n', match.range.first).let { if (it == -1) normalizedText.length else it }
        val line = normalizedText.substring(lineStart, lineEnd)
        if (line.contains("Exception") || line.contains("Error") || line.trimStart().startsWith("at ")) return@forEach
        builder.addStyle(
            SpanStyle(color = purpleColor, fontWeight = FontWeight.Bold),
            match.range.first,
            match.range.last + 1
        )
    }

    return builder.toAnnotatedString()
}

@Composable
fun createTunnelEntry(viewModel: NeoLinkViewModel) {
    val enabled = viewModel.creatableTunnelCount > 0
    Box(
        modifier = Modifier.fillMaxWidth().height(76.dp)
            .background(if (enabled) ModernTheme.surfaceRaised else ModernTheme.surfaceHover, ModernTheme.shapeMedium)
            .border(1.dp, if (enabled) ModernTheme.success.copy(alpha = 0.45f) else ModernTheme.border, ModernTheme.shapeMedium)
            .clickable(enabled = enabled) { viewModel.showCreateTunnelDialog() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, null, tint = if (enabled) ModernTheme.success else ModernTheme.textSecondary)
            Spacer(Modifier.width(8.dp))
            Text(
                "创建隧道",
                color = if (enabled) ModernTheme.success else ModernTheme.textSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = CreateEntryTextBaselineOffset)
            )
        }
    }
}

@Composable
fun createTunnelDialog(viewModel: NeoLinkViewModel, onAlert: (String) -> Unit) {
    val draft = viewModel.uiState.createDraft
    val selectedKey = viewModel.keys.firstOrNull { it.alias == draft.selectedKeyAlias }
    val canCreate by remember(draft, selectedKey) {
        derivedStateOf {
            selectedKey?.onlineNodes?.isNotEmpty() == true &&
                draft.localPort.toIntOrNull()?.let { it in 1..65535 } == true
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(460.dp).background(ModernTheme.panelBrush, ModernTheme.shapeMedium)
                .border(1.dp, ModernTheme.borderStrong, ModernTheme.shapeMedium).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("创建隧道", color = ModernTheme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            keyDropdown(viewModel)
            selectedKey?.let { key ->
                keyDetailsPanel(key)
                createNodeDropdown(viewModel, key)
            }
            labelText("本地端口")
            modernTextField(draft.localPort, { viewModel.updateCreateDraft(localPort = it) }, placeholder = "8080")
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = viewModel::hideCreateTunnelDialog, shape = ModernTheme.shapeSmall, colors = ButtonDefaults.buttonColors(backgroundColor = ModernTheme.surfaceHover), elevation = ButtonDefaults.elevation(0.dp, 0.dp)) {
                    Text("取消", color = ModernTheme.textPrimary, modifier = Modifier.offset(y = DialogActionTextBaselineOffset))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                    val error = viewModel.createTunnelFromDraft()
                    if (error != null) onAlert(error)
                    },
                    enabled = canCreate,
                    shape = ModernTheme.shapeSmall,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ModernTheme.success,
                        disabledBackgroundColor = ModernTheme.surfaceHover,
                        contentColor = Color.White,
                        disabledContentColor = Color.White
                    ),
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp)
                ) {
                    Text(
                        "创建隧道",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(y = DialogActionTextBaselineOffset)
                    )
                }
            }
        }
    }
}

@Composable
fun keyDropdown(viewModel: NeoLinkViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val draft = viewModel.uiState.createDraft
    val selected = viewModel.availableKeysForCreate.firstOrNull { it.alias == draft.selectedKeyAlias }
    inlineDropdown(
        expanded = expanded,
        selectedText = selected?.alias ?: "选择密钥",
        emptyText = "没有可创建的密钥",
        leadingIcon = { Icon(Icons.Default.Settings, null, tint = ModernTheme.textSecondary, modifier = Modifier.size(16.dp)) },
        selectedTrailing = selected?.let { key -> { keyTypeBadge(key) } },
        onToggle = { expanded = !expanded }
    ) {
        if (viewModel.availableKeysForCreate.isEmpty()) {
            inlineDropdownItem("没有可创建的密钥", "请先刷新 NAS 密钥或删除本地隧道", enabled = false) {}
        } else {
            viewModel.availableKeysForCreate.forEach { key ->
                inlineDropdownItem(
                    primary = key.alias,
                    secondary = keySummaryText(key),
                    primaryColor = keyTypeColor(key),
                    trailing = { keyTypeBadge(key) }
                ) {
                    viewModel.updateCreateDraft(keyAlias = key.alias)
                    expanded = false
                }
            }
        }
    }
}

@Composable
fun createNodeDropdown(viewModel: NeoLinkViewModel, key: NasKey) {
    var expanded by remember { mutableStateOf(false) }
    val onlineNodes = key.onlineNodes
    val selected = onlineNodes.firstOrNull { it.nodeId == viewModel.uiState.createDraft.selectedNodeId } ?: onlineNodes.firstOrNull()
    val pingResults = rememberNodePingResults(expanded, onlineNodes)
    labelText("节点选择")
    inlineDropdown(
        expanded = expanded,
        selectedText = selected?.displayName ?: "无可用节点",
        emptyText = "无可用节点",
        selectedColor = if (selected == null) ModernTheme.error else ModernTheme.textPrimary,
        leadingIcon = selected?.let { node -> { nodeSvgIcon(node, 16.dp) } },
        onToggle = { expanded = !expanded }
    ) {
        if (onlineNodes.isEmpty()) {
            inlineDropdownItem("无可用节点", "NAS 当前没有返回在线节点", enabled = false) {}
        } else {
            onlineNodes.forEach { node ->
                inlineDropdownItem(
                    primary = node.displayName,
                    secondary = "${node.address}:${node.hookPort}/${node.connectPort}",
                    primaryColor = ModernTheme.success,
                    leading = { nodeSvgIcon(node, 16.dp) },
                    trailing = { nodeLatencyText(pingResults[node.pingKey()]) }
                ) {
                    viewModel.updateCreateDraft(nodeId = node.nodeId)
                    expanded = false
                }
            }
        }
    }
}

private const val NodePingTimeoutMs = 1_000
private const val TunnelListTunnelStartIndex = 2
private const val TunnelExpandRevealDelayMs = 260L
private const val TunnelExpandRevealPaddingPx = 18f
private const val MaxInlineSvgLength = 16_384
private val FormalKeyPurple = Color(0xFF7C3AED)
private val LatencyWarning = Color(0xFFFACC15)
private val TunnelStatusBarBaselineOffset = (-1.5).dp
private val TunnelHeaderPortLabelBaselineOffset = (-4).dp
private val MetricCardTextBaselineOffset = (-3.5).dp
private val MetricCardValueBaselineOffset = (-3).dp
private val CompactFieldBaselineOffset = (-2).dp
private val CompactPortFieldBaselineOffset = (-1.5).dp
private val CompactFieldPlaceholderBaselineOffset = (-1.5).dp
private val MaterialButtonTextBaselineOffset = (-2).dp
private val DialogActionTextBaselineOffset = (-0.5).dp
private val RemainingTrafficTextBaselineOffset = (-2).dp
private val CreateEntryTextBaselineOffset = (-2).dp
private val KeyDetailsHeaderTextBaselineOffset = (-2).dp
private val KeyMetricLabelBaselineOffset = (-2).dp
private val KeyMetricValueBaselineOffset = (-2).dp
private val KeyTypeBadgeTextBaselineOffset = (-2.5).dp
private val NodeLatencyTextBaselineOffset = (-2).dp
private val StopIcon: ImageVector = ImageVector.Builder(
    name = "Stop",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 6f)
        horizontalLineTo(18f)
        verticalLineTo(18f)
        horizontalLineTo(6f)
        close()
    }
}.build()

@Composable
private fun keyDetailsPanel(key: NasKey) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(ModernTheme.recessedBrush, ModernTheme.shapeSmall)
            .border(1.dp, ModernTheme.border, ModernTheme.shapeSmall)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 密钥标题、徽标、延迟等小字号文本与图标/边框胶囊同排时，
            // Windows 字体边界会让视觉中心略低，沿用 7.x 的上移补偿。
            Text(
                "密钥参数",
                color = ModernTheme.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.offset(y = KeyDetailsHeaderTextBaselineOffset)
            )
            Spacer(Modifier.width(8.dp))
            keyTypeBadge(key)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            keyMetric("流量", "${formatTrafficMiB(key.balanceMiB)} MiB", Modifier.weight(1f), ModernTheme.success)
            keyMetric("到期时间", key.expire.ifBlank { "N/A" }, Modifier.weight(1.35f), ModernTheme.accentLight)
            keyMetric("带宽", formatBandwidth(key.rate), Modifier.weight(0.85f), keyTypeColor(key))
        }
    }
}

@Composable
private fun keyMetric(label: String, value: String, modifier: Modifier, valueColor: Color) {
    Column(modifier) {
        Text(label, color = ModernTheme.textSecondary, fontSize = 11.sp, modifier = Modifier.offset(y = KeyMetricLabelBaselineOffset))
        Spacer(Modifier.height(2.dp))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = KeyMetricValueBaselineOffset))
    }
}

@Composable
private fun keyTypeBadge(key: NasKey) {
    Box(
        modifier = Modifier
            .background(keyTypeBackground(key), RoundedCornerShape(4.dp))
            .border(1.dp, keyTypeColor(key).copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(key.displayType, color = keyTypeColor(key), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = KeyTypeBadgeTextBaselineOffset))
    }
}

private fun keySummaryText(key: NasKey, prefix: String = ""): String {
    val flow = formatTrafficMiB(key.balanceMiB)
    val expire = key.expire.ifBlank { "N/A" }
    val bandwidth = formatBandwidth(key.rate)
    return "${prefix}流量 $flow MiB / 到期 $expire / 带宽 $bandwidth"
}

private fun formatTrafficMiB(value: Double): String {
    return String.format(Locale.ROOT, "%.3f", value)
}

private fun formatBandwidth(value: String): String {
    val normalized = value.trim()
    if (normalized.isBlank() || normalized == "N/A" || normalized == "-") {
        return "N/A"
    }
    return if (normalized.contains("bps", ignoreCase = true)) normalized else "$normalized Mbps"
}

private fun keyTypeBackground(key: NasKey): Color {
    return if (key.isTrial) ModernTheme.success.copy(alpha = 0.16f) else FormalKeyPurple.copy(alpha = 0.18f)
}

private fun keyTypeColor(key: NasKey): Color {
    return if (key.isTrial) ModernTheme.success else FormalKeyPurple
}

@Composable
private fun rememberNodePingResults(expanded: Boolean, nodes: List<NasNode>): Map<String, String> {
    val pingResults = remember { mutableStateMapOf<String, String>() }
    val nodeKeys = nodes.map { it.pingKey() }
    LaunchedEffect(expanded, nodeKeys) {
        if (!expanded) {
            return@LaunchedEffect
        }
        pingResults.keys.retainAll(nodeKeys.toSet())
        nodes.forEach { node ->
            val pingKey = node.pingKey()
            if (node.address.isBlank() || node.hookPort !in 1..65535) {
                pingResults[pingKey] = "超时"
                return@forEach
            }
            pingResults[pingKey] = "测速中"
            launch(Dispatchers.IO) {
                val result = try {
                    val latency = TcpPingUtil.ping(node.address, node.hookPort, NodePingTimeoutMs)
                    if (latency == -1 || latency >= NodePingTimeoutMs) "超时" else "${latency}ms"
                } catch (_: Exception) {
                    "超时"
                }
                withContext(Dispatchers.Main) {
                    pingResults[pingKey] = result
                }
            }
        }
    }
    return pingResults
}

private fun NasNode.pingKey(): String {
    return nodeId.ifBlank { "${address}:${hookPort}:${connectPort}:${displayName}" }
}

@Composable
private fun nodeSvgIcon(node: NasNode, size: Dp) {
    svgIcon(node.icon, size)
}

@Composable
private fun nodeLatencyText(result: String?) {
    if (result.isNullOrBlank()) {
        return
    }
    Text(
        text = result,
        color = nodeLatencyColor(result),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.offset(y = NodeLatencyTextBaselineOffset)
    )
}

private fun nodeLatencyColor(result: String): Color {
    if (result == "测速中") {
        return ModernTheme.textSecondary
    }
    if (result == "超时") {
        return ModernTheme.error
    }
    val latencyMs = result.removeSuffix("ms").toIntOrNull() ?: return ModernTheme.error
    return when {
        latencyMs <= 99 -> ModernTheme.success
        latencyMs <= 200 -> LatencyWarning
        else -> ModernTheme.error
    }
}

@Composable
private fun svgIcon(svgContent: String?, iconSize: Dp) {
    if (svgContent.isNullOrBlank() || svgContent.length > MaxInlineSvgLength) {
        Canvas(modifier = Modifier.size(iconSize)) {
            drawCircle(Color(0xFF3B82F6), style = Stroke(width = 2f))
        }
        return
    }

    val drawInstructions = remember(svgContent) {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            configureSecureXmlFactory(factory)
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            val document = builder.parse(ByteArrayInputStream(svgContent.toByteArray(Charsets.UTF_8)))
            val root = document.documentElement
            val viewBoxParts = root.getAttribute("viewBox").split(Regex("[\\s,]+")).filter { it.isNotBlank() }
            val viewBox = if (viewBoxParts.size == 4) {
                val minX = viewBoxParts[0].toFloat()
                val minY = viewBoxParts[1].toFloat()
                val width = viewBoxParts[2].toFloat()
                val height = viewBoxParts[3].toFloat()
                Rect(
                    minX,
                    minY,
                    minX + width,
                    minY + height
                )
            } else {
                Rect(
                    0f,
                    0f,
                    root.getAttribute("width").toFloatOrNull() ?: 900f,
                    root.getAttribute("height").toFloatOrNull() ?: 600f
                )
            }
            val operations = mutableListOf<SvgDrawOp>()
            parseSvgLayer(root, operations)
            if (viewBox.width > 0f && viewBox.height > 0f && operations.isNotEmpty()) {
                viewBox to operations
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    if (drawInstructions == null) {
        Canvas(modifier = Modifier.size(iconSize)) {
            drawCircle(Color(0xFF3B82F6), style = Stroke(width = 2f))
        }
        return
    }

    val (viewBox, operations) = drawInstructions
    Canvas(modifier = Modifier.size(iconSize)) {
        val scaleX = size.width / viewBox.width
        val scaleY = size.height / viewBox.height
        val finalScale = minOf(scaleX, scaleY)
        val drawWidth = viewBox.width * finalScale
        val drawHeight = viewBox.height * finalScale
        val offsetX = (size.width - drawWidth) / 2f
        val offsetY = (size.height - drawHeight) / 2f
        translate(left = offsetX, top = offsetY) {
            scale(scale = finalScale, pivot = Offset.Zero) {
                translate(left = -viewBox.left, top = -viewBox.top) {
                    operations.forEach { operation ->
                        when (operation) {
                            is SvgDrawOp.PathOp -> drawPath(operation.path, operation.color)
                            is SvgDrawOp.RectOp -> drawRect(operation.color, operation.topLeft, operation.size)
                            is SvgDrawOp.CircleOp -> drawCircle(operation.color, operation.radius, operation.center)
                        }
                    }
                }
            }
        }
    }
}

private fun configureSecureXmlFactory(factory: DocumentBuilderFactory) {
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    factory.isXIncludeAware = false
    factory.isExpandEntityReferences = false
}

private sealed class SvgDrawOp {
    data class PathOp(val path: Path, val color: Color) : SvgDrawOp()
    data class RectOp(val topLeft: Offset, val size: Size, val color: Color) : SvgDrawOp()
    data class CircleOp(val center: Offset, val radius: Float, val color: Color) : SvgDrawOp()
}

private fun parseSvgLayer(element: Element, operations: MutableList<SvgDrawOp>) {
    val children = element.childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        if (child.nodeType != org.w3c.dom.Node.ELEMENT_NODE) {
            continue
        }
        val childElement = child as Element
        val color = parseSvgFillColor(childElement.getAttribute("fill"))
        when (childElement.tagName.lowercase(Locale.ROOT)) {
            "path" -> {
                val data = childElement.getAttribute("d")
                if (data.isNotBlank()) {
                    operations.add(SvgDrawOp.PathOp(PathParser().parsePathString(data).toPath(), color))
                }
            }

            "rect" -> operations.add(
                SvgDrawOp.RectOp(
                    Offset(
                        childElement.getAttribute("x").toFloatOrNull() ?: 0f,
                        childElement.getAttribute("y").toFloatOrNull() ?: 0f
                    ),
                    Size(
                        childElement.getAttribute("width").toFloatOrNull() ?: 0f,
                        childElement.getAttribute("height").toFloatOrNull() ?: 0f
                    ),
                    color
                )
            )

            "circle" -> operations.add(
                SvgDrawOp.CircleOp(
                    Offset(
                        childElement.getAttribute("cx").toFloatOrNull() ?: 0f,
                        childElement.getAttribute("cy").toFloatOrNull() ?: 0f
                    ),
                    childElement.getAttribute("r").toFloatOrNull() ?: 0f,
                    color
                )
            )
        }
        parseSvgLayer(childElement, operations)
    }
}

private fun parseSvgFillColor(rawFill: String): Color {
    val normalizedFill = rawFill.trim().replace("'", "")
    if (normalizedFill.isBlank() || normalizedFill.equals("none", ignoreCase = true)) {
        return ModernTheme.textPrimary
    }
    return try {
        val parsed = Color(java.awt.Color.decode(normalizedFill).rgb).copy(alpha = 1f)
        if (parsed == Color.Black) ModernTheme.textPrimary else parsed
    } catch (_: Exception) {
        ModernTheme.textPrimary
    }
}
