/*
 * Copyright 2025, TeamDev. All rights reserved.
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
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.chords.core.primitive

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.ContentAlpha.disabled
import androidx.compose.material.MaterialTheme.colors
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role.Companion.RadioButton
import androidx.compose.ui.unit.dp
import io.spine.chords.core.FocusRequestDispatcher
import io.spine.chords.core.focusRequestDispatcher
import io.spine.chords.core.keyboard.key
import io.spine.chords.core.keyboard.on


/**
 * A radio button that includes a given text on the right.
 *
 * @param selected A [MutableState] that holds a boolean flag indicating whether
 *   the radio button is selected.
 * @param onClick Invoked when the user has selected this radio button. This
 *   function has to be implemented in a way that makes the [selected]
 *   parameter to be updated accordingly.
 * @param text A text displayed to the right of the radio button.
 * @param focusRequestDispatcher A [FocusRequestDispatcher], which should be
 *   attached to by this field for receiving and handling field focus requests.
 */
@Composable
public fun RadioButtonWithText(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    focusRequestDispatcher: FocusRequestDispatcher? = null
) {
    Row(
        modifier = modifier
            .focusRequestDispatcher(focusRequestDispatcher)
            .run {
                if (enabled) {
                    selectable(
                        selected = selected,
                        onClick = onClick,
                        role = RadioButton
                    ).on(Key.Spacebar.key.up) {
                        onClick()
                    }
                } else {
                    this
                }
            },
        verticalAlignment = CenterVertically,
        horizontalArrangement = spacedBy(8.dp)
    ) {
        RadioButton(
            selected = selected,
            enabled = enabled,
            onClick = null
        )
        Text(text, color = if (enabled) colors.onSurface else colors.onSurface.copy(disabled))
    }
}
