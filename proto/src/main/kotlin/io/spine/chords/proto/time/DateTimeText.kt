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

package io.spine.chords.proto.time

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.google.protobuf.Timestamp
import java.time.format.DateTimeFormatter

/**
 * Renders the given timestamp with the pattern that includes the date
 * and time values being interpreted in the system time zone.
 *
 * @param dateTime A dateTime value that should be displayed as text.
 * @param pattern A dateTime formatting pattern (see [DateTimeFormatter]
 *         for the formatting syntax).
 * @param modifier A component's layout and behaviour decorator.
 * @param color A color that will be applied to the text.
 * @param fontWeight A font thickness value that will be applied to the text.
 */
@Composable
public fun DateTimeText(
    dateTime: Timestamp,
    pattern: String = "yyyy-MM-dd hh:mm a",
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null
) {
    DateTimeText(
        dateTime = dateTime,
        pattern = pattern,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        maxLines = Int.MAX_VALUE
    )
}

/**
 * Displays a timestamp with a line limit for constrained layouts such as table cells.
 *
 * The timestamp is interpreted in the system time zone. Text beyond [maxLines] uses [overflow].
 *
 * ```kotlin
 * DateTimeText(
 *     dateTime = timestamp,
 *     maxLines = 1,
 *     overflow = TextOverflow.Ellipsis
 * )
 * ```
 *
 * @param dateTime The timestamp to display.
 * @param pattern The date/time formatting pattern (see [DateTimeFormatter]
 *   for the formatting syntax).
 * @param modifier Layout and behavior adjustments.
 * @param color The text color.
 * @param fontWeight The text's font weight.
 * @param maxLines The positive maximum number of displayed lines.
 * @param overflow How text exceeding the available space is displayed.
 */
@Composable
@Suppress("LongParameterList" /* Mirrors the standard text layout and styling options. */)
public fun DateTimeText(
    dateTime: Timestamp,
    pattern: String = "yyyy-MM-dd hh:mm a",
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int,
    overflow: TextOverflow = TextOverflow.Clip
) {
    DateText(
        dateTime,
        pattern,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow
    )
}
