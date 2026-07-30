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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Tests the way that a dialog, whose size is detected automatically,
 * measures its width.
 */
internal class DialogSizingSpec {

    /**
     * A window's size is expressed in whole [Dp] units, while its content is
     * measured in pixels, and the conversion between the two discards the
     * fractional part. A width that doesn't survive that conversion makes
     * the window squeeze its content, which, e.g., truncates the labels of
     * the dialog's buttons.
     */
    @Test
    fun `provide the width that a dialog's window is able to reproduce`() {
        val contentWidth = 400.5.dp

        val width = dialogWidth {
            ContentStub(contentWidth, contentWidth)
        }

        val requiredWidth = with(density) { contentWidth.roundToPx() }
        width shouldBeGreaterThanOrEqual requiredWidth
        windowContentWidth(width) shouldBeGreaterThanOrEqual requiredWidth
    }

    /**
     * The buttons section is a part of the dialog's content, so the width
     * detected for the dialog has to display the buttons the way they need,
     * instead of making them squeeze their labels.
     */
    @Test
    fun `provide the width that the dialog's buttons need`() {
        val dialog = dialogWithLongButtonLabels()

        val buttonsWidth = measureWidth {
            MaterialTheme {
                Box(modifier = Modifier.width(IntrinsicSize.Max)) {
                    dialog.buttonsSectionInternal()
                }
            }
        }
        val width = dialogWidth {
            DialogContent(dialog)
        }

        // Makes sure that it is the buttons, and not the minimum dialog width,
        // that the detected width comes from.
        buttonsWidth shouldBeGreaterThan with(density) { DefaultDialogMinWidth.roundToPx() }
        width shouldBeGreaterThanOrEqual buttonsWidth
    }

    /**
     * A dialog has to remain usable on the display that it is shown on, so
     * the space available for the dialog wins over the width that the dialog's
     * buttons ask for.
     */
    @Test
    fun `cap the width that the dialog's buttons need with the available space`() {
        val dialog = dialogWithLongButtonLabels()
        val availableWidth = 300.dp

        val width = dialogWidth(availableWidth) {
            DialogContent(dialog)
        }

        width shouldBe with(density) { availableWidth.roundToPx() }
    }

    /**
     * A text that fits on a single line shouldn't be wrapped only because
     * the dialog is as narrow as the longest word of that text.
     */
    @Test
    fun `provide the width that displays the dialog's text without wrapping`() {
        val unwrappedWidth = 500.dp

        val width = dialogWidth {
            ContentStub(
                minIntrinsicWidth = 100.dp,
                maxIntrinsicWidth = unwrappedWidth,
                modifier = Modifier.preferUnwrappedWidth()
            )
        }

        width shouldBe with(density) { unwrappedWidth.roundToPx() }
    }

    /**
     * A long text is wrapped rather than stretching the dialog, so the width
     * that such a text asks for is limited.
     */
    @Test
    fun `limit the width that a long text requests`() {
        val maxTextWidth = 480.dp

        val width = dialogWidth {
            ContentStub(
                minIntrinsicWidth = 100.dp,
                maxIntrinsicWidth = 900.dp,
                modifier = Modifier.preferUnwrappedWidth(maxTextWidth)
            )
        }

        width shouldBe with(density) { maxTextWidth.roundToPx() }
    }

    /**
     * Content that is wider than the space available for the dialog cannot
     * make the dialog overflow the display that it is shown on.
     */
    @Test
    fun `cap the width with the space that is available for the dialog`() {
        val width = dialogWidth {
            ContentStub(AvailableWidth * 2, AvailableWidth * 2)
        }

        width shouldBe with(density) { AvailableWidth.roundToPx() }
    }

    /**
     * Creates a [ConfirmationDialog], whose buttons are wider than
     * the minimum width of an automatically sized dialog.
     */
    private fun dialogWithLongButtonLabels() = ConfirmationDialog().apply {
        message = "Proceed?"
        noButtonText = "Continue editing the command"
        yesButtonText = "Discard all the changes made so far"
    }

    /**
     * Displays the content of the given dialog's window the way that
     * a [WindowType] implementation does.
     *
     * @param dialog The dialog whose content is to be displayed.
     */
    @Composable
    private fun DialogContent(dialog: Dialog) {
        MaterialTheme {
            Column {
                dialog.windowContentInternal(DialogContentHeightMode.Natural)
            }
        }
    }

