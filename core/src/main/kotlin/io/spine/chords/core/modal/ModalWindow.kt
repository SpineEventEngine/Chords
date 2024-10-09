/*
 * Copyright 2024, TeamDev. All rights reserved.
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

package io.spine.chords.core.modal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyDown
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import java.awt.event.KeyEvent.VK_ESCAPE

/**
 * The modal window with a customizable content.
 *
 * This component itself does not provide any visual presentation.
 * It only displays the content that is supplied via the corresponding parameter.
 * The responsibility for handling the appearance, layout, and structure
 * of the modal window is delegated to the provided composable content block.
 *
 * The closing behavior of the modal window can be configured in two ways:
 *
 * 1. The cancel confirmation dialog. When the user attempts to close the modal,
 *    a confirmation dialog is shown before closing. This can be configured using
 *    the `ModalWindowConfig.cancelConfirmationWindow` parameter.
 *
 * 2. Immediate close (default). The modal can be closed by either:
 *    - Clicking outside the window.
 *    - Pressing the `Escape` key.
 *
 * If the `cancelConfirmationDialog` is not provided, the modal will close immediately
 * when the user clicks outside or presses `Escape`.
 *
 * @param onCancel The callback triggered when the user clicks outside the modal window.
 * @param config The configuration of the modal window.
 */
@Composable
public fun ModalWindow(
    onCancel: () -> Unit,
    config: ModalWindowConfig
) {
    val (content, cancelConfirmationDialog) = config
    val cancelConfirmationShown = remember { mutableStateOf(false) }
    Popup(
        popupPositionProvider = centerWindowPositionProvider,
        onDismissRequest = onCancel,
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { false },
        onKeyEvent = cancelOnEscape {
            if (cancelConfirmationDialog != null) {
                cancelConfirmationShown.value = true
            } else {
                onCancel()
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(onCancel) {
                    detectTapGestures(onPress = {
                        if (cancelConfirmationDialog == null) {
                            onCancel()
                        }
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.pointerInput(onCancel) {
                    detectTapGestures(onPress = {})
                }
            ) {
                content {
                    cancelConfirmationShown.value = true
                }
            }
        }
        if (cancelConfirmationShown.value && cancelConfirmationDialog != null) {
            CancelConfirmationDialogView(
                onCancel = { cancelConfirmationShown.value = false },
                onConfirm = { onCancel() },
                content = cancelConfirmationDialog
            )
        }
    }
}

/**
 * Configuration of the modal window.
 *
 * This object allows to define the content that will be displayed as
 * the modal window, as well as an optional cancel confirmation dialog. If the
 * `cancelConfirmationDialog` property is set to `null`, the modal will close
 * immediately when the user clicks outside or presses `Escape`.
 * Otherwise, the cancel confirmation dialog will be displayed to confirm the closure.
 *
 * @param content The content to display as a modal window.
 * @param cancelConfirmationDialog The configuration of the cancel confirmation dialog.
 */
public data class ModalWindowConfig(
    val content: @Composable BoxScope.(onShowCancelConfirmation: () -> Unit) -> Unit,
    val cancelConfirmationDialog: CancelConfirmationDialog? = null
)

/**
 * A type of the cancel confirmation modal window.
 *
 * @see CancelConfirmationDialogView
 */
public typealias CancelConfirmationDialog =
        @Composable BoxScope.(onConfirm: () -> Unit, onCancel: () -> Unit) -> Unit

/**
 * The cancel confirmation modal window for the [ModalWindow] component.
 *
 * This window confirms or denies the intention of the user to close the main modal window.
 *
 * @param onCancel The callback triggered on this window cancellation.
 * @param onConfirm The callback triggered on the confirmation to cancel the main modal window.
 * @param content The content to display as a modal window.
 */
@Composable
private fun CancelConfirmationDialogView(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    content: CancelConfirmationDialog
) {
    Popup(
        popupPositionProvider = centerWindowPositionProvider,
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { false }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Box {
                content(onConfirm, onCancel)
            }
        }
    }
}

/**
 * Provides a modal window setting that forces it
 * to appear at the center of the screen.
 */
private val centerWindowPositionProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset.Zero
}

/**
 * Returns a function that executes a provided `onCancel` callback
 * whenever the `Escape` keyboard button is pressed.
 */
private fun cancelOnEscape(onCancel: () -> Unit): ((KeyEvent) -> Boolean) =
    {
        if (it.type == KeyDown && it.awtEventOrNull?.keyCode == VK_ESCAPE) {
            onCancel()
            true
        } else {
            false
        }
    }
