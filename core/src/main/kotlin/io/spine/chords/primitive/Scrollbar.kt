/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.primitive

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Vertical scrollbar component.
 *
 * @param scrollState
 *         state of the scroll.
 * @param modifierExtender
 *         extension for the modifier.
 */
@Composable
public fun VerticalScrollbar(
    scrollState: ScrollState,
    modifierExtender: Modifier.() -> Modifier
) {
    VerticalScrollbar(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 5.dp)
            .modifierExtender(),
        adapter = rememberScrollbarAdapter(
            scrollState = scrollState
        )
    )
}

/**
 * Horizontal scrollbar component.
 *
 * @param scrollState
 *         state of the scroll.
 * @param modifierExtender
 *         extension for the modifier.
 */
@Composable
public fun HorizontalScrollbar(
    scrollState: ScrollState,
    modifierExtender: Modifier.() -> Modifier
) {
    HorizontalScrollbar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp)
            .modifierExtender(),
        adapter = rememberScrollbarAdapter(
            scrollState = scrollState
        )
    )
}
