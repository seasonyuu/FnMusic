package com.seasonyuu.fnmusic.feature.music

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import com.seasonyuu.fnmusic.core.designsystem.FnTextPrimary

@Composable
internal fun readableTextButtonColors() = ButtonDefaults.textButtonColors(contentColor = FnTextPrimary)

@Composable
internal fun readableOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(contentColor = FnTextPrimary)

@Composable
internal fun readableTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedLabelColor = FnTextPrimary,
    focusedBorderColor = FnTextPrimary,
    cursorColor = FnTextPrimary,
)
