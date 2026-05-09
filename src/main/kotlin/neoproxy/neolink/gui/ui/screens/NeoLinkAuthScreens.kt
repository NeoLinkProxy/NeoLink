package neoproxy.neolink.gui.ui.screens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import neoproxy.neolink.gui.model.AuthMode
import neoproxy.neolink.gui.state.NeoLinkViewModel
import neoproxy.neolink.gui.ui.components.labelText
import neoproxy.neolink.gui.ui.components.modernTextField
import neoproxy.neolink.gui.ui.components.primaryButton
import neoproxy.neolink.gui.ui.components.secondaryButton
import neoproxy.neolink.gui.ui.theme.ModernTheme

private val AuthHeaderTextIconBaselineOffset = (-1).dp

@Composable
fun authScreen(viewModel: NeoLinkViewModel) {
    val state = viewModel.authState
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        if (state.isRestoringSession) {
            sessionRestoringPanel(state.email)
        } else {
            Column(
                modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                authHeader(state.mode)
                Spacer(Modifier.height(6.dp))

                if (state.mode == AuthMode.VERIFY_IDENTITY) {
                    Text("用户: ${state.email}", color = ModernTheme.textSecondary, fontSize = 12.sp)
                    labelText("真实姓名")
                    modernTextField(state.realName, viewModel::updateRealName, placeholder = "请输入真实姓名")
                    labelText("身份证号")
                    modernTextField(state.idCard, viewModel::updateIdCard, placeholder = "请输入身份证号")
                    primaryButton("提交认证", state.isLoading, onClick = viewModel::verifyIdentity)
                    secondaryButton("退出登录", onClick = viewModel::logout)
                } else {
                    labelText("邮箱")
                    modernTextField(state.email, viewModel::updateEmail, placeholder = "name@example.com")
                    labelText("密码")
                    modernTextField(state.password, viewModel::updatePassword, placeholder = "请输入密码", isPassword = true)
                    if (state.mode == AuthMode.REGISTER) {
                        labelText("确认密码")
                        modernTextField(state.confirmPassword, viewModel::updateConfirmPassword, placeholder = "再次输入密码", isPassword = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            modernTextField(state.code, viewModel::updateCode, placeholder = "验证码", modifier = Modifier.weight(1f))
                            Button(
                                onClick = viewModel::sendCode,
                                enabled = !state.isLoading,
                                shape = ModernTheme.shapeSmall,
                                colors = ButtonDefaults.buttonColors(backgroundColor = ModernTheme.surfaceHover, contentColor = ModernTheme.textPrimary),
                                elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                                modifier = Modifier.height(34.dp).width(100.dp)
                            ) { Text("发验证码", fontSize = 12.sp) }
                        }
                        primaryButton("注册并登录", state.isLoading, onClick = viewModel::register)
                        secondaryButton("已有账号，去登录", onClick = { viewModel.switchAuthMode(AuthMode.LOGIN) })
                    } else {
                        primaryButton("登录", state.isLoading, onClick = viewModel::login)
                        secondaryButton("没有账号，去注册", onClick = { viewModel.switchAuthMode(AuthMode.REGISTER) })
                    }
                }
                if (state.message.isNotBlank()) {
                    Text(
                        state.message,
                        color = if (isAuthErrorMessage(state.message)) ModernTheme.error else ModernTheme.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun sessionRestoringPanel(email: String) {
    Column(
        modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        authHeader(AuthMode.LOGIN, titleOverride = "正在恢复会话", subtitleOverride = "正在验证本地会话，请稍候。")
        if (email.isNotBlank()) {
            Text("用户: $email", color = ModernTheme.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun authHeader(
    mode: AuthMode,
    titleOverride: String? = null,
    subtitleOverride: String? = null
) {
    val title = titleOverride ?: when (mode) {
        AuthMode.LOGIN -> "登录 NeoAuth"
        AuthMode.REGISTER -> "注册 NeoAuth"
        AuthMode.VERIFY_IDENTITY -> "实名认证"
    }
    val subtitle = subtitleOverride ?: when (mode) {
        AuthMode.LOGIN -> "登录后继续管理你的内网穿透节点。"
        AuthMode.REGISTER -> "创建账号后即可同步授权节点。"
        AuthMode.VERIFY_IDENTITY -> "完成认证后即可进入控制台。"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (mode == AuthMode.VERIFY_IDENTITY) Icons.Default.Check else Icons.Default.AccountCircle,
                null,
                tint = ModernTheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                color = ModernTheme.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                // 大号认证标题同样与图标共用一行；按主界面一致策略上移文本，
                // 以处理 Windows/Compose Desktop 的视觉基线不一致问题。
                modifier = Modifier.offset(y = AuthHeaderTextIconBaselineOffset)
            )
        }
        Text(subtitle, color = ModernTheme.textSecondary, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

private fun isAuthErrorMessage(message: String): Boolean =
    message.contains("失败") || message.contains("错误") || message.contains("失效")
