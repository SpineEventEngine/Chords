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

import androidx.compose.runtime.mutableStateOf
import com.google.protobuf.Timestamp
import com.google.protobuf.util.Timestamps
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.chords.core.ParseException
import io.spine.chords.proto.TestApplication
import io.spine.chords.proto.testing.inScene
import io.spine.chords.proto.testing.onUiThread
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit.MINUTES
import java.util.TimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * The default editor resolution used by these conversion scenarios.
 */
private const val TestDateTimePattern = "yyyy-MM-dd HH:mm"

/**
 * Verifies local input conversion, timestamp limits, and editor precision.
 */
@Isolated
@DisplayName("`DateTimeField` should")
internal class DateTimeFieldSpec {

    /**
     * Preserves the time zone of the surrounding test JVM.
     */
    private val originalTimeZone = TimeZone.getDefault()

    /**
     * Keeps local-time scenarios independent of other suites.
     */
    @AfterEach
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    /**
     * Winter input must use the winter offset even when the current date is in summer.
     */
    @Test
    fun `use the local winter offset for input and display`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val timestamp = Timestamps.parse("2026-01-15T11:30:00Z")

        val text = formatDateTime(timestamp, TestDateTimePattern)
        val parsed = parseDateTime("202601151230", TestDateTimePattern)

