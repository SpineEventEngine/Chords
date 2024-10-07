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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
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
 * @param onCancel The callback triggered when the user clicks outside the modal window.
 * @param content The content to display as a modal window.
 */
@Composable
public fun ModalWindow(
    onCancel: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Popup(
        popupPositionProvider = centerPopupPositionProvider,
        onDismissRequest = onCancel,
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { false },
        onKeyEvent = cancelOnEscape(onCancel)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(onCancel) {
                    detectTapGestures(onPress = { onCancel() })
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.pointerInput(onCancel) {
                    detectTapGestures(onPress = {})
                }
            ) {
                content()
            }
        }
    }
}

/**
 * Provides a modal window setting that forces it
 * to appear at the center of the screen.
 */
private val centerPopupPositionProvider = object : PopupPositionProvider {
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
        if (it.type == KeyEventType.KeyDown && it.awtEventOrNull?.keyCode == VK_ESCAPE) {
            onCancel()
            true
        } else {
            false
        }
    }
