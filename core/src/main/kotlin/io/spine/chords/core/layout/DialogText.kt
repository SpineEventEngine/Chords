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

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * The maximum width that a dialog's text can request for being displayed
 * without wrapping.
 *
 * The value follows the maximum width of a basic dialog in Material Design 3.
 * It prevents a long text from stretching the dialog across the screen, while
 * still allowing the typical single-sentence texts to be displayed on
 * a single line.
 */
private val MaxUnwrappedTextWidth = 560.dp

/**
 * Makes the content request the width that displays it without wrapping, but
 * no more than [maxWidth].
 *
 * A dialog, whose width is detected automatically, is as wide as the minimum
 * intrinsic width of its content (see [Dialog.width]). A text reports the width
 * of its longest word as such a width, which makes a dialog wrap the text that
 * it could have displayed on a single line.
 *
 * Applying this modifier to a dialog's text makes the dialog wide enough for
 * displaying that text without wrapping, as long as it is not wider than
 * [maxWidth]. A text that is longer than that is still wrapped.
 *
 * Note that this modifier doesn't constrain the content in any way. It only
 * affects the width that the content requests from the layout that measures it.
 *
 * @param maxWidth The maximum width that can be requested for displaying
 *   the content without wrapping.
 */
internal fun Modifier.preferUnwrappedWidth(
    maxWidth: Dp = MaxUnwrappedTextWidth
): Modifier = then(UnwrappedWidthModifier(maxWidth))

/**
 * A [LayoutModifier], which reports the content's maximum intrinsic width
 * (the width that displays the content without wrapping) as the content's
 * minimum intrinsic width, limiting it with [maxWidth].
 *
 * @property maxWidth The maximum width that can be reported as the content's
 *   minimum intrinsic width.
 */
private data class UnwrappedWidthModifier(
    private val maxWidth: Dp
) : LayoutModifier {

    /**
     * Measures the content the way it would have been measured without this
     * modifier, which only affects the intrinsic widths reported below.
     */
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeRelative(IntOffset.Zero)
        }
    }

    /**
     * Reports the width that displays the content without wrapping, so that
     * the layout, which sizes itself to the minimum intrinsic width of its
     * content, is wide enough for that.
     */
    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int = minOf(measurable.maxIntrinsicWidth(height), maxWidth.roundToPx())

    /**
     * Reports the content's maximum intrinsic width as is, since this modifier
     * doesn't limit the width that the content can occupy.
     */
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int = measurable.maxIntrinsicWidth(height)

    /**
     * Reports the content's minimum intrinsic height as is, since this modifier
     * affects the width only.
     */
    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int = measurable.minIntrinsicHeight(width)

    /**
     * Reports the content's maximum intrinsic height as is, since this modifier
     * affects the width only.
     */
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int = measurable.maxIntrinsicHeight(width)
}
