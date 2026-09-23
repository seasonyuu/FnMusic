package com.seasonyuu.fnmusic.feature.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seasonyuu.fnmusic.core.designsystem.FlowingLightBackground
import com.seasonyuu.fnmusic.core.designsystem.FlowingLightStyle
import com.seasonyuu.fnmusic.core.designsystem.FnTextSecondary
import com.seasonyuu.fnmusic.core.designsystem.R
import com.seasonyuu.fnmusic.core.model.ConnectionProfile
import com.seasonyuu.fnmusic.core.model.Endpoint
import com.seasonyuu.fnmusic.core.model.LoginForm
import com.seasonyuu.fnmusic.core.model.SessionState

private val LoginSwitchEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)
private const val LoginSwitchDuration = 260

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectionScreen(
    state: SessionState,
    onConnect: (ConnectionProfile, CharArray) -> Unit,
    form: LoginForm,
    onFormChange: (LoginForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val layoutDirection = LocalLayoutDirection.current
    val slideDistance = with(LocalDensity.current) { 12.dp.roundToPx() }
    val tabPosition by animateFloatAsState(
        targetValue = if (form.useDirectConnection) 1f else 0f,
        animationSpec = tween(LoginSwitchDuration, easing = LoginSwitchEasing),
        label = "login-tab-indicator",
    )
    val connecting = state is SessionState.Restoring || state is SessionState.Locating ||
        state is SessionState.RelaySelected || state is SessionState.RelayActive ||
        state is SessionState.MusicAuthenticated
    val canSubmit = !connecting && form.username.isNotBlank() && form.password.isNotBlank() &&
        if (form.useDirectConnection) form.directUrl.isNotBlank() else form.fnId.length >= 6
    val submit = {
        if (canSubmit) {
            passwordVisible = false
            focus.clearFocus()
            keyboard?.hide()
            val endpoint = if (form.useDirectConnection) Endpoint.Direct(form.directUrl.trim()) else Endpoint.FnConnect(form.fnId.trim())
            onConnect(ConnectionProfile(endpoint, form.username.trim(), form.allowPrivateLanHttp), form.password.toCharArray())
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF090C13))) {
        FlowingLightBackground(FlowingLightStyle.Login)
        // Keep the moving light behind the form subdued enough for readable labels.
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = .16f), Color(0xD9080B12)),
        )))
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .safeDrawingPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(R.drawable.fn_music_logo), null, Modifier.size(64.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("飞牛音乐", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("你的私人音乐库，随身播放", style = MaterialTheme.typography.bodyMedium, color = FnTextSecondary)
                    }
                }
                Spacer(Modifier.height(36.dp))
                if (connecting) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        LoadingIndicator(Modifier.size(40.dp), color = Color.White)
                        Text(connectionMessage(state), color = FnTextSecondary)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = .06f)).padding(4.dp)
                            .drawBehind {
                                val position = if (layoutDirection == LayoutDirection.Ltr) tabPosition else 1f - tabPosition
                                drawRoundRect(
                                    color = Color.White.copy(alpha = .13f),
                                    topLeft = Offset(size.width / 2f * position, 0f),
                                    size = Size(size.width / 2f, size.height),
                                    cornerRadius = CornerRadius(12.dp.toPx()),
                                )
                            }.selectableGroup(),
                    ) {
                        listOf(false, true).forEach { direct ->
                            val selected = form.useDirectConnection == direct
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .selectable(selected, role = Role.Tab, onClick = { onFormChange(form.copy(useDirectConnection = direct)) })
                                    .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            ) {
                                Icon(if (direct) Icons.Rounded.Dns else Icons.Rounded.Cloud, null, Modifier.size(18.dp), tint = if (selected) Color.White else FnTextSecondary)
                                Text(if (direct) "NAS 直连" else "FN Connect", fontSize = 14.sp, color = if (selected) Color.White else FnTextSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        AnimatedContent(
                            targetState = form.useDirectConnection,
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopStart,
                            transitionSpec = {
                                val direction = (if (targetState) 1 else -1) *
                                    (if (layoutDirection == LayoutDirection.Ltr) 1 else -1)
                                ((fadeIn(tween(220, delayMillis = 40)) + slideInHorizontally(
                                    tween(LoginSwitchDuration, easing = LoginSwitchEasing),
                                ) { direction * slideDistance }) togetherWith
                                    (fadeOut(tween(100)) + slideOutHorizontally(
                                        tween(160, easing = LoginSwitchEasing),
                                    ) { -direction * slideDistance / 2 }))
                                    .using(SizeTransform { _, _ -> tween(LoginSwitchDuration, easing = LoginSwitchEasing) })
                            },
                            label = "login-connection-fields",
                        ) { direct ->
                            // Bind outgoing and incoming fields to their own mode during transitions.
                            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                LoginField(
                                    value = if (direct) form.directUrl else form.fnId,
                                    onValueChange = { if (direct) onFormChange(form.copy(directUrl = it)) else onFormChange(form.copy(fnId = it.trim())) },
                                    label = if (direct) "飞牛音乐地址" else "FN ID",
                                    placeholder = if (direct) "https://nas.example.com/music/" else "输入你的 FN ID",
                                    keyboardType = if (direct) KeyboardType.Uri else KeyboardType.Ascii,
                                    onNext = { focus.moveFocus(FocusDirection.Down) },
                                    enabled = direct == form.useDirectConnection,
                                )
                                if (direct) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(form.allowPrivateLanHttp, { onFormChange(form.copy(allowPrivateLanHttp = it)) }, enabled = form.useDirectConnection)
                                        Text("允许局域网 HTTP（仅限可信网络）", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
                                    }
                                }
                            }
                        }
                        LoginField(form.username, { onFormChange(form.copy(username = it)) }, "音乐账号", "输入音乐账号", onNext = { focus.moveFocus(FocusDirection.Down) })
                        LoginField(
                            form.password, { onFormChange(form.copy(password = it)) }, "密码", "输入密码",
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            onDone = submit,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (passwordVisible) "隐藏密码" else "显示密码", tint = FnTextSecondary)
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("自动记住登录信息，仅加密保存在本机", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
                    if (state is SessionState.Error) {
                        Text(state.message, Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = submit,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF101218), disabledContainerColor = Color.White.copy(alpha = .24f), disabledContentColor = Color.White.copy(alpha = .65f)),
                    ) {
                        Text("登录音乐库", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text("FN MUSIC  /  私人音乐，随时聆听", color = Color.White.copy(alpha = .5f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onNext: () -> Unit = {},
    onDone: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = FnTextSecondary, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = value,
            enabled = enabled,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            // The visible label sits above the field, like the Web login form.
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White.copy(alpha = .38f),
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                disabledContainerColor = Color.White.copy(alpha = .08f),
                disabledTextColor = Color.White,
                focusedContainerColor = Color.White.copy(alpha = .16f),
                unfocusedContainerColor = Color.White.copy(alpha = .08f),
                cursorColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
        )
    }
}

private fun connectionMessage(state: SessionState): String = when (state) {
    SessionState.Restoring -> "正在恢复登录状态…"
    SessionState.Locating -> "正在准备连接…"
    SessionState.RelaySelected -> "正在建立远程连接…"
    SessionState.RelayActive -> "正在连接音乐库…"
    SessionState.MusicAuthenticated -> "正在获取服务器配置…"
    else -> "正在连接…"
}
