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

package io.spine.chords.proto.time

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.getSelectedText
import com.google.protobuf.Timestamp
import com.google.protobuf.util.Timestamps
import io.spine.chords.core.ComponentCompanion
import io.spine.chords.core.keyboard.KeyRange
import io.spine.chords.core.keyboard.matches
import io.spine.chords.core.InputField
import io.spine.chords.core.InputReviser
import io.spine.chords.core.InputReviser.Companion.DigitsOnly
import io.spine.chords.core.InputReviser.Companion.maxLength
import io.spine.chords.core.RawTextContent
import io.spine.chords.core.ValueParseException
import io.spine.chords.core.time.WallClock
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern
import java.time.format.DateTimeParseException
import java.util.*

private const val DefaultDateTimeFormat = "$DefaultDatePattern HH:mm"

/**
 * Date/time pattern as defined by [DateTimeFormatter].
 */
public typealias DateTimePattern = String

/**
 * A field that allows specifying date and time.
 */
public class DateTimeField : InputField<Timestamp>() {

    /**
     * Instance declaration API.
     */
    public companion object : ComponentCompanion<DateTimeField>({ DateTimeField() })

    /**
     * A pattern for parsing and formatting a date component (as used with
     * [DateTimeFormatter], no spaces are allowed).
     */
    public var dateTimePattern: DateTimePattern by mutableStateOf(DefaultDateTimeFormat)

    init {
        label = "Date/time"
        inputReviser = DateTimeFieldReviser(dateTimePattern)
    }

    override val visualTransformation: VisualTransformation
        @Composable
        @ReadOnlyComposable
        get() {
            val maskTextColor: Color = MaterialTheme.colorScheme.secondary
            return VisualTransformation {
                val transformedString = complementWithPattern(
                    it.text,
                    dateTimePattern
                )
                    .toTransformedString(maskTextColor)
                transformedString
            }
        }

    @Composable
    @ReadOnlyComposable
    override fun beforeComposeContent() {
        super.beforeComposeContent()
        inputReviser = DateTimeFieldReviser(dateTimePattern)
        textStyle = LocalTextStyle.current.copy(fontFamily = Monospace)
    }

    override fun formatValue(value: Timestamp): String {
        val instant = Instant.ofEpochSecond(value.seconds)
        return ofPattern(purifiedPattern(dateTimePattern)).format(
            OffsetDateTime.ofInstant(instant, WallClock.zoneOffset)
        )
    }

    override fun parseValue(rawText: String): Timestamp {
        val offsetDateTime = try {
            LocalDateTime.parse(
                complementWithPattern(rawText, dateTimePattern).string,
                ofPattern(dateTimePattern)
            )
        } catch (e: DateTimeParseException) {
            throw ValueParseException("Enter a valid value", e)
        }
        val millis = Date.from(offsetDateTime.toInstant(WallClock.zoneOffset)).time
        return Timestamps.fromMillis(millis)
    }
}

/**
 * Text representation of some data type in a masked text field.
 *
 * It contains the entire displayed text including the user-entered characters,
 * and any separators defined by the formatting pattern, as well as placeholders
 * for the characters that haven't been filled in yet.
 *
 * Its first part (the length of [enteredSoFar]) is the part that has already
 * been entered by the user (including any symbols that are a part of
 * the pattern), and the remaining characters are the ones that display
 * the outstanding unfilled text pattern.
 *
 * @param string
 *         the entire string that contains the partially or fully
 *         filled pattern.
 * @param enteredSoFar
 *         the number of characters in [string] starting from the beginning,
 *         which are already filled in by the user.
 * @param originalCharOffsetsInPattern
 *         an array, the size of raw string plus one, which serves as a mapping
 *         of cursor positions (offsets) in raw string to the respective cursor
 *         positions in [string]. Each element with an index of `i` in this
 *         array corresponds to the position between characters in a raw string,
 *         which is entered by the user (not actually shown), and the number in
 *         that array is an index of the respective cursor position in [string].
 */
private class MaskedString(
    val string: String,
    val enteredSoFar: Int,
    val originalCharOffsetsInPattern: IntArray
) {
    /**
     * Converts this string into respective [TransformedText], whose unfilled
     * mask part is colored with [maskTextColor].
     *
     * @param maskTextColor
     *         a color for the unfilled part of the input mask.
     */
    fun toTransformedString(maskTextColor: Color): TransformedText {
        val annotatedString = AnnotatedString.Builder(string).apply {
            addStyle(
                SpanStyle(color = maskTextColor, fontFamily = Monospace),
                enteredSoFar,
                string.length
            )
        }.toAnnotatedString()
        return TransformedText(annotatedString, object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return if (offset < originalCharOffsetsInPattern.size) {
                    originalCharOffsetsInPattern[offset]
                } else {
                    originalCharOffsetsInPattern[originalCharOffsetsInPattern.size - 1]
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val idx = originalCharOffsetsInPattern.indexOfFirst { offset <= it }
                return if (idx != -1) {
                    idx
                } else {
                    originalCharOffsetsInPattern.size - 1
                }
            }
        })
    }
}

