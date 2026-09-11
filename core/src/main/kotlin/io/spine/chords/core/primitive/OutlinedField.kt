/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.chords.core.primitive

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.spine.chords.core.styling.ChordsTheme

/**
 * Shares the outlined editor used by Chords input fields and dropdown selectors.
 *
 * Internal primitive; application code uses `InputField` or `DropdownSelector`. The caller owns
 * [value], including its selection and composition, and must apply [onValueChange] updates.
 * [isError] changes the appearance only; validation and error messages remain the caller's job.
 *
 * Example for an internal field renderer:
 * ```kotlin
 * @Composable
 * fun RequiredNameEditor() {
 *     var value by remember { mutableStateOf(TextFieldValue()) }
 *     val missing = value.text.isBlank()
 *     OutlinedField(
 *         value = value,
 *         onValueChange = { value = it },
 *         label = { Text("Name") },
 *         singleLine = true,
 *         isError = missing,
 *         supportingText = { if (missing) Text("Enter a name.") }
 *     )
 * }
 * ```
 *
 * With [colors] left `null`, the editor uses Chords colors and a 1 dp focused outline, including
 * the error state. An explicit [textStyle] color takes precedence over the default text color.
 * Supplying [colors] switches to the native Material `OutlinedTextField`: this preserves its
 * editor and cursor colors on supported Compose versions, but also restores its border thickness.
 *
 * @param value The current text, selection, and input-method composition.
 * @param onValueChange Receives the complete edited value, not just its text.
 * @param modifier Layout and input behavior applied to the editor.
 * @param enabled Whether the field accepts input and focus.
 * @param textStyle The editor's typography; inherits `LocalTextStyle` by default.
 * @param label Optional floating label. Its presence reserves space above the outline.
 * @param placeholder Content shown while the empty editor is exposed by the label state.
 * @param trailingIcon Optional control or decoration at the end of the field.
 * @param prefix Content placed before the editor text.
 * @param suffix Content placed after the editor text.
 * @param supportingText Content below the field, such as a hint or validation error.
 * @param isError Whether to use error colors for the outline, label, and cursor.
 * @param visualTransformation Changes the displayed text without changing [value].
 * @param singleLine Whether to keep text on one horizontally scrolling line.
 * @param maxLines The maximum visible line count when [singleLine] is `false`.
 * @param minLines The minimum visible line count when [singleLine] is `false`.
 * @param interactionSource Shared focus, press, and hover events for the editor and decoration.
 * @param shape The field's outline; defaults to the active Material small shape.
 * @param colors Optional Material colors; `null` keeps the Chords rendering path.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress(
    "LongMethod", // Keep the editor and its matching Material decoration together.
    "LongParameterList" // Shared field slots mirror the two input component call sites.
)
internal fun OutlinedField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = MaterialTheme.shapes.small,
    colors: TextFieldColors? = null
) {
    if (colors != null) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            interactionSource = interactionSource,
            shape = shape,
            colors = colors
        )
        return
    }
    val scheme = MaterialTheme.colorScheme
    val textColor = textStyle.color.takeOrElse {
        if (enabled) scheme.onSurface else ChordsTheme.disabledContentColor
    }
    val fieldModifier = if (label == null) modifier else modifier
        .semantics(mergeDescendants = true) {}
        .padding(top = FloatingLabelPadding)
    val fieldColors = defaultOutlinedTextFieldColors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = fieldModifier.defaultMinSize(
            OutlinedTextFieldDefaults.MinWidth,
            OutlinedTextFieldDefaults.MinHeight
        ),
        enabled = enabled,
        textStyle = textStyle.merge(TextStyle(color = textColor)),
        cursorBrush = SolidColor(if (isError) scheme.error else scheme.primary),
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        decorationBox = { editor ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value.text,
                innerTextField = editor,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                isError = isError,
                label = label,
                placeholder = placeholder,
                trailingIcon = trailingIcon,
                prefix = prefix,
                suffix = suffix,
                supportingText = supportingText,
                colors = fieldColors,
                container = {
                    OutlinedTextFieldDefaults.ContainerBox(
                        enabled = enabled,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = fieldColors,
                        shape = shape,
                        focusedBorderThickness = 1.dp
                    )
                }
            )
        }
    )
}

/**
 * Reserves the same space above a floating label as the native outlined field.
 */
private val FloatingLabelPadding = 8.dp
