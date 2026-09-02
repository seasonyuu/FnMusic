package com.seasonyuu.fnmusic.feature.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnAccent
import com.seasonyuu.fnmusic.core.designsystem.FnCard
import com.seasonyuu.fnmusic.core.designsystem.FnGradientBackground
import com.seasonyuu.fnmusic.core.designsystem.FnProgressiveSystemBars
import com.seasonyuu.fnmusic.core.designsystem.FnTextSecondary
import com.seasonyuu.fnmusic.core.designsystem.FnTextPrimary
import com.seasonyuu.fnmusic.core.designsystem.R
import com.seasonyuu.fnmusic.core.model.ConnectionProfile
import com.seasonyuu.fnmusic.core.model.Endpoint
import com.seasonyuu.fnmusic.core.model.SessionState

private enum class ConnectionMode { FnConnect, Direct }

@Composable
fun ConnectionScreen(
    state: SessionState,
    onConnect: (ConnectionProfile, CharArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(ConnectionMode.FnConnect) }
    var fnId by remember { mutableStateOf("") }
    var directUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var allowLanHttp by remember { mutableStateOf(false) }
    val connecting = state is SessionState.Restoring ||
        state is SessionState.Locating ||
        state is SessionState.RelaySelected ||
        state is SessionState.RelayActive ||
        state is SessionState.MusicAuthenticated

    if (connecting) {
        ConnectionLoadingScreen(state, modifier)
        return
    }

    FnGradientBackground {
        FnProgressiveSystemBars {
            Box(modifier.fillMaxSize().imePadding()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .safeDrawingPadding()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = FnCard),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(R.drawable.fn_music_logo), "飞牛音乐", Modifier.size(48.dp))
                        Column {
                            Text("飞牛音乐", style = MaterialTheme.typography.headlineMedium)
                            Text("你的私人音乐库，随身播放", color = FnTextSecondary)
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ConnectionMode.entries.forEachIndexed { index, item ->
                            SegmentedButton(
                                selected = mode == item,
                                onClick = { mode = item },
                                shape = SegmentedButtonDefaults.itemShape(index, ConnectionMode.entries.size),
                                icon = { Icon(if (item == ConnectionMode.FnConnect) Icons.Rounded.Cloud else Icons.Rounded.Dns, null) },
                            ) { Text(if (item == ConnectionMode.FnConnect) "FN Connect" else "NAS 直连") }
                        }
                    }
                    if (mode == ConnectionMode.FnConnect) {
                        OutlinedTextField(
                            value = fnId,
                            onValueChange = { fnId = it.trim() },
                            label = { Text("FN ID") },
                            placeholder = { Text("输入你的 FN ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    } else {
                        OutlinedTextField(
                            value = directUrl,
                            onValueChange = { directUrl = it },
                            label = { Text("飞牛音乐地址") },
                            placeholder = { Text("https://nas.example.com/music/") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(allowLanHttp, { allowLanHttp = it })
                            Text("允许经过私网地址校验的局域网 HTTP", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("音乐账号") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    )
                    Text(
                        "登录摘要和会话将使用 Android Keystore 加密保存。局域网 HTTP 会以明文传输，请仅在可信网络使用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = FnTextSecondary,
                    )
                    if (state is SessionState.Error) Text(state.message, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = {
                            val endpoint = if (mode == ConnectionMode.FnConnect) Endpoint.FnConnect(fnId) else Endpoint.Direct(directUrl)
                            onConnect(ConnectionProfile(endpoint, username.trim(), allowLanHttp), password.toCharArray())
                            password = ""
                        },
                        enabled = !connecting && username.isNotBlank() && password.isNotBlank() &&
                            ((mode == ConnectionMode.FnConnect && fnId.length >= 6) || (mode == ConnectionMode.Direct && directUrl.isNotBlank())),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (connecting) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text("连接音乐库")
                    }
                }
            }
                }
            }
        }
    }
}

@Composable
private fun ConnectionLoadingScreen(state: SessionState, modifier: Modifier) {
    val message = when (state) {
        SessionState.Restoring -> "正在恢复登录状态…"
        SessionState.Locating -> "正在准备连接…"
        SessionState.RelaySelected -> "正在建立远程连接…"
        SessionState.RelayActive -> "正在连接音乐库…"
        SessionState.MusicAuthenticated -> "正在读取音乐库…"
        else -> "正在连接…"
    }
    FnGradientBackground {
        FnProgressiveSystemBars {
            Box(
                modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Image(painterResource(R.drawable.fn_music_logo), "飞牛音乐", Modifier.size(72.dp))
                    Text("飞牛音乐", style = MaterialTheme.typography.headlineMedium, color = FnTextPrimary)
                    CircularProgressIndicator(color = FnAccent)
                    Text(message, color = FnTextSecondary)
                }
            }
        }
    }
}
