package neoproxy.neolink.gui.ui.screens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Warning
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

private val AuthMessageReservedHeight = 20.dp

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
                    Text("用户: ${state.email}", color = ModernTheme.textSecondary, fontSize = 12.sp, modifier = Modifier)
                    labelText("真实姓名")
                    modernTextField(state.realName, viewModel::updateRealName, placeholder = "请输入真实姓名")
                    labelText("身份证号")
                    modernTextField(state.idCard, viewModel::updateIdCard, placeholder = "请输入身份证号")
                    primaryButton("提交认证", state.isLoading, onClick = viewModel::verifyIdentity)
                    secondaryButton("退出登录", onClick = viewModel::logout)
                } else {
                    labelText("邮箱")
                    modernTextField(state.email, viewModel::updateEmail, placeholder = "name@example.com")
                    labelText(if (state.mode == AuthMode.RESET_PASSWORD) "新密码" else "密码")
                    modernTextField(
                        state.password,
                        viewModel::updatePassword,
                        placeholder = if (state.mode == AuthMode.RESET_PASSWORD) "设置新密码" else "请输入密码",
                        isPassword = true
                    )
                    if (state.mode == AuthMode.REGISTER || state.mode == AuthMode.RESET_PASSWORD) {
                        labelText("确认密码")
                        modernTextField(state.confirmPassword, viewModel::updateConfirmPassword, placeholder = "再次输入密码", isPassword = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            modernTextField(state.code, viewModel::updateCode, placeholder = "验证码", modifier = Modifier.weight(1f))
                            Button(
                                onClick = viewModel::sendCode,
                                enabled = !state.isLoading && state.codeCooldownSeconds <= 0,
                                shape = ModernTheme.shapeSmall,
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = ModernTheme.success,
                                    disabledBackgroundColor = ModernTheme.surfaceHover,
                                    contentColor = Color.White,
                                    disabledContentColor = Color.White
                                ),
                                elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                                modifier = Modifier.height(34.dp).width(100.dp)
                            ) {
                                Text(
                                    if (state.codeCooldownSeconds > 0) "${state.codeCooldownSeconds}s" else "发验证码",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                )
                            }
                        }
                        if (state.mode == AuthMode.REGISTER) {
                            primaryButton("注册并登录", state.isLoading, onClick = viewModel::register)
                            secondaryButton("已有账号，去登录", onClick = { viewModel.switchAuthMode(AuthMode.LOGIN) })
                        } else {
                            primaryButton("重置密码", state.isLoading, onClick = viewModel::resetPassword)
                            secondaryButton("返回登录", onClick = { viewModel.switchAuthMode(AuthMode.LOGIN) })
                        }
                    } else {
                        primaryButton("登录", state.isLoading, onClick = viewModel::login)
                        secondaryButton("忘记密码", onClick = { viewModel.switchAuthMode(AuthMode.RESET_PASSWORD) })
                        secondaryButton("没有账号，去注册", onClick = { viewModel.switchAuthMode(AuthMode.REGISTER) })
                    }
                }
                Box(Modifier.fillMaxWidth().height(AuthMessageReservedHeight)) {
                    if (state.message.isNotBlank()) {
                        Text(
                            state.message,
                            color = if (isAuthErrorMessage(state.message)) ModernTheme.error else ModernTheme.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier
                        )
                    }
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
            Text("用户: $email", color = ModernTheme.textSecondary, fontSize = 13.sp, modifier = Modifier)
        }
    }
}

@Composable
fun accountLockedScreen(viewModel: NeoLinkViewModel) {
    val state = viewModel.authState
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = ModernTheme.error, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "账号已被封禁",
                    color = ModernTheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    modifier = Modifier
                )
            }
            Text(
                "该账号当前禁止登录和使用服务，请退出登录并联系管理员处理。",
                color = ModernTheme.textSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier
            )
            if (state.email.isNotBlank()) {
                Text("用户: ${state.email}", color = ModernTheme.textSecondary, fontSize = 12.sp, modifier = Modifier)
            }
            if (state.message.isNotBlank() && !state.message.isRedundantAccountLockedMessage()) {
                Text(state.message, color = ModernTheme.error, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier)
            }
            primaryButton("退出登录", false, onClick = viewModel::logout)
        }
    }
}

private fun String.isRedundantAccountLockedMessage(): Boolean {
    val normalized = filterNot(Char::isWhitespace).removeSuffix("。").removeSuffix(".")
    return normalized == "账号已被封禁" || normalized == "账号已被封禁，请联系管理员"
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
        AuthMode.RESET_PASSWORD -> "重置密码"
        AuthMode.VERIFY_IDENTITY -> "实名认证"
    }
    val subtitle = subtitleOverride ?: when (mode) {
        AuthMode.LOGIN -> "登录后即可管理内网穿透。"
        AuthMode.REGISTER -> "创建账号后即可使用内网穿透。"
        AuthMode.RESET_PASSWORD -> "通过邮箱验证码重新设置密码。"
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
                modifier = Modifier
            )
        }
        Text(subtitle, color = ModernTheme.textSecondary, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier)
    }
}

private fun isAuthErrorMessage(message: String): Boolean =
    message.contains("失败") || message.contains("错误") || message.contains("失效")
