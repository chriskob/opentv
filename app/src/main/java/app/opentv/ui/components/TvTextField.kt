/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.opentv.ui.theme.AppTheme
import app.opentv.ui.theme.cardFocusBg
import app.opentv.ui.theme.primary

/**
 * A TV-optimized [OutlinedTextField] that does not bring up the on-screen keyboard when scrolled
 * over with the D-pad. Focus highlights the text field container cleanly; pressing OK / D-pad
 * Center (or clicking) activates editing mode and displays the keyboard.
 */
@Composable
fun TvOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = RoundedCornerShape(10.dp),
    colors: TextFieldColors? = null,
) {
    var isEditing by remember { mutableStateOf(false) }
    var isBoxFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // If Back is pressed while editing, exit edit mode and hide keyboard instead of closing screen
    BackHandler(enabled = isEditing) {
        isEditing = false
        keyboardController?.hide()
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    val mergedKeyboardActions = remember(keyboardActions, keyboardController) {
        KeyboardActions(
            onDone = {
                isEditing = false
                keyboardController?.hide()
                keyboardActions.onDone?.invoke(this)
            },
            onGo = {
                isEditing = false
                keyboardController?.hide()
                keyboardActions.onGo?.invoke(this)
            },
            onNext = {
                isEditing = false
                keyboardController?.hide()
                keyboardActions.onNext?.invoke(this)
            },
            onPrevious = {
                isEditing = false
                keyboardController?.hide()
                keyboardActions.onPrevious?.invoke(this)
            },
            onSearch = {
                isEditing = false
                keyboardController?.hide()
                keyboardActions.onSearch?.invoke(this)
            },
            onSend = {
                isEditing = false
                keyboardController?.hide()
                keyboardActions.onSend?.invoke(this)
            },
        )
    }

    val effectiveColors = colors ?: OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AppTheme.primary,
        unfocusedBorderColor = if (isBoxFocused) AppTheme.primary else Color(0xFF37474F),
        focusedLabelColor = AppTheme.primary,
        unfocusedLabelColor = if (isBoxFocused) AppTheme.primary else Color(0xFF90A4AE),
        focusedContainerColor = if (isBoxFocused || isEditing) AppTheme.cardFocusBg else Color(0xFF141C24),
        unfocusedContainerColor = if (isBoxFocused) AppTheme.cardFocusBg else Color(0xFF141C24),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = AppTheme.primary,
    )

    Box(
        modifier = modifier
            .onFocusChanged { isBoxFocused = it.isFocused }
            .clip(shape)
            .then(
                if (isBoxFocused && !isEditing) Modifier.border(2.dp, AppTheme.primary, shape)
                else Modifier
            )
            .focusable(!isEditing && enabled)
            .clickable(
                enabled = !isEditing && enabled && !readOnly,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                isEditing = true
            }
            .onKeyEvent { keyEvent ->
                if (!isEditing && enabled && !readOnly && keyEvent.type == KeyEventType.KeyUp &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                ) {
                    isEditing = true
                    true
                } else false
            },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = isEditing }
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (isEditing && !it.isFocused) {
                        isEditing = false
                    }
                },
            enabled = enabled,
            readOnly = readOnly,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon ?: {
                Icon(
                    imageVector = if (isEditing) Icons.Filled.Keyboard else Icons.Filled.Edit,
                    contentDescription = if (isEditing) "Keyboard open" else "Click to enter text",
                    tint = if (isBoxFocused || isEditing) AppTheme.primary else Color(0xFF607D8B),
                    modifier = Modifier.size(20.dp),
                )
            },
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = mergedKeyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            interactionSource = interactionSource,
            shape = shape,
            colors = effectiveColors,
        )

        // Overlay to capture clicks/taps when not in edit mode
        if (!isEditing && enabled && !readOnly) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { isEditing = true },
                    ),
            )
        }
    }
}
