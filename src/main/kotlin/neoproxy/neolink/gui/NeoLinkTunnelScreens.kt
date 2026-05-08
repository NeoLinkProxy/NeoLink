package neoproxy.neolink.gui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun tunnelManagementPage(viewModel: NeoLinkViewModel, onAlert: (String) -> Unit) {
    LaunchedEffect(Unit) { viewModel.refreshKeys() }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            metricsRow(viewModel)
        }
        item {
            trafficCollapsible("实时总流量", viewModel.uiState.totalTrafficExpanded, viewModel.totalTrafficPoints, viewModel.totalTrafficBytes, viewModel::toggleTotalTraffic)
        }
        items(viewModel.tunnels, key = { it.id }) { tunnel ->
            tunnelCard(viewModel, tunnel, onAlert)
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
        modifier = modifier.height(82.dp).background(ModernTheme.surface, ModernTheme.shapeMedium)
            .border(1.dp, ModernTheme.border, ModernTheme.shapeMedium).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ModernTheme.textSecondary, fontSize = 12.sp)
        Text(value, color = ModernTheme.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun trafficCollapsible(title: String, expanded: Boolean, points: List<TrafficPoint>, totalBytes: Long, onToggle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(ModernTheme.surface, ModernTheme.shapeMedium)
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
                Box(Modifier.fillMaxWidth().height(150.dp).background(ModernTheme.terminalBg, ModernTheme.shapeSmall).border(1.dp, ModernTheme.border, ModernTheme.shapeSmall)) {
            trafficChart(points, Modifier.fillMaxSize().padding(10.dp), lineColor = TotalTrafficChartColor)
                }
            }
        }
    }
}

