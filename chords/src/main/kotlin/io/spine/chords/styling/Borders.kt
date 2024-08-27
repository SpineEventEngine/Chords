/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.styling

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Adds a border line that goes along the right edge.
 *
 * @param lineWidthDp
 *         width of the border line that should be drawn.
 * @param color
 *         color of the border line that should be drawn.
 */
@Composable
@ReadOnlyComposable
public fun Modifier.borderRight(lineWidthDp: Dp, color: Color): Modifier {
    val lineWidthPx = with(LocalDensity.current) { lineWidthDp.toPx() }
    return border(width = lineWidthDp, color, GenericShape { size, _ ->
        moveTo(size.width, 0f)
        lineTo(size.width, size.height)
        lineTo(size.width - lineWidthPx, size.height)
        lineTo(size.width - lineWidthPx, 0f)
    })
}

/**
 * Adds a border line that goes along the bottom edge.
 *
 * @param lineWidthDp
 *         width of the border line that should be drawn.
 * @param color
 *         color of the border line that should be drawn.
 */
@Composable
@ReadOnlyComposable
public fun Modifier.borderBottom(lineWidthDp: Dp, color: Color): Modifier {
    val lineWidthPx = with(LocalDensity.current) { lineWidthDp.toPx() }
    return border(width = lineWidthDp, color, GenericShape { size, _ ->
        moveTo(0f, size.height)
        lineTo(size.width, size.height)
        lineTo(size.width, size.height - lineWidthPx)
        lineTo(0f, size.height - lineWidthPx)
    })
}