/**
 * Given a date pattern, converts it to a pattern that only contains the data
 * characters, such as `y`, `M`, `d`.
 *
 * This pattern can be used to format a date into a "raw" date representation.
 *
 * @param dateTimePattern
 *         a date/time pattern for formatting dates presented to the user.
 * @return the same pattern with all separators (non-data characters) removed.
 */
private fun purifiedPattern(dateTimePattern: DateTimePattern): DateTimePattern =
    dateTimePattern.filter { c -> c.isLetter() }

/**
 * Given a string of user-entered characters ([rawStr]) and a date/time
 * pattern ([pattern]), creates a [MaskedString], which contains the specified
 * pattern whose editable places are filled in with the given user-entered
 * characters left to right.
 *
 * Note that this method can accept a partially specified [rawStr] value, so it
 * can contain fewer characters than there are editable places in [pattern].
 * In this case, the returned [MaskedString] retains the formatting symbols
 * (such as `y`, `m`, `d`) in places of missing characters.
 *
 * @param rawStr
 *         a string of characters that the user has typed. They don't include
 *         any separator characters, which are part of the pattern.
 * @param pattern
 *         a date/time pattern that defines the date/time format.
 * @return the respective [MaskedString], which represents the pattern with
 *         partially or fully entered data.
 */
private fun complementWithPattern(rawStr: String, pattern: DateTimePattern): MaskedString {
    val complementedText = StringBuilder()
    val originalCharOffsetsInPattern = IntArray(rawStr.length + 1)
    var currOriginalTextOffset = 0
    var filledInLength = 0

    pattern.forEachIndexed { i: Int, c: Char ->
        var targetChar = c.lowercaseChar()
        if (c.isLetter()) {
            when {
                (currOriginalTextOffset < rawStr.length) -> {
                    originalCharOffsetsInPattern[currOriginalTextOffset] = i
                    filledInLength = i + 1
                    targetChar = rawStr[currOriginalTextOffset++]
                }

                (currOriginalTextOffset == rawStr.length) -> {
                    originalCharOffsetsInPattern[currOriginalTextOffset++] = i
                    filledInLength = i
                }
            }
        }
        complementedText.append(targetChar)
    }

    val lastRowCharIdx = originalCharOffsetsInPattern.size - 1
    if (
        originalCharOffsetsInPattern[lastRowCharIdx] == 0 &&
        rawStr.isNotEmpty()
    ) {
        originalCharOffsetsInPattern[lastRowCharIdx] = pattern.length
    }

    return MaskedString(
        complementedText.toString(),
        filledInLength,
        originalCharOffsetsInPattern
    )
}

/**
 * An [InputReviser] that is used to revise the entered input text and
 * to stop propagation of specific key events for [DateTimeField].
 *
 * @property dateTimePattern
 *         a date/time pattern which is used by [DateTimeField].
 */
