/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.primitive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role.Companion.Checkbox
import androidx.compose.ui.unit.dp
import io.spine.chords.FocusRequestDispatcher
import io.spine.chords.ValidationErrorText
import io.spine.chords.focusRequestDispatcher
import io.spine.chords.keyboard.key
import io.spine.chords.keyboard.on

/**
 * A checkbox that includes a given text on the right.
 *
 * @param checked
 *         indicates whether the checkbox is checked.
 * @param onChange
 *         invoked when the user tries to change the "checked" state.
 *         This handler has to be implemented in a way that updates the value of
 *         the [checked] parameter.
 * @param text
 *         a text displayed to the right of the checkbox.
 * @param enabled
 *         indicates whether the component is enabled for receiving
 *         the user input.
 * @param focusRequestDispatcher
 *         a [FocusRequestDispatcher], which specifies when the component should
 *         be focused.
 * @param externalValidationMessage
 *         a validation error that should be displayed by the component.
 */
@Composable
public fun CheckboxWithText(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    text: String,
    enabled: Boolean = true,
    focusRequestDispatcher: FocusRequestDispatcher? = null,
    externalValidationMessage: State<String?>? = null
) {
    fun toggle() = onChange(!checked)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = { toggle() },
                role = Checkbox
            ).on(Key.Spacebar.key.up) {
                toggle()
            },
        verticalAlignment = CenterVertically,
        horizontalArrangement = spacedBy(5.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.focusRequestDispatcher(focusRequestDispatcher)
        )
        Text(text)
    }
    if (externalValidationMessage?.value != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    onClick = { onChange(!checked) },
                    role = Checkbox
                ),
            verticalAlignment = CenterVertically,
            horizontalArrangement = spacedBy(5.dp)
        ) {
            ValidationErrorText(externalValidationMessage)
        }
    }
}

/**
 * A checkbox that includes a given text on the right.
 *
 * This version of `CheckboxWithText` receives the state as a [MutableState]
 * object, which is updated by the component automatically when the user changes
 * the state of checkbox.
 *
 * @param checked
 *         a [MutableState] that holds a boolean flag indicating whether
 *         the checkbox is checked. If that boolean value is null, displays
 *         an unchecked checkbox.
 * @param onChange
 *         invoked after the user has changed the "checked" state.
 * @param text
 *         a text displayed to the right of the checkbox.
 * @param enabled
 *         indicates whether the component is enabled for receiving
 *         the user input.
 * @param focusRequestDispatcher
 *         a [FocusRequestDispatcher], which specifies when the component should
 *         be focused.
 */
@Composable
public fun CheckboxWithText(
    checked: MutableState<Boolean?>,
    onChange: ((Boolean) -> Unit)? = null,
    text: String,
    enabled: Boolean = true,
    focusRequestDispatcher: FocusRequestDispatcher? = null,
    externalValidationMessage: State<String?>? = null
) {
    CheckboxWithText(
        checked.value ?: false,
        onChange = {
            checked.value = it
            onChange?.invoke(it)
        },
        text = text,
        enabled = enabled,
        focusRequestDispatcher = focusRequestDispatcher,
        externalValidationMessage = externalValidationMessage
    )
}
