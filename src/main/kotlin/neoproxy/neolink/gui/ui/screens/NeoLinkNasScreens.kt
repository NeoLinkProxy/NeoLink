package neoproxy.neolink.gui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import neoproxy.neolink.gui.model.IdentityStatus
import neoproxy.neolink.gui.model.NasAnnouncement
import neoproxy.neolink.gui.model.NasKey
import neoproxy.neolink.gui.state.NeoLinkViewModel
import neoproxy.neolink.gui.ui.components.labelText
import neoproxy.neolink.gui.ui.components.modalInputBarrier
import neoproxy.neolink.gui.ui.components.modernTextField
import neoproxy.neolink.gui.ui.components.primaryButton
import neoproxy.neolink.gui.ui.components.secondaryButton
import neoproxy.neolink.gui.ui.components.sectionCard
import neoproxy.neolink.gui.ui.components.sectionTitle
import neoproxy.neolink.gui.ui.theme.ModernTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun purchasePage(viewModel: NeoLinkViewModel) {
    LaunchedEffect(Unit) { viewModel.refreshNasDashboard() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        identityStatusCard(viewModel)
        sectionCard {
            sectionTitle("购买服务")
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    labelText("流量 (GiB)")
                    modernTextField(viewModel.nasState.purchaseDraft.trafficGiB, viewModel::updatePurchaseTraffic, placeholder = "10")
                    labelText("时长 (天)")
                    modernTextField(viewModel.nasState.purchaseDraft.days, viewModel::updatePurchaseDays, placeholder = "30")
                    rateLimitSlider(viewModel)
                }
                Column(
                    Modifier.width(300.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("订单总额", color = ModernTheme.textSecondary, fontSize = 12.sp)
                        Text("¥ ${formatMoney(viewModel.purchaseAmount())}", color = ModernTheme.success, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        paymentWarningCard(compact = true)
                        primaryButton("创建订单", viewModel.nasState.paymentDialog.loading, viewModel::createPurchaseOrder)
                    }
                }
            }
            nasMessage(viewModel.nasState.message)
        }
    }
}