internal class DateTimeFieldReviser(
    private val dateTimePattern: DateTimePattern
) : InputReviser {

    override fun reviseRawTextContent(
        currentRawTextContent: RawTextContent,
        rawTextContentCandidate: RawTextContent
    ): RawTextContent {
        var updatedRawTextContentCandidate = rawTextContentCandidate

        if (currentRawTextContent.text != rawTextContentCandidate.text) {
            updatedRawTextContentCandidate =
                if (currentRawTextContent.selection.collapsed) {
                    updateRawTextContentCandidateWhenTextIsNotSelected(
                        currentRawTextContent,
                        rawTextContentCandidate
                    )
                } else {
                    updateRawTextContentCandidateWhenTextIsSelected(
                        currentRawTextContent,
                        rawTextContentCandidate
                    )
                }
        }

        updatedRawTextContentCandidate =
            DigitsOnly.reviseRawTextContent(currentRawTextContent, updatedRawTextContentCandidate)

        return maxLength(dateTimePattern.filter { it.isLetter() }.length).reviseRawTextContent(
            currentRawTextContent,
            updatedRawTextContentCandidate
        )
    }

    override fun filterKeyEvent(keyEvent: KeyEvent): Boolean {
        return keyEvent matches (!KeyRange.Companion.Digit).typed
    }

    /**
     * Updates user's input when entered text replaces the one selected by user.
     *
     * @param currentRawTextContent
     *         a [RawTextContent] that encapsulates current text input value
     *         and cursor position.
     * @param rawTextContentCandidate
     *         a [RawTextContent] that encapsulates updated text input value
     *         and updated cursor position.
     * @return [RawTextContent] which contains updated text input and
     *         updated cursor input position.
     */
    private fun updateRawTextContentCandidateWhenTextIsSelected(
        currentRawTextContent: RawTextContent,
        rawTextContentCandidate: RawTextContent
    ): RawTextContent {
        val selectedText = currentRawTextContent.getSelectedText().text
        val selectionRange = currentRawTextContent.selection

        if (selectedText == currentRawTextContent.text && rawTextContentCandidate.text.isEmpty()) {
            return rawTextContentCandidate.copy()
        }

        val textPartBeforeSelection = currentRawTextContent.text.substring(0, selectionRange.min)
        val remainingTextPart = currentRawTextContent.text.substring(selectionRange.max)

        var updatedSelectionTextPartLength: Int? = null

        val updatedSelectionTextPart =
            if (selectionRange.max < rawTextContentCandidate.selection.max) {
                if (remainingTextPart.isNotEmpty()) {
                    rawTextContentCandidate.text.substring(selectionRange.min, selectionRange.max)
                } else {
                    rawTextContentCandidate.text.substring(selectionRange.min)
                }
            } else {
                val selectionTextPart =
                    rawTextContentCandidate.text.substring(
                        selectionRange.min,
                        rawTextContentCandidate.selection.max
                    )

                if (remainingTextPart.isNotEmpty()) {
                    updatedSelectionTextPartLength = selectionTextPart.length
                    selectionTextPart.padEnd(selectedText.length, '0')
                } else {
                    selectionTextPart
                }
            }

        val updatedRawText = textPartBeforeSelection + updatedSelectionTextPart + remainingTextPart

        if (updatedSelectionTextPartLength == null) {
            updatedSelectionTextPartLength = updatedSelectionTextPart.length
        }

        return rawTextContentCandidate.copy(
            text = updatedRawText,
            selection = TextRange(
                textPartBeforeSelection.length + updatedSelectionTextPartLength
            )
        )
    }

    /**
     * Updates user's input when user doesn't select any input text
     * and just enters new one.
     *
     * @param currentRawTextContent
     *         a [RawTextContent] that encapsulates current text input value
     *         and cursor position.
     * @param rawTextContentCandidate
     *         a [RawTextContent] that encapsulates updated text input value
     *         and updated cursor position.
     * @return [RawTextContent] which contains updated text input and
     *         updated cursor input position.
     */
    private fun updateRawTextContentCandidateWhenTextIsNotSelected(
        currentRawTextContent: RawTextContent,
        rawTextContentCandidate: RawTextContent
    ): RawTextContent {
        val updatedRawTextContentCandidate: RawTextContent
        if (rawTextContentCandidate.selection.start <= currentRawTextContent.selection.start) {
            if (currentRawTextContent.selection.max == currentRawTextContent.text.length &&
                rawTextContentCandidate.selection.max == rawTextContentCandidate.text.length
            ) {
                updatedRawTextContentCandidate = rawTextContentCandidate.copy()
            } else {
                val remainingDateTimePartIndex = if (currentRawTextContent.selection.collapsed &&
                    rawTextContentCandidate.selection.collapsed &&
                    currentRawTextContent.selection.start == rawTextContentCandidate.selection.start
                ) {
                    currentRawTextContent.selection.min + 1
                } else {
                    currentRawTextContent.selection.min
                }
                val updatedRawText =
                    currentRawTextContent.text.substring(0, rawTextContentCandidate.selection.min) +
                            "0" + currentRawTextContent.text.substring(remainingDateTimePartIndex)

                updatedRawTextContentCandidate = rawTextContentCandidate.copy(text = updatedRawText)
            }
        } else {
            updatedRawTextContentCandidate = updateRawTextContent(
                currentRawTextContent,
                rawTextContentCandidate
            )
        }

        return updatedRawTextContentCandidate
    }

    /**
     * Updates user's input so that new input characters are not inserted in
     * current text, but instead they should replace characters in
     * current raw text.
     *
     * @param currentRawTextContent
     *         a [RawTextContent] that encapsulates current text input value
     *         and cursor position.
     * @param rawTextContentCandidate
     *         a [RawTextContent] that encapsulates updated text input value
     *         and updated cursor position.
     * @return [RawTextContent] which contains updated text input and
     *         updated cursor input position.
     */
    private fun updateRawTextContent(
        currentRawTextContent: RawTextContent,
        rawTextContentCandidate: RawTextContent
    ): RawTextContent {
        val textPartBeforeSelection =
            currentRawTextContent.text.substring(0, currentRawTextContent.selection.min)
        val selectionTextPart = rawTextContentCandidate.text.substring(
            currentRawTextContent.selection.min,
            rawTextContentCandidate.selection.max
        )
        val remainingTextPart =
            if (currentRawTextContent.text.length <
                rawTextContentCandidate.selection.max
            ) {
                ""
            } else {
                currentRawTextContent.text.substring(
                    rawTextContentCandidate.selection.max,
                    currentRawTextContent.text.length
                )
            }
        val updatedRawText = textPartBeforeSelection + selectionTextPart + remainingTextPart

        return rawTextContentCandidate.copy(text = updatedRawText)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is DateTimeFieldReviser) {
            return false
        }

        return dateTimePattern == other.dateTimePattern
    }

    override fun hashCode(): Int {
        return dateTimePattern.hashCode()
    }
}
