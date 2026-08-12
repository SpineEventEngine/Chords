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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Displays the given [content] with a tooltip using theme content colors.
 *
 * @param tooltip The text shown when the mouse hovers over the content.
 * @param modifier The [Modifier] applied to the tooltip area.
 * @param tooltipCardColor The tooltip background, or [Color.Unspecified] to
 *   use the current theme's inverse surface color.
 * @param shape The tooltip container shape.
 * @param content The content to which the tooltip is assigned.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun WithTooltip(
    tooltip: String,
    modifier: Modifier = Modifier,
    tooltipCardColor: Color = Color.Unspecified,
    shape: RoundedCornerShape = RoundedCornerShape(6.dp),
    content: @Composable () -> Unit
) {
    WithTooltip(
        tooltip = tooltip,
        modifier = modifier,
        tooltipCardColor = tooltipCardColor,
        shape = shape,
        tooltipContentColor = Color.Unspecified,
        content = content
    )
}

/**
 * Displays the given `content` with assigning a tooltip for it.
 *
 * This overload adds a text-color override while the original five-parameter
 * function remains available for source and binary compatibility. The required
 * [tooltipContentColor] keeps calls to the two overloads unambiguous.
 *
 * @param tooltip
 *         the text shown when the mouse hovers over the content.
 * @param modifier
 *         the [Modifier] to be applied to this tooltip.
 * @param tooltipCardColor
 *         the background color of the tooltip container.
 * @param shape
 *         the shape of the card for which the tip is shown.
 * @param tooltipContentColor
 *         the color of the tooltip text, or [Color.Unspecified] to use the
 *         current theme's inverse surface content color.
 * @param content
 *         the content to which assign the tooltip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun WithTooltip(
    tooltip: String,
    modifier: Modifier = Modifier,
    tooltipCardColor: Color = Color.Unspecified,
    shape: RoundedCornerShape = RoundedCornerShape(6.dp),
    tooltipContentColor: Color,
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = {
            Card(
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = if (tooltipCardColor == Color.Unspecified) {
                        MaterialTheme.colorScheme.inverseSurface
                    } else {
                        tooltipCardColor
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .padding(8.dp)
                    .widthIn(min = 64.dp, max = 320.dp)
            ) {
                Text(
                    tooltip,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooltipContentColor == Color.Unspecified) {
                        MaterialTheme.colorScheme.inverseOnSurface
                    } else {
                        tooltipContentColor
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }
        },
        modifier = modifier,
        content = content
    )
}
