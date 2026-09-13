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

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * An outlined button for an alternative to the main action.
 *
 * Uses the active Material `onSurface` foreground and `outline` border. Hover and press tint the
 * background; keyboard focus changes the border to `primary`. Mouse focus does not leave the
 * button highlighted after the pointer exits. Feedback clears while its window is inactive.
 *
 * Example with a primary action:
 * ```kotlin
 * @Composable
 * fun EditActions(onCancel: () -> Unit, onSave: () -> Unit) {
 *     Row(
 *         horizontalArrangement = Arrangement.spacedBy(ChordsTheme.dimensions.spacingMedium),
 *         verticalAlignment = Alignment.CenterVertically
 *     ) {
 *         SecondaryButton(onClick = onCancel) { Text("Cancel") }
 *         PrimaryButton(onClick = onSave) { Text("Save") }
 *     }
 * }
 * ```
 *
 * [PrimaryButton] documents their shared sizing and content layout. Import this function from
 * `io.spine.chords.core.primitive` to use Chords feedback instead of Material's `OutlinedButton`.
 *
 * @param onClick Invoked when the action is activated.
 * @param modifier Layout adjustments, such as width or outer padding.
 * @param enabled Whether pointer and keyboard activation are allowed. Disabled content is dimmed.
 * @param content A centered row of text and optional icons, inheriting the button's foreground.
 */
@Composable
public fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    ActionButton(onClick, modifier, enabled, filled = false, content)
}