        text shouldBe "202601151230"
        parsed shouldBe timestamp
    }

    /**
     * Summer input must use daylight-saving time even when the current date is in winter.
     */
    @Test
    fun `use the local summer offset for input and display`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val timestamp = Timestamps.parse("2026-07-15T10:30:00Z")

        val text = formatDateTime(timestamp, TestDateTimePattern)
        val parsed = parseDateTime("202607151230", TestDateTimePattern)

        text shouldBe "202607151230"
        parsed shouldBe timestamp
    }

    /**
     * Local midnight may belong to the previous UTC date, including fractional-hour offsets.
     */
    @Test
    fun `convert local input across the UTC date boundary`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kathmandu"))
        val timestamp = Timestamps.parse("2026-07-14T18:45:00Z")

        val text = formatDateTime(timestamp, TestDateTimePattern)
        val parsed = parseDateTime("202607150030", TestDateTimePattern)

        text shouldBe "202607150030"
        parsed shouldBe timestamp
    }

    /**
     * Rejects a local value whose UTC instant precedes the supported timestamp range.
     */
    @Test
    fun `reject a local date-time before the Protobuf range`() {
        shouldThrow<ParseException> {
            parseDateTime(
                rawText = "000101010000",
                dateTimePattern = TestDateTimePattern,
                zone = ZoneOffset.ofHours(2)
            )
        }
    }

    /**
     * Accepts the lower timestamp boundary after accounting for the local offset.
     */
    @Test
    fun `accept the minimum Protobuf timestamp in a positive offset`() {
        val result = parseDateTime(
            rawText = "000101010200",
            dateTimePattern = TestDateTimePattern,
            zone = ZoneOffset.ofHours(2)
        )

        result shouldBe Timestamps.MIN_VALUE
    }

    /**
     * Rejects a local value whose UTC instant exceeds the supported timestamp range.
     */
    @Test
    fun `reject a local date-time after the Protobuf range`() {
        shouldThrow<ParseException> {
            parseDateTime(
                rawText = "999912312359",
                dateTimePattern = TestDateTimePattern,
                zone = ZoneOffset.ofHours(-2)
            )
        }
    }

    /**
     * Converts ordinary UTC input without changing the represented instant.
     */
    @Test
    fun `parse an ordinary local date-time`() {
        val result = parseDateTime(
            rawText = "202507011230",
            dateTimePattern = TestDateTimePattern,
            zone = ZoneOffset.UTC
        )
        val expected = Timestamp.newBuilder()
            .setSeconds(Instant.parse("2025-07-01T12:30:00Z").epochSecond)
            .build()

        result shouldBe expected
    }

    /**
     * Preserves the current instant through parsing, validation, and change notification,
     * including both occurrences of the repeated hour when daylight-saving time ends.
     */
    @ParameterizedTest
    @ValueSource(strings = [
        "2026-07-01T12:30:45.123456789Z",
        "2026-10-25T00:30:45.123456789Z",
        "2026-10-25T01:30:45.123456789Z"
    ])
    fun `fill the current moment truncated to the field's pattern`(currentTime: String) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val instant = Instant.parse(currentTime)
        val expected = instant.truncatedTo(MINUTES)
            .toTimestamp()
        val validated = mutableListOf<Timestamp>()
        val changed = mutableListOf<Timestamp?>()
        val field = DateTimeField()
            .apply {
                dateTimePattern = TestDateTimePattern
                value = mutableStateOf(null)
                valid = mutableStateOf(true)
                onValidate = {
                    validated.add(it)
                    null
                }
                onChange = { changed.add(it) }
            }

        field.fillNow(instant)

        field.value.value shouldBe expected
        field.valid.value shouldBe true
        validated shouldBe listOf(expected)
        changed shouldBe listOf(expected)
    }

    /**
     * A rejected current moment must remain invalid, and a later use of the same field
     * must use the offset of that moment.
     */
    @Test
    fun `validate the current moment and accept a later retry`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val validated = mutableListOf<Timestamp>()
        val field = DateTimeField()
            .apply {
                dateTimePattern = TestDateTimePattern
                value = mutableStateOf(null)
                valid = mutableStateOf(true)
                onValidate = {
                    validated.add(it)
                    "Choose another time."
                }
            }

        field.fillNow(Instant.parse("2026-10-25T01:30:45Z"))

        field.value.value shouldBe null
        field.valid.value shouldBe false
        validated shouldBe listOf(Timestamps.parse("2026-10-25T01:30:00Z"))

        field.onValidate = null
        field.fillNow(Instant.parse("2026-10-26T01:30:45Z"))

        field.value.value shouldBe Timestamps.parse("2026-10-26T01:30:00Z")
        field.valid.value shouldBe true
    }

    /**
     * A later manual edit must not inherit the overlap occurrence selected by the now action.
     */
    @Test
    fun `interpret ordinary input normally after filling the current moment`() {
        TestApplication.install()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val field = DateTimeField()
            .apply {
                dateTimePattern = TestDateTimePattern
                value = mutableStateOf(null)
                valid = mutableStateOf(true)
            }

        inScene({ field.Content() }) { scene ->
            onUiThread { field.fillNow(Instant.parse("2026-10-25T01:30:45Z")) }
            scene.render()
            field.value.value shouldBe Timestamps.parse("2026-10-25T01:30:00Z")

            scene.enterText("202610250231")

            field.value.value shouldBe Timestamps.parse("2026-10-25T00:31:00Z")
            field.valid.value shouldBe true
        }
    }

    /**
     * A nonexistent spring-forward time advances by the gap in both the value and the editor.
     */
    @Test
    fun `advance local input in the spring daylight-saving gap`() {
        TestApplication.install()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        val field = DateTimeField()
            .apply {
                dateTimePattern = TestDateTimePattern
                value = mutableStateOf(null)
                valid = mutableStateOf(true)
            }

        inScene({ field.Content() }) { scene ->
            scene.enterText("202603290230")

            field.value.value shouldBe Timestamps.parse("2026-03-29T01:30:00Z")
            field.valid.value shouldBe true
            scene.inputText() shouldBe "2026-03-29 03:30"
        }
    }

    /**
     * Retains fractional seconds when the editor pattern exposes them.
     */
    @Test
    fun `preserve sub-second precision when the pattern includes a fraction`() {
        val pattern = "yyyy-MM-dd HH:mm:ss.SSS"
        val instant = Instant.parse("2025-07-01T12:30:45.123Z")

        val text = formatDateTime(
            value = instant.toTimestamp(),
            dateTimePattern = pattern,
            zone = ZoneOffset.UTC
        )
        val stored = parseDateTime(rawText = text, dateTimePattern = pattern, zone = ZoneOffset.UTC)

        stored shouldBe Timestamp.newBuilder()
            .setSeconds(Instant.parse("2025-07-01T12:30:45Z").epochSecond)
            .setNanos(123_000_000)
            .build()
    }
}