    /**
     * Measures the width, in pixels, of an automatically sized dialog with
     * the given content.
     *
     * @param availableWidth The width available for the dialog.
     * @param content The content of the dialog to measure.
     * @return The measured width of the dialog.
     */
    private fun dialogWidth(
        availableWidth: Dp = AvailableWidth,
        content: @Composable () -> Unit
    ): Int = measureWidth(availableWidth) {
        Box(
            modifier = Modifier.dialogSize(
                width = Dp.Unspecified,
                height = Dp.Unspecified,
                maxWidth = availableWidth,
                maxHeight = AvailableHeight,
                fillSpecifiedDimensions = false
            )
        ) {
            content()
        }
    }

    /**
     * Measures the width, in pixels, that the given content occupies within
     * the specified available width.
     *
     * @param availableWidth The width available for the content.
     * @param content The content to measure.
     * @return The measured width of the content.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun measureWidth(
        availableWidth: Dp = AvailableWidth,
        content: @Composable () -> Unit
    ): Int {
        val scene = ComposeScene(density = density)
        try {
            scene.constraints = with(density) {
                Constraints(
                    maxWidth = availableWidth.roundToPx(),
                    maxHeight = AvailableHeight.roundToPx()
                )
            }
            scene.setContent(content)
            return scene.contentSize.width
        } finally {
            scene.close()
        }
    }

    /**
     * Emulates the way that Compose Desktop sizes a window from the size that
     * was measured for its content: the measured size is converted into
     * the whole [Dp] units of the window's size, and the content of that window
     * is then measured against those units again.
     *
     * @param width The width, in pixels, measured for the window's content.
     * @return The width, in pixels, that the window makes available
     *   to its content.
     */
    private fun windowContentWidth(width: Int): Int {
        val scale = density.density
        val windowWidth = (width / scale).toInt()
        return (windowWidth * scale).toInt()
    }

    /**
     * Displays no content, and reports the specified intrinsic widths.
     *
     * It stands for a text, whose minimum intrinsic width is the width of its
     * longest word, and whose maximum intrinsic width is the width that
     * displays it without wrapping.
     *
     * @param minIntrinsicWidth The minimum intrinsic width to report.
     * @param maxIntrinsicWidth The maximum intrinsic width to report.
     * @param modifier The modifier to apply to this content.
     */
    @Composable
    private fun ContentStub(
        minIntrinsicWidth: Dp,
        maxIntrinsicWidth: Dp,
        modifier: Modifier = Modifier
    ) {
        Layout(
            content = {},
            modifier = modifier,
            measurePolicy = object : MeasurePolicy {

                /**
                 * Occupies the space that the content is measured against.
                 */
                override fun MeasureScope.measure(
                    measurables: List<Measurable>,
                    constraints: Constraints
                ): MeasureResult = layout(constraints.minWidth, constraints.minHeight) {}

                /**
                 * Reports the width of the content's longest "word".
                 */
                override fun IntrinsicMeasureScope.minIntrinsicWidth(
                    measurables: List<IntrinsicMeasurable>,
                    height: Int
                ): Int = minIntrinsicWidth.roundToPx()

                /**
                 * Reports the width that displays the content without wrapping.
                 */
                override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                    measurables: List<IntrinsicMeasurable>,
                    height: Int
                ): Int = maxIntrinsicWidth.roundToPx()

                /**
                 * Reports no height, as this content is measured by width only.
                 */
                override fun IntrinsicMeasureScope.minIntrinsicHeight(
                    measurables: List<IntrinsicMeasurable>,
                    width: Int
                ): Int = 0

                /**
                 * Reports no height, as this content is measured by width only.
                 */
                override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                    measurables: List<IntrinsicMeasurable>,
                    width: Int
                ): Int = 0
            }
        )
    }

    private companion object {

        /**
         * The density that makes a whole [Dp] unit occupy more than one pixel,
         * like the one of a high-resolution display.
         */
        val density = Density(2f)

        /**
         * The width that is available to the dialog being measured.
         */
        val AvailableWidth = 1200.dp

        /**
         * The height that is available to the dialog being measured.
         */
        val AvailableHeight = 800.dp
    }
}
