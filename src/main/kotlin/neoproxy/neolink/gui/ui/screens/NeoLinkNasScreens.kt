package neoproxy.neolink.gui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import neoproxy.neolink.gui.model.IdentityStatus
import neoproxy.neolink.gui.model.NasAnnouncement
import neoproxy.neolink.gui.model.NasKey
import neoproxy.neolink.gui.state.NeoLinkViewModel
import neoproxy.neolink.gui.ui.components.labelText
import neoproxy.neolink.gui.ui.components.modernTextField
import neoproxy.neolink.gui.ui.components.primaryButton
import neoproxy.neolink.gui.ui.components.secondaryButton
import neoproxy.neolink.gui.ui.components.sectionCard
import neoproxy.neolink.gui.ui.components.sectionTitle
import neoproxy.neolink.gui.ui.theme.ModernTheme
import java.awt.Desktop
import java.net.URI
import java.util.Locale

@Composable
fun purchasePage(viewModel: NeoLinkViewModel) {
    LaunchedEffect(Unit) { viewModel.refreshNasDashboard() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        identityStatusCard(viewModel)
        sectionCard {
            sectionTitle("购买服务")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    labelText("流量 (GiB)")
                    modernTextField(viewModel.nasState.purchaseDraft.trafficGiB, viewModel::updatePurchaseTraffic, placeholder = "10")
                    labelText("时长 (天)")
                    modernTextField(viewModel.nasState.purchaseDraft.days, viewModel::updatePurchaseDays, placeholder = "30")
                    labelText("带宽 (Mbps)")
                    modernTextField(viewModel.nasState.purchaseDraft.rateMbps, viewModel::updatePurchaseRate, placeholder = "10")
                }
                Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("订单总额", color = ModernTheme.textSecondary, fontSize = 12.sp)
                    Text("¥ ${formatMoney(viewModel.purchaseAmount())}", color = ModernTheme.success, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("请在 120 秒内完成支付，金额必须完全一致。", color = ModernTheme.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    primaryButton("创建订单", viewModel.nasState.paymentDialog.loading, viewModel::createPurchaseOrder)
                }
            }
            nasMessage(viewModel.nasState.message)
        }
        keyServicePanel(viewModel)
    }
}

@Composable
private fun keyServicePanel(viewModel: NeoLinkViewModel) {
    sectionCard {
        sectionTitle("密钥服务")
        Spacer(Modifier.height(10.dp))
        if (!viewModel.authState.isVerified) {
            Text("完成实名认证后可查看密钥、充值和重置序列号。", color = ModernTheme.textSecondary, fontSize = 13.sp)
            return@sectionCard
        }
        if (viewModel.keys.isEmpty()) {
            Text("暂无密钥。购买成功后会自动同步到这里。", color = ModernTheme.textSecondary, fontSize = 13.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.keys.forEach { key -> keyServiceRow(viewModel, key) }
            }
        }
    }
}

@Composable
private fun keyServiceRow(viewModel: NeoLinkViewModel, key: NasKey) {
    Row(
        Modifier.fillMaxWidth()
            .background(ModernTheme.recessedBrush, ModernTheme.shapeSmall)
            .border(1.dp, ModernTheme.border, ModernTheme.shapeSmall)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(key.alias, color = ModernTheme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("余额 ${formatMoney(key.balanceMiB)} MiB / 到期 ${key.expire.ifBlank { "-" }} / 剩余刷新 ${key.refreshRemainingToday}", color = ModernTheme.textSecondary, fontSize = 11.sp)
        }
        if (!key.isTrial) {
            compactAction("充值") { viewModel.showRechargeDialog(key.alias) }
            Spacer(Modifier.width(8.dp))
        }
        compactAction("刷新") { viewModel.showRefreshKeyDialog(key) }
    }
}

@Composable
fun downloadsPage(viewModel: NeoLinkViewModel) {
    sectionCard {
        sectionTitle("软件下载")
        Spacer(Modifier.height(12.dp))
        Text("选择对应平台下载，点击会记录到 NAS 下载日志。", color = ModernTheme.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            downloadButton(viewModel, "Windows", "win", "https://p.ceroxe.top:56000/win")
            downloadButton(viewModel, "macOS/JAR", "jar", "https://p.ceroxe.top:56000/jar")
            downloadButton(viewModel, "Linux", "linux", "https://p.ceroxe.top:56000/linux")
        }
    }
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
    modalSurface(width = 420) {
        sectionTitle(if (dialog.status == "SUCCESS") "支付成功" else "微信扫码支付")
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.width(180.dp).height(180.dp)
                .align(Alignment.CenterHorizontally)
                .background(Color.White, ModernTheme.shapeSmall),
            contentAlignment = Alignment.Center
        ) {
            Text("微信收款码", color = Color.Black, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("支付金额 ¥ ${formatMoney(dialog.amount)}", color = ModernTheme.success, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("订单号 ${dialog.orderId}", color = ModernTheme.textSecondary, fontSize = 11.sp)
        Text(if (dialog.status == "SUCCESS") "支付成功，密钥将自动刷新。" else "剩余 ${dialog.secondsLeft}s", color = ModernTheme.textSecondary, fontSize = 13.sp)
        if (dialog.message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(dialog.message, color = if (dialog.status == "SUCCESS") ModernTheme.success else ModernTheme.textSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        secondaryButton(if (dialog.status == "SUCCESS") "完成" else "关闭", viewModel::closePaymentDialog)
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
        sectionTitle(announcement.title.ifBlank { "公告" })
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
private fun modalSurface(width: Int, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)), contentAlignment = Alignment.Center) {
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
private fun compactAction(text: String, color: Color = ModernTheme.primary, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = ModernTheme.shapeSmall,
        colors = ButtonDefaults.buttonColors(backgroundColor = color),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier.offset(y = (-1).dp))
    }
}

@Composable
private fun downloadButton(viewModel: NeoLinkViewModel, title: String, platform: String, url: String) {
    Button(
        onClick = {
            viewModel.recordDownload(platform)
            runCatching {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
            }
        },
        shape = ModernTheme.shapeSmall,
        colors = ButtonDefaults.buttonColors(backgroundColor = ModernTheme.primary),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
    ) {
        Text(title, color = Color.White, fontSize = 13.sp, modifier = Modifier.offset(y = (-1).dp))
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
