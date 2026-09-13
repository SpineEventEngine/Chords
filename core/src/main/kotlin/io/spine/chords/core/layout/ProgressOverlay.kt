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
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.chords.core.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass.Initial
import androidx.compose.ui.input.pointer.pointerInput
import io.spine.chords.core.keyboard.matches

/**
 * The opacity of the [ProgressOverlay]'s default background.
 *
 * The background dims the covered content enough for it to read as
 * unavailable, while keeping it recognizable.
 */
private const val DefaultOverlayAlpha = 0.72f

/**
 * Displays the given [content], and covers it with a progress overlay while
 * [active] is `true`.
 *
 * The overlay is a layer that is displayed above the content, occupies the
 * content's full bounds, and prevents the content from being operated with
 * a pointing device. It displays a progress indicator to signify that some
 * process, which involves the content, is currently in progress.
 *
 * Wrapping the content with this function doesn't change the way that
 * the content is measured or placed, and, while [active] is `false`, the
 * content behaves exactly like it does without this wrapper (in particular,
 * no interaction-intercepting layer is introduced).
 *
 * Here's an example of using it for a form whose data is being saved:
 * ```kotlin
 *     ProgressOverlay(saving) {
 *         Column {
 *             // The content that cannot be edited while it is being saved.
 *         }
 *     }
 * ```
 *
 * Note that the overlay blocks the pointer input only, and the content that
 * is covered by it can still be reached with the keyboard. A container that
 * displays such content is expected to prevent the keyboard interaction on
 * its own, like [Dialog] and [Wizard] do while they are submitting (see the
 * "Submission progress" section in [Dialog]).
 *
 * @param active Specifies whether the progress overlay should be displayed
 *   above the [content] currently.
 * @param modifier The [Modifier] to be applied to the layout that contains
 *   the [content] along with the overlay.
 * @param background The color, which fills the overlay's bounds. It is
 *   semitransparent by default, so that the covered content remains visible.
 * @param indicator The content, which is displayed in the center of the
 *   overlay, and is expected to indicate that some process is in progress.
 * @param content The content, which the overlay is displayed above.
 */
@Composable
public fun ProgressOverlay(
    active: Boolean,
    modifier: Modifier = Modifier,
    background: Color = colorScheme.surface.copy(alpha = DefaultOverlayAlpha),
    indicator: @Composable () -> Unit = { CircularProgressIndicator() },
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier,

        // Makes this wrapper transparent for the layout constraints that it
        // receives, so that wrapping the content doesn't change the way that
        // the content is measured.
        propagateMinConstraints = true
    ) {
        content()
        if (active) {
            Box(
                modifier = Modifier

                    // Takes the content's size without participating in
                    // the measurement of this layout.
                    .matchParentSize()
                    .background(background)
                    .consumePointerInput(),
                contentAlignment = Center
            ) {
                indicator()
            }
        }
    }
}

/**
 * Makes the modified layout consume all the pointer events that occur within
 * its bounds, which prevents any content below that layout from receiving
 * those events.
 *
 * The events are consumed on the [Initial] pass, which is the stage that
 * precedes the one where the gesture detectors of the content below this
 * layout would otherwise recognize them.
 *
 * @receiver The [Modifier] to which the pointer input handler is attached.
 * @return the [Modifier] that consumes the pointer input.
 */
private fun Modifier.consumePointerInput(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(Initial).changes.forEach { it.consume() }
            }
        }
    }

/**
 * Makes the modified layout consume all the key events that are addressed to
 * its content while [blocking] is `true`, which prevents the content from
 * being operated with the keyboard.
 *
 * The events are consumed before the content has a chance to handle them, so
 * neither the focused content itself, nor any keyboard shortcut declared
 * within the modified layout is triggered while the content is blocked.
 *
 * The cancellation shortcut is consumed as well, and the [onCancelShortcut]
 * callback is what makes it remain functional in the modified layout. Letting
 * the event propagate instead would expose it to the blocked content, which
 * could handle it in place of the layout's own cancellation.
 *
 * @receiver The [Modifier] to which the key event handler is attached.
 * @param blocking Specifies whether the content's key events have to be
 *   consumed currently.
 * @param onCancelShortcut The callback invoked when the cancellation shortcut
 *   (see [cancelShortcutKey]) is pressed while the content is blocked.
 * @return the [Modifier] that consumes the content's key events.
 */
internal fun Modifier.blockKeyEvents(
    blocking: Boolean,
    onCancelShortcut: () -> Unit = {}
): Modifier =
    if (blocking) {
        onPreviewKeyEvent { handleBlockedKeyEvent(it, onCancelShortcut) }
    } else {
        this
    }

/**
 * Handles a key event that is addressed to the content that is currently
 * blocked (see [blockKeyEvents]).
 *
 * @param event The key event that the blocked content would receive.
 * @param onCancelShortcut The callback invoked when [event] is the
 *   cancellation shortcut being pressed.
 * @return `true` always, since no key event can reach the blocked content.
 */
internal fun handleBlockedKeyEvent(
    event: KeyEvent,
    onCancelShortcut: () -> Unit
): Boolean {
    if (event matches cancelShortcutKey.down) {
        onCancelShortcut()
    }
    return true
}