@Composable
fun tunnelCard(viewModel: NeoLinkViewModel, tunnel: TunnelCardState, onAlert: (String) -> Unit) {
    val runtime = viewModel.tunnelRuntime[tunnel.id] ?: TunnelRuntimeUiState()
    val key = viewModel.keys.firstOrNull { it.alias == tunnel.keyAlias }
    val expanded = tunnel.expanded
    val nameFocusRequester = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxWidth().background(ModernTheme.surface, ModernTheme.shapeMedium)
            .clip(ModernTheme.shapeMedium)
    ) {
        val toggleExpanded = { viewModel.toggleTunnelExpanded(tunnel.id) }
        collapsibleHeaderRow(
            onToggle = toggleExpanded,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            headerClickable = false,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(3.dp, 18.dp).offset(y = 1.dp)
                    .background(if (runtime.running) ModernTheme.success else ModernTheme.primary, RoundedCornerShape(2.dp))
            )
            tunnelNameEditor(
                tunnel.name,
                { viewModel.updateTunnelName(tunnel.id, it) },
                nameFocusRequester = nameFocusRequester
            )
            Spacer(
                Modifier.weight(1f)
                    .height(34.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = toggleExpanded
                    )
            )
            modernTextField(tunnel.localPort, { viewModel.updateTunnelLocalPort(tunnel.id, it) }, placeholder = "端口", modifier = Modifier.width(76.dp))
            compactToggle("TCP", tunnel.tcpEnabled) { viewModel.updateTunnelTcp(tunnel.id, it) }
            compactToggle("UDP", tunnel.udpEnabled) { viewModel.updateTunnelUdp(tunnel.id, it) }
            IconButton(onClick = {
                val error = if (runtime.running) null else viewModel.startTunnel(tunnel.id)
                if (runtime.running) viewModel.stopTunnel(tunnel.id)
                if (error != null) onAlert(error)
            }, modifier = Modifier.size(34.dp)) {
                if (runtime.running) {
                    Canvas(Modifier.size(15.dp)) {
                        drawRect(ModernTheme.error, size = Size(size.width, size.height))
                    }
                } else {
                    Icon(Icons.Default.PlayArrow, null, tint = ModernTheme.success)
                }
            }
            IconButton(onClick = { viewModel.deleteTunnel(tunnel.id) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Delete, null, tint = ModernTheme.error)
            }
            tunnelHeaderExpandedIndicator(expanded, toggleExpanded)
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Divider(color = ModernTheme.divider)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    metricCard("本隧道连接", runtime.activeConnections.toString(), Modifier.width(120.dp))
                    Box(Modifier.weight(1f).height(88.dp).background(ModernTheme.terminalBg, ModernTheme.shapeSmall).border(1.dp, ModernTheme.border, ModernTheme.shapeSmall)) {
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
        Modifier.size(34.dp)
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
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun tunnelNameEditor(value: String, onValueChange: (String) -> Unit, nameFocusRequester: FocusRequester) {
    val visibleText = value.ifBlank { "隧道" }
    val fieldWidth = (visibleText.length * 14).dp.coerceIn(44.dp, 138.dp)
    val buttonSize = 26.dp
    val gap = 6.dp
    Row(
        modifier = Modifier.width(fieldWidth + gap + buttonSize).height(34.dp),
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
                Box(Modifier.width(fieldWidth).height(34.dp), contentAlignment = Alignment.CenterStart) {
                    innerTextField()
                }
            },
            modifier = Modifier.width(fieldWidth).focusRequester(nameFocusRequester)
        )
        inlineIconButton(onClick = { nameFocusRequester.requestFocus() }) {
            Icon(Icons.Default.Edit, "重命名隧道", tint = ModernTheme.textSecondary, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
fun inlineIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(26.dp)
            .clip(ModernTheme.shapeSmall)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
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
    Column {
        labelText("节点选择")
        inlineDropdown(
            expanded = expanded,
            selectedText = tunnel.selectedNodeName.ifBlank { "选择节点" },
            emptyText = "无可用节点",
            onToggle = { expanded = !expanded }
        ) {
            key.onlineNodes.forEach { node ->
                inlineDropdownItem(
                    primary = node.displayName,
                    secondary = "${node.address}:${node.hookPort}/${node.connectPort}",
                    primaryColor = ModernTheme.success
                ) {
                    viewModel.selectTunnelNode(tunnel.id, node)
                    expanded = false
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            key.availableNodes.forEach { node ->
                Text(node.displayName, color = if (node.isOnline) ModernTheme.success else ModernTheme.error, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun remainingTrafficRing(tunnel: TunnelCardState, runtime: TunnelRuntimeUiState) {
    val totalBytes = (tunnel.keyBalanceMiB * 1024.0 * 1024.0).toLong().coerceAtLeast(0L)
    val remainingBytes = (totalBytes - runtime.trafficSinceBalanceSyncBytes).coerceAtLeast(0L)
    val progress = if (totalBytes <= 0L) 0f else remainingBytes.toFloat() / totalBytes.toFloat()
    Column(Modifier.width(120.dp).height(88.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(48.dp)) {
            val stroke = 5.dp.toPx()
            drawCircle(ModernTheme.border, style = Stroke(stroke))
            drawArc(ModernTheme.success, -90f, 360f * progress, false, style = Stroke(stroke))
        }
        Spacer(Modifier.height(6.dp))
        Text("%.3f MiB".format(remainingBytes / 1024.0 / 1024.0), color = ModernTheme.textSecondary, fontSize = 11.sp)
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
        Modifier.fillMaxWidth().height(150.dp).background(ModernTheme.terminalBg, ModernTheme.shapeSmall)
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
    Box(
        modifier = Modifier.fillMaxWidth().height(76.dp)
            .background(ModernTheme.surface.copy(alpha = 0.55f), ModernTheme.shapeMedium)
            .border(1.dp, ModernTheme.border, ModernTheme.shapeMedium)
            .clickable { viewModel.showCreateTunnelDialog() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, null, tint = ModernTheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("创建隧道", color = ModernTheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun createTunnelDialog(viewModel: NeoLinkViewModel, onAlert: (String) -> Unit) {
    val draft = viewModel.uiState.createDraft
    val selectedKey = viewModel.keys.firstOrNull { it.alias == draft.selectedKeyAlias }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(460.dp).background(Color(0xFF1E1E20), ModernTheme.shapeMedium)
                .border(1.dp, ModernTheme.border, ModernTheme.shapeMedium).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("创建隧道", color = ModernTheme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            keyDropdown(viewModel)
            selectedKey?.let { key ->
                Text("密钥参数：${key.displayType} / 余额 ${"%.3f".format(key.balanceMiB)} MiB / 速率 ${key.rate} / 到期 ${key.expire}", color = ModernTheme.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    key.availableNodes.forEach { node ->
                        Text(node.displayName, color = if (node.isOnline) ModernTheme.success else ModernTheme.error, fontSize = 11.sp)
                    }
                }
                createNodeDropdown(viewModel, key)
            }
            labelText("本地端口")
            modernTextField(draft.localPort, { viewModel.updateCreateDraft(localPort = it) }, placeholder = "8080")
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = viewModel::hideCreateTunnelDialog, shape = ModernTheme.shapeSmall, colors = ButtonDefaults.buttonColors(backgroundColor = ModernTheme.surfaceHover), elevation = ButtonDefaults.elevation(0.dp, 0.dp)) {
                    Text("取消", color = ModernTheme.textPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val error = viewModel.createTunnelFromDraft()
                    if (error != null) onAlert(error)
                }, shape = ModernTheme.shapeSmall, colors = ButtonDefaults.buttonColors(backgroundColor = ModernTheme.primary), elevation = ButtonDefaults.elevation(0.dp, 0.dp)) {
                    Text("创建隧道", color = Color.White)
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
        selectedText = selected?.let { "${it.alias} · ${it.displayType}" } ?: "选择密钥",
        emptyText = "没有可创建的密钥",
        leadingIcon = { Icon(Icons.Default.Settings, null, tint = ModernTheme.textSecondary, modifier = Modifier.size(16.dp)) },
        onToggle = { expanded = !expanded }
    ) {
        if (viewModel.availableKeysForCreate.isEmpty()) {
            inlineDropdownItem("没有可创建的密钥", "请先刷新 NAS 密钥或删除本地隧道", enabled = false) {}
        } else {
            viewModel.availableKeysForCreate.forEach { key ->
                inlineDropdownItem(
                    primary = "${key.alias} · ${key.displayType}",
                    secondary = "余额 ${"%.3f".format(key.balanceMiB)} MiB / 节点 ${key.availableNodesConsole.ifBlank { "N/A" }}"
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
    val selected = key.onlineNodes.firstOrNull { it.nodeId == viewModel.uiState.createDraft.selectedNodeId } ?: key.onlineNodes.firstOrNull()
    labelText("节点选择")
    inlineDropdown(
        expanded = expanded,
        selectedText = selected?.displayName ?: "无可用节点",
        emptyText = "无可用节点",
        selectedColor = if (selected == null) ModernTheme.error else ModernTheme.textPrimary,
        onToggle = { expanded = !expanded }
    ) {
        if (key.onlineNodes.isEmpty()) {
            inlineDropdownItem("无可用节点", "NAS 当前没有返回在线节点", enabled = false) {}
        } else {
            key.onlineNodes.forEach { node ->
                inlineDropdownItem(
                    primary = node.displayName,
                    secondary = "${node.address}:${node.hookPort}/${node.connectPort}",
                    primaryColor = ModernTheme.success
                ) {
                    viewModel.updateCreateDraft(nodeId = node.nodeId)
                    expanded = false
                }
            }
        }
    }
}
