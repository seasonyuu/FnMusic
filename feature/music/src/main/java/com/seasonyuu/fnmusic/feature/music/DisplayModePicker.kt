package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnCard
import com.seasonyuu.fnmusic.core.model.AppearancePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DisplayModePicker(value: AppearancePreference, onChange: suspend (AppearancePreference) -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val dark = when (value) {
        AppearancePreference.Light -> false
        AppearancePreference.Dark -> true
        AppearancePreference.System -> isSystemInDarkTheme()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = FnCard), shape = RoundedCornerShape(24.dp)) {
            Row(Modifier.fillMaxWidth().selectableGroup().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(AppearancePreference.Light to "浅色", AppearancePreference.Dark to "深色").forEach { (mode, label) ->
                    val selected = dark == (mode == AppearancePreference.Dark)
                    Column(
                        Modifier.weight(1f).selectable(interactionSource = remember { MutableInteractionSource() }, indication = null, selected = selected, enabled = !saving, role = Role.RadioButton, onClick = {
                            if (!saving && value != mode) {
                                saving = true
                                scope.launch {
                                    try { onChange(mode); error = false }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { error = true }
                                    finally { saving = false }
                                }
                            }
                        }).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ModeIllustration(mode == AppearancePreference.Dark, Modifier.widthIn(max = 100.dp).fillMaxWidth().aspectRatio(0.55f))
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Surface(
                            modifier = Modifier.size(24.dp), shape = RoundedCornerShape(50),
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            border = if (selected) null else androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            if (selected) Icon(Icons.Default.Check, null, Modifier.padding(4.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error) Text("外观保存失败，请重新选择。", color = MaterialTheme.colorScheme.error)
    }
}

/** Resolution-independent miniatures of the same layout in its two appearances. */
@Composable
private fun ModeIllustration(dark: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val surface = if (dark) Color(0xFF202427) else Color.White
        val tile = if (dark) Color(0xFF3C4A58) else Color(0xFFE8EDF4)
        val outline = if (dark) Color(0xFF596066) else Color(0xFFCDD0D4)
        val radius = CornerRadius(size.width * .15f)
        drawRoundRect(surface, cornerRadius = radius)
        drawRoundRect(outline, cornerRadius = radius, style = Stroke(1.dp.toPx()))
        val padding = size.width * .12f
        val gap = size.width * .06f
        val width = (size.width - padding * 2 - gap) / 2
        fun block(x: Float, y: Float, w: Float, h: Float) {
            drawRoundRect(tile, Offset(x, size.height * y), Size(w, size.height * h), CornerRadius(size.width * .05f))
        }
        repeat(2) { column ->
            val x = padding + column * (width + gap)
            block(x, .07f, width, .14f)
            block(x, .39f, width, .24f)
            block(x, .69f, width, .24f)
        }
        val smallWidth = (size.width - padding * 2 - gap * 2) / 3
        repeat(3) { block(padding + it * (smallWidth + gap), .27f, smallWidth, .06f) }
    }
}
