package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.model.ThemeColorPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ThemeColorPicker(
    value: ThemeColorPreference,
    onChange: suspend (ThemeColorPreference) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ThemeColorPreference.entries.forEach { choice ->
                val color = Color(choice.argb)
                Column(
                    Modifier.size(48.dp).semantics { contentDescription = choice.label }.selectable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
                        selected = value == choice, enabled = !saving, role = Role.RadioButton,
                        onClick = {
                            if (!saving && value != choice) {
                                saving = true
                                scope.launch {
                                    try { onChange(choice); error = false }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { error = true }
                                    finally { saving = false }
                                }
                            }
                        },
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(48.dp).border(2.dp, if (choice == value) color else Color.Transparent, CircleShape)
                        .padding(5.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                        if (choice == value) Icon(Icons.Rounded.Check, null,
                            tint = if (color.luminance() > .5f) Color.Black else Color.White)
                    }
                }
            }
        }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error) Text("主题色保存失败，请重新选择。", color = MaterialTheme.colorScheme.error)
    }
}
