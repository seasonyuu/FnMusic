package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Settings
import com.seasonyuu.fnmusic.core.model.LocalAudioDeviceKind
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.seasonyuu.fnmusic.core.designsystem.LiquidButton
import com.seasonyuu.fnmusic.core.designsystem.CoverImage
import com.seasonyuu.fnmusic.core.designsystem.FnTextPrimary
import com.seasonyuu.fnmusic.core.designsystem.FnTextSecondary
import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.PlaybackOutput
import com.seasonyuu.fnmusic.core.model.PlaybackOutputController

val LocalPlaybackOutput = staticCompositionLocalOf<PlaybackOutputController?> { null }
val LocalNetworkPermissionRequest = staticCompositionLocalOf<(((Boolean) -> Unit) -> Unit)?> { null }

@Composable
private fun AirPlayAudioIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val unit = size.width / 24f
        for (radius in listOf(10f, 7f, 4f)) drawArc(tint, 140f, 260f, false,
            Offset((12-radius)*unit, (10-radius)*unit), Size(radius*2*unit, radius*2*unit),
            style = Stroke(1.5f*unit, cap = StrokeCap.Round))
        drawPath(Path().apply { moveTo(12*unit,13*unit); lineTo(6.5f*unit,23*unit); lineTo(17.5f*unit,23*unit); close() }, tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirPlayOutputEntry(current: PlayableTrack? = null) {
    val controller = LocalPlaybackOutput.current ?: return
    val state by controller.outputState.collectAsState()
    val remote = state.output as? PlaybackOutput.AirPlay
    val currentLocal = state.local.devices.firstOrNull { it.id == state.local.currentId }
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingPermission by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermission?.invoke(granted); pendingPermission = null
    }
    val requestPermission: ((Boolean) -> Unit) -> Unit = LocalNetworkPermissionRequest.current ?: { callback ->
        if (android.os.Build.VERSION.SDK_INT < 37 || context.checkSelfPermission(android.Manifest.permission.ACCESS_LOCAL_NETWORK) == android.content.pm.PackageManager.PERMISSION_GRANTED) callback(true)
        else { pendingPermission = callback; permissionLauncher.launch(android.Manifest.permission.ACCESS_LOCAL_NETWORK) }
    }
    var open by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    LaunchedEffect(state.pairing) { if (state.pairing) open = true }
    IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)
        .background(if (remote != null) FnTextPrimary.copy(alpha = .14f) else Color.Transparent, CircleShape)
        .semantics {
            contentDescription = "选择播放设备"
            stateDescription = remote?.let { "正在通过 ${it.name} 播放" } ?: currentLocal?.let { "本地输出：${it.name}" } ?: "本地输出待确认"
            selected = remote != null
        }.testTag("player-airplay")) {
        AirPlayAudioIcon(if (remote != null) FnTextPrimary else FnTextSecondary)
    }
    if (!open) return
    DisposableEffect(controller) {
        var active = true
        requestPermission { granted ->
            if (active) { denied = !granted; if (granted) controller.scanOutputs(true) }
        }
        onDispose { active = false; controller.scanOutputs(false) }
    }
    ModalBottomSheet(onDismissRequest = { open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 480.dp,
        containerColor = Color.Transparent, scrimColor = Color.Black.copy(alpha = .48f),
        dragHandle = null, tonalElevation = 0.dp,
        modifier = Modifier.testTag("airplay-device-sheet")) {
        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            shape = RoundedCornerShape(36.dp), color = Color(0xFF3B3B40), contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)), shadowElevation = 18.dp) {
            val sheetBackdrop = rememberLayerBackdrop()
            Box {
                Box(Modifier.matchParentSize().layerBackdrop(sheetBackdrop).background(Color(0xFF3B3B40)))
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverImage(current?.coverUrl, null, Modifier.size(60.dp), requestSizePx = 180)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(current?.track?.title ?: "播放设备", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(current?.track?.artists?.joinToString("、") { it.name }.orEmpty().ifBlank { "选择音频输出" },
                                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .55f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(10.dp))
                        LiquidButton(onClick = {
                            controller.useLocalOutput()
                            val shown = if (android.os.Build.VERSION.SDK_INT >= 34) runCatching {
                                android.media.MediaRouter2.getInstance(context).showSystemOutputSwitcher()
                            }.getOrDefault(false) else false
                            if (!shown) context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                        }, backdrop = sheetBackdrop,
                            modifier = Modifier.size(48.dp).testTag("local-system-output").semantics {
                                contentDescription = if (android.os.Build.VERSION.SDK_INT >= 34) "系统输出选择器" else "蓝牙设置"
                            }, contentPadding = PaddingValues(0.dp), surfaceColor = Color(0xFF48484E)) {
                            AirPlayAudioIcon(Color.White.copy(alpha = .70f))
                        }
                    }
                    Text("本地设备", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = .55f))
                    state.local.devices.forEach { device ->
                        val pending = remote == null && state.local.pending && state.local.requestedId == device.id
                        DeviceOutputRow(device.name, when (device.kind) {
                            LocalAudioDeviceKind.Speaker -> Icons.Rounded.PhoneAndroid
                            LocalAudioDeviceKind.Bluetooth -> Icons.Rounded.BluetoothAudio
                            LocalAudioDeviceKind.Wired -> Icons.Rounded.Headphones
                            LocalAudioDeviceKind.Usb -> Icons.Rounded.Usb
                            LocalAudioDeviceKind.Hdmi -> Icons.Rounded.Speaker
                        }, selected = remote == null && state.local.currentId == device.id,
                            connecting = pending && !state.local.awaitingPlayback,
                            subtitle = if (pending && state.local.awaitingPlayback) "待播放时确认" else if (pending) "正在切换…" else null,
                            onClick = { controller.selectLocalOutput(device.id) }, tag = "local-device-${device.id}")
                    }
                    DeviceOutputRow("跟随系统", Icons.Rounded.Settings, false,
                        subtitle = if (remote == null && state.local.pending && state.local.requestedId == null)
                            (if (state.local.awaitingPlayback) "待播放时确认" else "正在切换…")
                        else if (remote == null && state.local.followsSystem) currentLocal?.name ?: "播放后显示实际设备" else "使用系统默认音频输出",
                        onClick = { controller.useLocalOutput() }, tag = "airplay-local")
                    state.local.error?.takeIf { remote == null }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB4AB), modifier = Modifier.testTag("local-output-error"))
                    }
                    Text("AirPlay", Modifier.padding(start = 12.dp, top = 6.dp), style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = .55f))
                    if (remote != null && state.devices.none { it.id == remote.id }) {
                        DeviceOutputRow(remote.name, Icons.Rounded.Speaker, true, onClick = {}, tag = "airplay-device-${remote.id}")
                    }
                    state.devices.forEach { device ->
                        val supported = device.serviceType.contains("_airplay")
                        DeviceOutputRow(device.name,
                            if (device.model.contains("Mac", true)) Icons.Rounded.LaptopMac else Icons.Rounded.Speaker,
                            selected = remote?.id == device.id, connecting = state.connecting?.id == device.id,
                            enabled = supported && state.connecting == null,
                            subtitle = if (!supported) "暂不支持此设备" else null,
                            onClick = { controller.selectOutput(device.id) }, tag = "airplay-device-${device.id}")
                    }
                    if (denied) {
                        Text("允许本地网络访问，以发现附近的 AirPlay 设备。", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { requestPermission { allowed -> denied = !allowed; if (allowed && open) controller.scanOutputs(true) } }) { Text("允许访问", color = Color.White) }
                    } else {
                        if (state.scanning && state.connecting == null) Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(12.dp), color = Color.White.copy(alpha = .5f), strokeWidth = 1.5.dp)
                            Text("正在查找 AirPlay 设备", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .55f))
                        }
                        if (state.devices.isEmpty() && remote == null) Text("请确认接收设备已开启 AirPlay，并与手机连接同一网络。",
                            Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .55f))
                        state.connecting?.let {
                            Text(if (state.pairing) "输入 ${it.name} 上显示的配对码" else "请及时确认 ${it.name} 上的 AirPlay 连接弹窗。",
                                Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .75f))
                            TextButton(onClick = controller::cancelOutputConnection, modifier = Modifier.testTag("airplay-cancel-connection")) { Text("取消连接", color = Color.White) }
                        }
                        if (state.pairing) {
                            var pin by remember(state.connecting?.id) { mutableStateOf("") }
                            OutlinedTextField(value = pin, onValueChange = { pin = it.filter { c -> c in '0'..'9' }.take(4) },
                                label = { Text("四位配对码") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("airplay-pin"))
                            Button(onClick = { controller.submitOutputPin(pin); pin = "" }, enabled = pin.length == 4,
                                modifier = Modifier.align(Alignment.End).testTag("airplay-submit-pin")) { Text("连接") }
                        }
                        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB4AB)) }
                        if (!state.scanning || state.error != null) TextButton(onClick = { controller.scanOutputs(true) }) { Text("重新搜索", color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceOutputRow(name: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit,
    tag: String, enabled: Boolean = true, connecting: Boolean = false, subtitle: String? = null) {
    val foreground = if (selected) Color(0xFF222225) else Color.White.copy(alpha = if (enabled || connecting) 1f else .4f)
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(28.dp),
        color = if (selected) Color(0xFFE9E9EC) else Color.White.copy(alpha = .10f), contentColor = foreground,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected }.testTag(tag)) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(25.dp)); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            if (connecting) CircularProgressIndicator(Modifier.padding(start = 10.dp).size(20.dp), color = foreground, strokeWidth = 2.dp)
            else if (selected) Box(Modifier.padding(start = 10.dp).size(22.dp).background(foreground, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "当前播放设备", Modifier.size(16.dp), tint = Color(0xFFE9E9EC))
            }
        }
    }
}
