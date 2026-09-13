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
 * A filled button for the main action in a form, dialog, or toolbar.
 *
 * Uses the active Material `primary` and `onPrimary` colors and `labelLarge` typography.
 * Hover, press, and keyboard focus have distinct feedback in the active window. Set [enabled]
 * to `false` while an operation is running; the button does not track asynchronous work itself.
 *
 * Example:
 * ```kotlin
 * @Composable
 * fun SaveAction(hasChanges: Boolean, saving: Boolean, onSave: () -> Unit) {
 *     PrimaryButton(
 *         onClick = onSave,
 *         enabled = hasChanges && !saving
 *     ) {
 *         Text(if (saving) "Saving…" else "Save")
 *     }
 * }
 * ```
 *
 * Use [SecondaryButton] for adjacent alternatives. Both buttons use the same sizing and content
 * padding. Align them by their component bounds; Material's minimum interaction area can be taller
 * than the visible button. Supply spacing explicitly when [content] includes both an icon and text.
 * Import this function from `io.spine.chords.core.primitive` rather than using Material's `Button`.
 *
 * @param onClick Invoked when the action is activated.
 * @param modifier Layout adjustments, such as width or outer padding.
 * @param enabled Whether pointer and keyboard activation are allowed. Disabled content is dimmed.
 * @param content A centered row of text and optional icons, inheriting the button's foreground.
 */
@Composable
public fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    ActionButton(onClick, modifier, enabled, filled = true, content)
}
