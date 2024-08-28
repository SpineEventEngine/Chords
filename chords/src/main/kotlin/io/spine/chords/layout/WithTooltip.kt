/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.layout

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
 * Displays the given `content` with assigning a tooltip for it.
 *
 * @param tooltip
 *         the text shown when the mouse hovers over the content.
 * @param modifier
 *         the [Modifier] to be applied to this tooltip.
 * @param tooltipCardColor
 *         the background color of the tooltip container.
 * @param shape
 *         the shape of the card for which the tip is shown.
 * @param content
 *         the content to which assign the tooltip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun WithTooltip(
    tooltip: String,
    modifier: Modifier = Modifier,
    tooltipCardColor: Color = Color.LightGray,
    shape: RoundedCornerShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 8.dp,
        bottomEnd = 8.dp,
        bottomStart = 8.dp
    ),
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = {
            Card(
                shape = shape,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = tooltipCardColor
                ),
                modifier = Modifier
                    .padding(10.dp)
                    .widthIn(min = 90.dp, max = 210.dp)
            ) {
                Text(
                    tooltip,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    modifier = Modifier.padding(10.dp)
                )
            }
        },
        modifier = modifier,
        content = content
    )
}
