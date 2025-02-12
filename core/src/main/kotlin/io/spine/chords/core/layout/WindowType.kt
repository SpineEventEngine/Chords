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

package io.spine.chords.core.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Gray
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntOffset.Companion.Zero
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.spine.chords.core.keyboard.matches

/**
 * Defines the way that a dialog is displayed on the screen (e.g. as a separate
 * desktop window, or as a lightweight modal popup).
 */
public sealed class WindowType {

    /**
     * Renders the dialog window according to the display mode defined by this
     * object.
     *
     * @param dialog The [Dialog] that is being displayed.
     */
    @Composable
    public abstract fun dialogWindow(dialog: Dialog)

    /**
     * A [WindowType] implementation, which ensures displaying a dialog
     * as a separate desktop window.
     *
     * @param resizable Specifies whether the window can be resized by the user.
     */
    public open class DesktopWindow(
        public val resizable: Boolean = false
    ) : WindowType() {

        @Composable
        override fun dialogWindow(dialog: Dialog) {
            DialogWindow(
                title = dialog.title,
                resizable = resizable,
                state = DialogState(
                    size = DpSize(dialog.width, dialog.height)
                ),
                onCloseRequest = { dialog.close() },
                onKeyEvent = { event ->
                    if (dialog.cancelAvailableInternal && event matches cancelShortcutKey.down) {
                        dialog.cancel()
                    }
                    if (dialog.submitAvailableInternal && event matches submitShortcutKey.up) {
                        dialog.submit()
                    }
                    false
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(dialog.look.padding),
                    ) {
                        dialog.windowContentInternal()
                        dialog.nestedDialog?.Content()
                    }
                }
            }
        }

        /**
         * A default reusable instance of [DesktopWindow], which can be used if
         * no additional customizations are required.
         *
         * Here's an example of how it can be used:
         * ```
         *     Dialog {
         *         windowType = DesktopWindow
         *         ...
         *     }
         * ```
         *
         * If any customizations are required (e.g. if you need to make the
         * window resizable), just create a new [DesktopWindow] instance with
         * respective parameters, like this:
         * ```
         *     Dialog {
         *         windowType = DesktopWindow(resizable = true)
         *         ...
         *     }
         * ```
         */
        public companion object : DesktopWindow(
            resizable = false
        )
    }

    /**
     * A [WindowType] implementation, which ensures displaying a dialog
     * as a lightweight modal popup inside the current desktop window.
     *
     * @param backdropColor The color of the surface that covers the entire
     *   content of the current desktop window behind the dialog's modal popup
     *   displayed in this window.
     */
    public open class LightweightWindow(
        public val backdropColor: Color = Gray.copy(alpha = 0.5f)
    ) : WindowType() {

        @Composable
        override fun dialogWindow(dialog: Dialog) {
            Popup(
                popupPositionProvider = centerWindowPositionProvider,
                properties = PopupProperties(focusable = true),
                onPreviewKeyEvent = { false },
                onKeyEvent = cancelShortcutHandler {
                    if (dialog.cancelAvailableInternal) {
                        dialog.cancel()
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backdropColor),
                    contentAlignment = Center
                ) {
                    val modifier = if (dialog.isBottomDialog) {
                        Modifier.pointerInput(dialog) {
                            detectTapGestures(onPress = {})
                        }
                    } else {
                        Modifier
                    }
                    Box(modifier = modifier) {
                        dialogFrame(dialog)
                    }
                }
            }
        }

        @Composable
        private fun dialogFrame(dialog: Dialog) {
            Column(
                modifier = Modifier
                    .clip(shapes.large)
                    .size(dialog.width, dialog.height)
                    .background(colorScheme.background),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dialog.look.padding),
                ) {
                    DialogTitle(dialog.title, dialog.look.titlePadding)
                    dialog.windowContentInternal()
                    dialog.nestedDialog ?.Content()
                }
            }
        }

        /**
         * Creates a key event handler function that executes a provided
         * [cancelHandler] callback whenever the `Escape` key is pressed.
         */
        private fun cancelShortcutHandler(
            cancelHandler: () -> Unit
        ): (KeyEvent) -> Boolean = { event ->
            if (event matches cancelShortcutKey.down) {
                cancelHandler()
                true
            } else {
                false
            }
        }

        /**
         * A default reusable instance of [LightweightWindow], which can be used if
         * no additional customizations are required.
         *
         * Here's an example of how it can be used:
         * ```
         *     Dialog {
         *         windowType = LightweightWindow
         *         ...
         *     }
         * ```
         *
         * If any customizations are required (e.g. if you need to change the
         * backdrop color), just create a new [LightweightWindow] instance with
         * respective parameters, like this:
         * ```
         *     Dialog {
         *         windowType = LightweightWindow(
         *             backdropColor = White.copy(alpha = 0.5f)
         *         )
         *         ...
         *     }
         * ```
         */
        public companion object : LightweightWindow()

    }
}

/**
 * A [PopupPositionProvider], which makes a lightweight popup to appear at
 * the window's center.
 *
 * @see WindowType.LightweightWindow
 */
private val centerWindowPositionProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = Zero
}

/**
 * The title of the dialog for lightweight windows.
 *
 * @param text The text to be displayed as the window's title.
 * @see WindowType.LightweightWindow
 */
@Composable
private fun DialogTitle(
    text: String,
    padding: PaddingValues
) {
    Text(
        modifier = Modifier.padding(padding),
        text = text,
        style = typography.headlineLarge
    )
}
