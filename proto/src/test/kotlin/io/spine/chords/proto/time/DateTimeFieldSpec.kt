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
 * modification, are permitted provided that the following conditions are met:
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

import com.google.protobuf.Timestamp
import com.google.protobuf.util.Timestamps
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.DisplayName
import io.kotest.matchers.shouldBe
import io.spine.chords.core.ParseException
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

private const val TestDateTimePattern = "yyyy-MM-dd HH:mm"
private const val OutOfRangeMessage = "Enter a date/time within the supported range"

@DisplayName("`DateTimeField` should")
internal class DateTimeFieldSpec {

    @Test
    fun `reject a local date-time before the Protobuf range`() {
        val exception = shouldThrow<ParseException> {
            parseDateTime("000101010000", TestDateTimePattern, ZoneOffset.ofHours(2))
        }

        exception.validationErrorMessage shouldBe OutOfRangeMessage
    }

    @Test
    fun `accept the minimum Protobuf timestamp in a positive offset`() {
        val result = parseDateTime(
            "000101010200",
            TestDateTimePattern,
            ZoneOffset.ofHours(2)
        )

        result shouldBe Timestamps.MIN_VALUE
    }

    @Test
    fun `reject a local date-time after the Protobuf range`() {
        val exception = shouldThrow<ParseException> {
            parseDateTime("999912312359", TestDateTimePattern, ZoneOffset.ofHours(-2))
        }

        exception.validationErrorMessage shouldBe OutOfRangeMessage
    }

    @Test
    fun `parse an ordinary local date-time`() {
        val result = parseDateTime("202507011230", TestDateTimePattern, ZoneOffset.UTC)
        val expected = Timestamp.newBuilder()
            .setSeconds(Instant.parse("2025-07-01T12:30:00Z").epochSecond)
            .build()

        result shouldBe expected
    }
}
