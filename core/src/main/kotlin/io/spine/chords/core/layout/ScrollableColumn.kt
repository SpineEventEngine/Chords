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

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Modifier
import io.spine.chords.core.styling.ChordsTheme

/**
 * Keeps a column's overflowing content reachable without scrolling surrounding controls.
 *
 * Fills the available area, which must have a bounded height. The scrollbar gutter always reserves
 * the same width so content does not rewrap after measurement or selection changes. The scrollbar
 * appears only when needed. Content that already manages scrolling, such as a lazy list, belongs
 * outside this container.
 *
 * Example with a fixed header and action:
 * ```kotlin
 * @Composable
 * fun ActivityDetails(lines: List<String>, onClose: () -> Unit) {
 *     Column(Modifier.height(360.dp)) {
 *         Text("Activity", style = MaterialTheme.typography.titleMedium)
 *         ScrollableColumn(Modifier.weight(1F)) {
 *             lines.forEach { line -> Text(line) }
 *         }
 *         SecondaryButton(onClick = onClose) { Text("Close") }
 *     }
 * }
 * ```
 *
 * The default state retains the position while this call remains in the composition. To reset
 * it when the selected item changes, put the call inside `key(itemId)`. Use a stable identifier,
 * not the whole data object, so updates to that item do not reset its position:
 * ```kotlin
 * @Composable
 * fun SelectedItemBody(itemId: String, body: String) {
 *     key(itemId) {
 *         ScrollableColumn(Modifier.height(320.dp)) {
 *             Text(body)
 *         }
 *     }
 * }
 * ```
 *
 * The scrollbar follows `LocalScrollbarStyle`; its gutter adds the theme's small spacing.
 * Children are measured without a vertical limit. Keep actions that must stay visible outside
 * this column, and do not use child weights to fill a viewport here.
 *
 * @param modifier A modifier applied to the viewport.
 * @param scrollState The scroll position; callers can retain it or reset it with a content key.
 * @param content The vertically arranged content.
 */
@Composable
public fun ScrollableColumn(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    val overflows = scrollState.maxValue in 1 until Int.MAX_VALUE
    val gutter = LocalScrollbarStyle.current.thickness + ChordsTheme.dimensions.spacingSmall
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = gutter)
                .verticalScroll(scrollState),
            content = content
        )
        if (overflows) {
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier
                    .align(CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}
