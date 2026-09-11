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

import androidx.compose.foundation.BorderStroke
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
import io.spine.chords.core.styling.ChordsTheme

/**
 * Shows a themed text tooltip when the pointer rests over [content].
 *
 * Wrap the control whose bounds should trigger the tooltip. Compose's [TooltipArea] handles
 * hover timing and popup positioning; [Tooltip] supplies the card's appearance.
 * The tooltip supplements the control's accessible description rather than replacing it.
 *
 * Example:
 * ```kotlin
 * @Composable
 * fun CloseWithHint(onClose: () -> Unit) {
 *     WithTooltip(tooltip = "Close details") {
 *         CircularIconButton(onClick = onClose) {
 *             Icon(Icons.Default.Close, contentDescription = "Close details")
 *         }
 *     }
 * }
 * ```
 *
 * The background and text follow the current theme. Supply [tooltipCardColor] to change the
 * background; the overload with `tooltipContentColor` can override both colors together.
 *
 * @param tooltip The text shown when the mouse hovers over the content.
 * @param modifier The [Modifier] applied to the tooltip area.
 * @param tooltipCardColor The tooltip background, or [Color.Unspecified] to
 *   use the current theme's overlay color.
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
 * Shows a tooltip with an explicit text-color override.
 *
 * Use the simpler overload for theme defaults. This overload requires [tooltipContentColor];
 * `Color.Unspecified` selects the same supporting foreground as the default tooltip.
 *
 * Example for text that needs the primary foreground:
 * ```kotlin
 * @Composable
 * fun StatusWithHint(status: String, explanation: String) {
 *     WithTooltip(
 *         tooltip = explanation,
 *         tooltipContentColor = MaterialTheme.colorScheme.onSurface
 *     ) {
 *         Text(status)
 *     }
 * }
 * ```
 *
 * @param tooltip The text shown when the pointer hovers over the content.
 * @param modifier The modifier applied to the hover area.
 * @param tooltipCardColor The background, or [Color.Unspecified] for the theme overlay color.
 * @param shape The tooltip card shape.
 * @param tooltipContentColor The text color, or [Color.Unspecified] for the overlay foreground.
 * @param content The content whose bounds trigger the tooltip.
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
            Tooltip(
                text = tooltip,
                containerColor = tooltipCardColor,
                contentColor = tooltipContentColor,
                shape = shape
            )
        },
        modifier = modifier,
        content = content
    )
}

/**
 * Renders tooltip text with the Chords appearance inside a custom tooltip area.
 *
 * This component draws a card; it does not detect hover or create a popup. Use [WithTooltip] for
 * a tooltip on a whole control. Use [TooltipArea] directly when the caller must control which
 * content produces a tooltip, and render this card from its `tooltip` slot:
 * ```kotlin
 * @OptIn(ExperimentalFoundationApi::class)
 * @Composable
 * fun ConditionalHint(label: String, explanation: String, showHint: Boolean) {
 *     TooltipArea(
 *         tooltip = { if (showHint) Tooltip(text = explanation) }
 *     ) {
 *         Text(label)
 *     }
 * }
 * ```
 *
 * Text wraps within a card constrained to 64–320 dp, subject to [modifier] and parent limits.
 * The card adds an outer inset, a 1 dp theme outline, and a shadow. Text uses `bodySmall`;
 * unspecified colors resolve to the overlay background and its supporting foreground.
 *
 * @param text The tooltip text.
 * @param modifier The modifier applied to the tooltip card.
 * @param containerColor The background, or [Color.Unspecified] for the theme overlay color.
 * @param contentColor The foreground, or [Color.Unspecified] for the overlay supporting foreground.
 * @param shape The tooltip card shape.
 */
@Composable
public fun Tooltip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    shape: RoundedCornerShape = RoundedCornerShape(6.dp)
) {
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (containerColor == Color.Unspecified) {
                ChordsTheme.overlayColor
            } else {
                containerColor
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .padding(8.dp)
            .widthIn(min = 64.dp, max = 320.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (contentColor == Color.Unspecified) {
                ChordsTheme.overlaySupportingColor
            } else {
                contentColor
            },
            modifier = Modifier.padding(
                horizontal = ChordsTheme.dimensions.spacingMedium,
                vertical = ChordsTheme.dimensions.spacingSmall
            )
        )
    }
}
