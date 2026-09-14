package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PasswordSettingsScreen(
    username: String,
    onSave: (suspend (String) -> Unit)?,
    onBack: () -> Unit,
) {
    // Secrets intentionally never enter saved instance state or the navigation snapshot.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = 20.dp, bottom = 20.dp))
            .testTag("password-settings-page"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PageTitle("修改密码", onBack)
        Text(username, style = MaterialTheme.typography.titleLarge)
        Text("请输入你的新密码。修改成功后需要重新登录。")
        OutlinedTextField(
            password, { password = it; error = null }, label = { Text("新密码") },
            enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "隐藏" else "显示") } },
        )
        OutlinedTextField(
            confirmation, { confirmation = it; error = null }, label = { Text("确认密码") },
            enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            isError = confirmation.isNotEmpty() && confirmation != password,
        )
        if (confirmation.isNotEmpty() && confirmation != password) Text("两次输入的密码不一致", color = MaterialTheme.colorScheme.error)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !saving && password.isNotEmpty() && password == confirmation && onSave != null,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!saving) {
                    saving = true
                    scope.launch {
                        try {
                            onSave?.invoke(password)
                            password = ""
                            confirmation = ""
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "修改未确认成功。请检查网络或密码要求；若请求超时，请先尝试使用新密码登录。"
                        } finally {
                            saving = false
                        }
                    }
                }
            },
        ) { Text(if (saving) "正在保存…" else "保存修改") }
    }
}
