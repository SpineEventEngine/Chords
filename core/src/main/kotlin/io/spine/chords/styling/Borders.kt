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
