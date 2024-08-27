/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.primitive

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role.Companion.RadioButton
import androidx.compose.ui.unit.dp
import io.spine.chords.FocusRequestDispatcher
import io.spine.chords.focusRequestDispatcher
import io.spine.chords.keyboard.key
import io.spine.chords.keyboard.on


/**
 * A radio button that includes a given text on the right.
 *
 * @param selected
 *         a [MutableState] that holds a boolean flag indicating whether
 *         the radio button is selected.
 * @param onClick
 *         invoked when the user has selected this radio button. This function
 *         has to be implemented in a way that makes the [selected] parameter
 *         to be updated accordingly.
 * @param text
 *         a text displayed to the right of the radio button.
 * @param focusRequestDispatcher
 *         a [FocusRequestDispatcher], which should be attached to by this field
 *         for receiving and handling field focus requests.
 */
@Composable
public fun RadioButtonWithText(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    focusRequestDispatcher: FocusRequestDispatcher? = null
) {
    Row(
        modifier = modifier
            .focusRequestDispatcher(focusRequestDispatcher)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = RadioButton
            ).on(Key.Spacebar.key.up) {
                onClick()
            },
        verticalAlignment = CenterVertically,
        horizontalArrangement = spacedBy(5.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Text(text)
    }
}
