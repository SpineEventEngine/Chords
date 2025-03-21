/*
 * Copyright 2025, TeamDev. All rights reserved.
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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.google.protobuf.Timestamp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import io.spine.chords.proto.value.time.toInstant

/**
 * A text that shows a given date being interpreted in the system time zone.
 *
 * @param date
 *         a date that should be displayed as text.
 * @param pattern
 *         a pattern for formatting the date (see [DateTimeFormatter] for the
 *         formatting syntax).
 */
@Composable
public fun DateText(date: Timestamp, pattern: String = "yyyy-MM-dd") {
    val instant = date.toInstant()
    val zoneId = ZoneId.systemDefault()
    val dateTimeFormatter = DateTimeFormatter.ofPattern(pattern)

    val zonedDateTime = instant.atZone(zoneId)
    val dateText = dateTimeFormatter.format(zonedDateTime)
    Text(dateText)
}