@Composable
private fun rateLimitSlider(viewModel: NeoLinkViewModel) {
    val pricing = viewModel.nasState.pricing
    val maxRate = pricing.purchaseMaxRateMbps.coerceAtLeast(1)
    val currentRate = (viewModel.nasState.purchaseDraft.rateMbps.toIntOrNull() ?: 1).coerceIn(1, maxRate)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            labelText("端口速率限制")
            Text("$currentRate Mbps", color = ModernTheme.accentLight, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier)
        }
        Slider(
            value = currentRate.toFloat(),
            onValueChange = { value -> viewModel.updatePurchaseRate(value.roundToInt().coerceIn(1, maxRate).toString()) },
            valueRange = 1f..maxRate.toFloat(),
            steps = (maxRate - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = ModernTheme.success,
                activeTrackColor = ModernTheme.success,
                inactiveTrackColor = ModernTheme.borderStrong
            ),
            modifier = Modifier.fillMaxWidth().height(34.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1 Mbps", color = ModernTheme.textSecondary, fontSize = 10.sp)
            Text("$maxRate Mbps", color = ModernTheme.textSecondary, fontSize = 10.sp)
        }
        if (currentRate > pricing.rateLimitWarn) {
            Text(
                "超过 ${pricing.rateLimitWarn}Mbps 可能因服务器负载原因无法时刻跑满。",
                color = ModernTheme.warning,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
                    .background(ModernTheme.warning.copy(alpha = 0.10f), ModernTheme.shapeSmall)
                    .border(1.dp, ModernTheme.warning.copy(alpha = 0.30f), ModernTheme.shapeSmall)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun keyManagementPage(viewModel: NeoLinkViewModel) {
    LaunchedEffect(Unit) { viewModel.refreshKeys() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 14.dp)
    ) {
        item { identityStatusCard(viewModel) }
        item { keyServicePanel(viewModel) }
    }
}

@Composable
private fun keyServicePanel(viewModel: NeoLinkViewModel) {
    if (!viewModel.authState.isVerified) {
        sectionCard {
            sectionTitle("密钥管理")
            Spacer(Modifier.height(10.dp))
            Text("完成实名认证后可查看密钥、充值和重置序列号。", color = ModernTheme.textSecondary, fontSize = 13.sp)
        }
        return
    }
    if (viewModel.keys.isEmpty()) {
        sectionCard {
            sectionTitle("密钥管理")
            Spacer(Modifier.height(10.dp))
            Text("暂无密钥。购买成功后会自动同步到这里。", color = ModernTheme.textSecondary, fontSize = 13.sp)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        viewModel.keys.forEach { key ->
            keyServiceRow(viewModel, key)
        }
    }
}

@Composable
private fun keyServiceRow(viewModel: NeoLinkViewModel, key: NasKey) {
    Row(
        Modifier.fillMaxWidth()
            .height(KeyCardHeight)
            .background(ModernTheme.recessedBrush, KeyCardShape)
            .border(1.dp, keyTypeColor(key).copy(alpha = 0.30f), KeyCardShape)
            // 这里对齐隧道管理卡片的视觉密度：左侧不再保留过大的装饰留白，
            // 同时给底部元信息行留出稳定高度，避免 Windows 字体边界裁切字形。
            .padding(start = KeyCardStartPadding, end = 28.dp, top = 22.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    key.alias,
                    color = ModernTheme.textPrimary,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                )
                Spacer(Modifier.width(10.dp))
                keyTypeBadge(key)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                keyInfoText(Icons.Default.FiberManualRecord, keyStatusText(key), keyStatusColor(key), emphasized = keyStatusIsPositive(key))
                keyInfoDivider()
                keyInfoText(
                    Icons.Default.Storage,
                    "可用节点: ${formatAvailableNodes(key)}",
                    ModernTheme.textSecondary,
                    emphasizedValueColor = ModernTheme.success,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                keyInfoText(Icons.Default.AccountTree, "端口:${keyPortText(key)}", ModernTheme.textSecondary)
                keyInfoText(Icons.Default.Dns, "余额:${formatKeyTrafficMiB(key.balanceMiB, decimals = 2)} MiB", ModernTheme.textSecondary)
                keyInfoText(Icons.Default.Speed, "限速:${formatKeyBandwidth(key.rate)}", ModernTheme.textSecondary)
                keyInfoText(null, "到期:${key.expire.ifBlank { "N/A" }}", ModernTheme.textSecondary, modifier = Modifier.weight(1f, fill = false))
            }
        }
        Row(
            modifier = Modifier.width(if (key.isTrial) 94.dp else 202.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            keyOutlineAction(Icons.Default.Refresh, "刷新", ModernTheme.warning) { viewModel.showRefreshKeyDialog(key) }
            if (!key.isTrial) {
                Spacer(Modifier.width(10.dp))
                keyOutlineAction(Icons.Default.Bolt, "充值", Color.White) { viewModel.showRechargeDialog(key.alias) }
            }
        }
    }
}

@Composable
private fun keyInfoDivider() {
    Box(Modifier.padding(horizontal = 10.dp)) {
        Box(Modifier.width(1.dp).height(22.dp).background(ModernTheme.borderStrong.copy(alpha = 0.42f)))
    }
}

@Composable
private fun keyInfoText(
    icon: ImageVector?,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    emphasizedValueColor: Color? = null
) {
    Row(modifier = modifier.height(KeyMetaRowHeight), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = color.copy(alpha = if (color == ModernTheme.success) 1f else 0.70f), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        val valueColor = when {
            emphasized -> color
            emphasizedValueColor != null && text.contains(':') -> emphasizedValueColor
            else -> color
        }
        if (emphasizedValueColor != null && text.contains(':')) {
            val label = text.substringBefore(':') + ": "
            val value = text.substringAfter(':')
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = color.copy(alpha = 0.72f), fontSize = 12.sp, lineHeight = KeyMetaTextLineHeight, fontWeight = FontWeight.Bold)
                Text(value, color = valueColor, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(text, color = valueColor.copy(alpha = if (emphasized) 1f else 0.72f), fontSize = 12.sp, lineHeight = KeyMetaTextLineHeight, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun keyOutlineAction(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = ModernTheme.shapeSmall,
        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
        border = BorderStroke(1.dp, color.copy(alpha = 0.82f)),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatAvailableNodes(key: NasKey): String {
    return key.onlineNodes.joinToString("，") { it.displayName.ifBlank { it.nodeId } }.ifBlank { "无" }
}

private fun keyPortText(key: NasKey): String {
    val port = key.port.trim()
    return when (port.uppercase(Locale.ROOT)) {
        "FIXED" -> "固定"
        "RANDOM" -> "随机"
        else -> {
            if (port.contains('-')) {
                "随机"
            } else if (port.toIntOrNull() != null) {
                "固定"
            } else {
                port.ifBlank { "N/A" }
            }
        }
    }
}

private fun keyStatusText(key: NasKey): String {
    val status = key.status.trim()
    return when (status.uppercase(Locale.ROOT)) {
        "ACTIVE", "RUNNING", "NORMAL", "VALID", "OK", "ENABLED" -> "运行中"
        "EXPIRED", "INVALID" -> "已失效"
        "DISABLED", "LOCKED", "BANNED" -> "已停用"
        else -> status.ifBlank { "N/A" }
    }
}

private fun keyStatusColor(key: NasKey): Color {
    return if (keyStatusIsPositive(key)) ModernTheme.success else ModernTheme.textSecondary
}

private fun keyStatusIsPositive(key: NasKey): Boolean {
    return key.status.trim().uppercase(Locale.ROOT) in setOf("ACTIVE", "RUNNING", "NORMAL", "VALID", "OK", "ENABLED")
}

@Composable
fun tutorialPage(viewModel: NeoLinkViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        identityStatusCard(viewModel)
        sectionCard {
            sectionTitle("使用教程")
            Spacer(Modifier.height(10.dp))
            Text("1. 完成实名认证后购买或领取服务密钥。", color = ModernTheme.textSecondary, fontSize = 13.sp)
            Text("2. 回到隧道管理页创建隧道，选择密钥和在线节点。", color = ModernTheme.textSecondary, fontSize = 13.sp)
            Text("3. 填写本地端口后启动隧道，连接日志会在卡片内显示。", color = ModernTheme.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
fun nasGlobalDialogs(viewModel: NeoLinkViewModel) {
    if (viewModel.nasState.rechargeDialogVisible) rechargeDialog(viewModel)
    if (viewModel.nasState.paymentDialog.visible) paymentDialog(viewModel)
    if (viewModel.nasState.refreshKeyDialog.visible) refreshKeyDialog(viewModel)
    if (viewModel.nasState.announcementDialogVisible) announcementDialog(viewModel)
}

@Composable
private fun identityStatusCard(viewModel: NeoLinkViewModel) {
    val status = viewModel.nasState.identityStatus
    val verified = viewModel.authState.isVerified || status == IdentityStatus.VERIFIED
    sectionCard {
        sectionTitle("实名认证", color = if (verified) ModernTheme.success else ModernTheme.error)
        Spacer(Modifier.height(10.dp))
        when {
            status == IdentityStatus.LOCKED -> Text("账户已锁定，请退出登录并联系管理员。", color = ModernTheme.error, fontSize = 13.sp)
            verified -> Text("已完成实名认证，可正常购买和使用服务。", color = ModernTheme.success, fontSize = 13.sp)
            else -> {
                Text("需要完成实名认证才能购买服务和创建可用隧道。", color = ModernTheme.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                labelText("真实姓名")
                modernTextField(viewModel.authState.realName, viewModel::updateRealName, placeholder = "请输入真实姓名")
                Spacer(Modifier.height(8.dp))
                labelText("身份证号")
                modernTextField(viewModel.authState.idCard, viewModel::updateIdCard, placeholder = "请输入身份证号")
                Spacer(Modifier.height(10.dp))
                primaryButton("提交认证", viewModel.authState.isLoading, viewModel::verifyIdentity)
            }
        }
    }
}

@Composable
private fun rechargeDialog(viewModel: NeoLinkViewModel) {
    modalSurface(width = 420) {
        sectionTitle("服务充值")
        Spacer(Modifier.height(12.dp))
        Text("目标密钥: ${viewModel.nasState.rechargeDraft.targetKey}", color = ModernTheme.textPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        labelText("增加流量 (GiB)")
        modernTextField(viewModel.nasState.rechargeDraft.trafficGiB, viewModel::updateRechargeTraffic)
        Spacer(Modifier.height(8.dp))
        labelText("延长时长 (天)")
        modernTextField(viewModel.nasState.rechargeDraft.days, viewModel::updateRechargeDays)
        Spacer(Modifier.height(12.dp))
        Text("支付金额 ¥ ${formatMoney(viewModel.rechargeAmount())}", color = ModernTheme.success, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            compactAction("取消") { viewModel.hideRechargeDialog() }
            Spacer(Modifier.width(8.dp))
            compactAction("创建订单", ModernTheme.success) { viewModel.createRechargeOrder() }
        }
    }
}

@Composable
private fun paymentDialog(viewModel: NeoLinkViewModel) {
    val dialog = viewModel.nasState.paymentDialog
    val isSuccess = dialog.status == "SUCCESS"
    val isTerminal = isSuccess || dialog.timedOut
    modalSurface(width = 520) {
        sectionTitle(if (dialog.status == "SUCCESS") "支付成功" else "微信扫码支付")
        Spacer(Modifier.height(12.dp))
        if (!isSuccess) {
            paymentWarningCard(compact = false)
            Spacer(Modifier.height(12.dp))
        }
        Box(
            Modifier.width(196.dp).height(196.dp)
                .align(Alignment.CenterHorizontally)
                .background(Color.White, ModernTheme.shapeSmall)
                .border(1.dp, ModernTheme.borderStrong, ModernTheme.shapeSmall)
                .padding(10.dp)
                .then(if (isTerminal) Modifier.blur(14.dp).alpha(0.18f) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource("wxpay.png"),
                contentDescription = "微信收款码",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "支付金额 ¥ ${formatMoney(dialog.amount)}",
            color = ModernTheme.success,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text("订单号 ${dialog.orderId}", color = ModernTheme.textSecondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        if (!isSuccess) {
            Text(
                "${dialog.secondsLeft.coerceAtLeast(0)}s",
                color = if (dialog.timedOut) ModernTheme.error else ModernTheme.warning,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            if (dialog.timedOut) {
                Text("订单已超时，二维码已失效，请关闭后重新创建订单。", color = ModernTheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (dialog.message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                dialog.message,
                color = if (dialog.status == "SUCCESS") ModernTheme.success else ModernTheme.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        if (isTerminal) {
            Spacer(Modifier.height(14.dp))
            secondaryButton(if (isSuccess) "完成" else "关闭", viewModel::closePaymentDialog)
        }
    }
}

@Composable
private fun paymentWarningCard(compact: Boolean) {
    Column(
        Modifier.fillMaxWidth()
            .background(ModernTheme.error.copy(alpha = 0.08f), ModernTheme.shapeSmall)
            .border(BorderStroke(1.dp, ModernTheme.error.copy(alpha = 0.45f)), ModernTheme.shapeSmall)
            .padding(if (compact) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = ModernTheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("支付警告", color = Color.White, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier)
        }
        if (!compact) Divider(color = ModernTheme.error.copy(alpha = 0.22f))
        Text("请在 120 秒内完成支付。", color = ModernTheme.error.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("金额必须完全一致，不能多付或者少付。", color = ModernTheme.error.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("掉单请加 QQ 群 1035440745 联系群主。", color = ModernTheme.error.copy(alpha = 0.92f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun refreshKeyDialog(viewModel: NeoLinkViewModel) {
    val dialog = viewModel.nasState.refreshKeyDialog
    modalSurface(width = 420) {
        sectionTitle("重置序列号", color = ModernTheme.error)
        Spacer(Modifier.height(12.dp))
        Text("即将刷新密钥：${dialog.keyName}", color = ModernTheme.textPrimary, fontSize = 13.sp)
        Text("旧设备会被强制踢出，今日剩余刷新次数：${dialog.remainingToday}", color = ModernTheme.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            compactAction("取消") { viewModel.hideRefreshKeyDialog() }
            Spacer(Modifier.width(8.dp))
            compactAction(if (dialog.loading) "处理中" else "确认刷新", ModernTheme.error) { viewModel.submitRefreshKey() }
        }
    }
}

@Composable
private fun announcementDialog(viewModel: NeoLinkViewModel) {
    val announcement = viewModel.nasState.announcements.getOrNull(viewModel.nasState.announcementIndex) ?: return
    modalSurface(width = 520) {
        announcementTitle(announcement.title.ifBlank { "公告" })
        Spacer(Modifier.height(12.dp))
        Text(renderAnnouncementText(announcement), color = ModernTheme.textSecondary, fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (announcement.allowDismiss) {
                compactAction("不再显示") { viewModel.closeCurrentAnnouncement(dismissed = true) }
                Spacer(Modifier.width(8.dp))
            }
            compactAction("我知道了", ModernTheme.success) { viewModel.closeCurrentAnnouncement(dismissed = false) }
        }
    }
}

@Composable
private fun announcementTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(3.dp, 14.dp)
                
                .background(AnnouncementMarkerColor, RoundedCornerShape(2.dp))
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
private fun modalSurface(width: Int, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .modalInputBarrier()
            .background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(width.dp).widthIn(max = 560.dp)
                .background(ModernTheme.panelBrush, ModernTheme.shapeMedium)
                .border(1.dp, ModernTheme.borderStrong, ModernTheme.shapeMedium)
                .padding(18.dp),
            content = content
        )
    }
}

@Composable
private fun compactAction(text: String, color: Color = ModernTheme.success, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = ModernTheme.shapeSmall,
        colors = ButtonDefaults.buttonColors(backgroundColor = color),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier)
    }
}

@Composable
private fun nasMessage(message: String) {
    if (message.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(message, color = ModernTheme.error, fontSize = 12.sp)
    }
}

private fun renderAnnouncementText(announcement: NasAnnouncement): String {
    return announcement.content
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .trim()
}

private fun formatMoney(value: Double): String {
    return String.format(Locale.ROOT, "%.2f", value)
}

private val KeyCardShape = RoundedCornerShape(14.dp)
private val KeyCardHeight = 158.dp
private val KeyCardStartPadding = 20.dp
private val KeyMetaRowHeight = 20.dp
private val KeyMetaTextLineHeight = 16.sp
private val AnnouncementMarkerColor = Color(0xFF8B5CF6)
