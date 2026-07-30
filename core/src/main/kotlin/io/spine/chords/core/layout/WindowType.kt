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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Gray
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntOffset.Companion.Zero
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.spine.chords.core.keyboard.matches
import kotlin.math.ceil

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
            val initialSize = DpSize(dialog.width, dialog.height)
            val state = remember(dialog, initialSize) {
                DialogState(size = initialSize)
            }
            var contentFittedSize by remember(dialog, initialSize) {
                mutableStateOf<DpSize?>(null)
            }
            LaunchedEffect(state.size) {
                if (
                    contentFittedSize == null &&
                    state.size.width.isSpecified &&
                    state.size.height.isSpecified
                ) {
                    val candidate = state.size
                    withFrameNanos {}
                    if (state.size == candidate) {
                        contentFittedSize = candidate
                    }
                }
            }
            DialogWindow(
                title = dialog.title,
                resizable = resizable,
                state = state,
                onCloseRequest = { dialog.cancel() },
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
                DesktopDialogContent(dialog, state, contentFittedSize)
            }
        }

        @Composable
        private fun DesktopDialogContent(
            dialog: Dialog,
            state: DialogState,
            contentFittedSize: DpSize?
        ) {
            BoxWithConstraints {
                val resized =
                    resizable && contentFittedSize?.let { state.size != it } == true
                val sizeModifier = if (resized) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.dialogSize(
                        dialog.width,
                        dialog.height,
                        maxWidth,
                        maxHeight,
                        fillSpecifiedDimensions = true
                    )
                }
                Column(
                    modifier = sizeModifier
                        .background(colorScheme.background),
                ) {
                    val heightMode =
                        if (dialog.height.isSpecified || contentFittedSize != null) {
                            DialogContentHeightMode.Exact
                        } else {
                            DialogContentHeightMode.AtMost
                        }
                    val contentModifier = if (heightMode == DialogContentHeightMode.Exact) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    Column(
                        modifier = contentModifier.padding(dialog.look.padding),
                    ) {
                        dialog.windowContentInternal(heightMode)
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
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backdropColor),
                    contentAlignment = Center
                ) {
                    val availableWidth = maxWidth
                    val availableHeight = maxHeight
                    val modifier = if (dialog.isBottomDialog) {
                        Modifier.pointerInput(dialog) {
                            detectTapGestures(onPress = {})
                        }
                    } else {
                        Modifier
                    }
                    Box(modifier = modifier) {
                        dialogFrame(dialog, availableWidth, availableHeight)
                    }
                }
            }
        }

        @Composable
        private fun dialogFrame(
            dialog: Dialog,
            maxWidth: Dp,
            maxHeight: Dp
        ) {
            Column(
                modifier = Modifier
                    .clip(shapes.large)
                    .dialogSize(
                        dialog.width,
                        dialog.height,
                        maxWidth,
                        maxHeight,
                        fillSpecifiedDimensions = false
                    )
                    .background(colorScheme.background),
            ) {
                val heightMode = if (dialog.height.isSpecified) {
                    DialogContentHeightMode.Exact
                } else {
                    DialogContentHeightMode.AtMost
                }
                val contentModifier = if (heightMode == DialogContentHeightMode.Exact) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth()
                }
                Column(
                    modifier = contentModifier.padding(dialog.look.padding),
                ) {
                    DialogTitle(dialog.title, dialog.look.titlePadding)
                    dialog.windowContentInternal(heightMode)
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
 * The minimum width used for automatically-sized dialogs.
 *
 * This prevents short text from producing impractically narrow windows while
 * still allowing wider form layouts to determine their preferred width.
 */
internal val DefaultDialogMinWidth = 400.dp

/**
 * Measures unspecified dimensions from the content and caps them at the
 * available window size.
 *
 * Minimum intrinsic width is intentional: it keeps text-based dialogs compact
 * instead of expanding them to the width of an unwrapped paragraph. A text that
 * should still be displayed on a single line requests such a width explicitly
 * (see [preferUnwrappedWidth]).
 */
internal fun Modifier.dialogSize(
    width: Dp,
    height: Dp,
    maxWidth: Dp,
    maxHeight: Dp,
    fillSpecifiedDimensions: Boolean
): Modifier =
    then(
        if (width.isSpecified) {
            if (fillSpecifiedDimensions) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.width(width)
            }
        } else {
            Modifier
                .then(WindowSafeMinIntrinsicWidth)
                .widthIn(
                    min = minOf(DefaultDialogMinWidth, maxWidth),
                    max = maxWidth
                )
        }
    ).then(
        if (height.isSpecified) {
            if (fillSpecifiedDimensions) {
                Modifier.fillMaxHeight()
            } else {
                Modifier.height(height)
            }
        } else {
            Modifier.heightIn(max = maxHeight)
        }
    )

/**
 * Sizes the content to its minimum intrinsic width, rounded up so that
 * the window that displays this content is guaranteed to be wide enough
 * for it.
 *
 * This modifier is an analog of `Modifier.width(IntrinsicSize.Min)`, which
 * additionally accounts for the fact that a window's size is measured in whole
 * [Dp] units, while its content is measured in pixels. The conversion of the
 * content's width into the window's width discards the fractional part of
 * a [Dp] value, so a window whose size is derived from the content can end up
 * narrower than the content that it was measured from.
 *
 * Such a window makes its content squeeze into the space that is smaller than
 * the one that the content has reported as needed, which, e.g., renders
 * the dialog's button labels with an ellipsis (see [Dialog]).
 */
private object WindowSafeMinIntrinsicWidth : LayoutModifier {

    /**
     * Measures the content with the width that it reports as its minimum
     * intrinsic one, extended up to the window-safe width, and limited with
     * the incoming constraints.
     */
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val width = windowSafeWidth(measurable.minIntrinsicWidth(constraints.maxHeight))
        val placeable = measurable.measure(
            constraints.constrain(Constraints.fixedWidth(width))
        )
        return layout(placeable.width, placeable.height) {
            placeable.placeRelative(IntOffset.Zero)
        }
    }

    /**
     * Reports the width that this modifier makes the content occupy.
     */
    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int = windowSafeWidth(measurable.minIntrinsicWidth(height))

    /**
     * Reports the same width as [minIntrinsicWidth] does, since this modifier
     * makes the content occupy that width in either case.
     */
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int = windowSafeWidth(measurable.minIntrinsicWidth(height))

    /**
     * Extends the given width, which is expressed in pixels, up to the width
     * that survives being converted into the whole [Dp] units of a window's
     * size and then back into the pixels of that window's content.
     *
     * @receiver The density that converts between pixels and [Dp] units.
     * @param width The width in pixels that the content needs.
     * @return The width in pixels, which is not less than [width].
     */
    private fun Density.windowSafeWidth(width: Int): Int {
        val windowWidth = ceil(width / density)
        return ceil(windowWidth * density).toInt()
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
        modifier = Modifier
            .padding(padding)
            .preferUnwrappedWidth(),
        text = text,
        style = typography.headlineLarge
    )
}
