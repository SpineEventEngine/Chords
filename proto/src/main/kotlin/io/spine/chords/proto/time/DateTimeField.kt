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

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType.Companion.Enter
import androidx.compose.ui.input.pointer.PointerEventType.Companion.Exit
import androidx.compose.ui.input.pointer.PointerEventType.Companion.Press
import androidx.compose.ui.input.pointer.PointerEventType.Companion.Release
import androidx.compose.ui.input.pointer.PointerIcon.Companion.Hand
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.unit.dp
import com.google.protobuf.Timestamp
import com.google.protobuf.util.Timestamps
import io.spine.chords.core.ComponentSetup
import io.spine.chords.core.keyboard.KeyModifiers.Companion.Ctrl
import io.spine.chords.core.keyboard.KeyRange
import io.spine.chords.core.keyboard.key
import io.spine.chords.core.keyboard.matches
import io.spine.chords.core.InputField
import io.spine.chords.core.InputReviser
import io.spine.chords.core.InputReviser.Companion.DigitsOnly
import io.spine.chords.core.InputReviser.Companion.maxLength
import io.spine.chords.core.RawTextContent
import io.spine.chords.core.ParseException
import io.spine.chords.core.layout.WithTooltip
import io.spine.chords.core.styling.ChordsInteraction
import io.spine.chords.core.styling.ChordsTheme
import io.spine.chords.core.time.WallClock
import io.spine.chords.proto.value.time.DefaultDatePattern
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern
import java.time.format.DateTimeParseException

private const val DefaultDateTimeFormat = "$DefaultDatePattern HH:mm"

/**
 * The text used both as the accessibility description and the tooltip of the
 * [DateTimeField]'s default "now" button.
 */
private const val NowButtonDescription = "Set to the current date and time"

/**
 * The overall size of the "now" button (its round state layer).
 *
 * It matches the height of a single text line, so that the button doesn't
 * enlarge the field.
 */
private val NowButtonSize = 24.dp

/**
 * The size of the "now" button's icon, kept smaller than [NowButtonSize] so that
 * the round state layer remains visible around it.
 */
private val NowButtonIconSize = 20.dp

/**
 * Date/time pattern as defined by [DateTimeFormatter].
 */
public typealias DateTimePattern = String

/**
 * A field that allows specifying date and time.
 *
 * ### The "now" option
 *
 * When [nowOptionEnabled] is set to `true`, the field offers an optional
 * affordance for filling it with the current date and time, so that the user
 * doesn't have to type it by hand when recording something at the moment it
 * happens. The current moment is obtained through
 * [WallClock][io.spine.chords.core.time.WallClock].
 *
 * Since the field always keeps its value consistent with the text it displays,
 * the filled value is truncated to the resolution of [dateTimePattern] — for
 * example, to the minute with the default pattern, which has no seconds field.
 *
 * The affordance is available both as a button displayed within the field
 * (while it is focused or already contains a value), and as the Ctrl+N keyboard
 * shortcut while the field is focused. The button's appearance can be customized
 * via [nowOptionAffordance].
 */
public class DateTimeField : InputField<Timestamp>() {
    public companion object : ComponentSetup<DateTimeField>()

    /**
     * A pattern for parsing and formatting a date component (as used with
     * [DateTimeFormatter], no spaces are allowed).
     */
    public var dateTimePattern: DateTimePattern by mutableStateOf(DefaultDateTimeFormat)

    /**
     * Enables an optional "now" affordance that fills the field with the current
     * date and time (see the class documentation).
     *
     * When set to `true`, the field displays a button (while it is focused or
     * already contains a value) that fills it with the current date and time
     * (truncated to the resolution of [dateTimePattern]), and the same can be
     * done with the Ctrl+N keyboard shortcut while the field is focused.
     *
     * It is `false` by default, so that the affordance appears only where
     * a "current moment" value makes sense.
     *
     * @see nowOptionAffordance
     */
    public var nowOptionEnabled: Boolean by mutableStateOf(false)

    /**
     * An optional custom renderer for the "now" affordance, used instead of the
     * default button when [nowOptionEnabled] is `true`.
     *
     * The provided composable receives a `fillNow` callback, which sets the
     * field to the current date and time; it should be invoked when the user
     * activates the custom affordance.
     *
     * When this property is `null` (by default), a default button is displayed.
     *
     * @see nowOptionEnabled
     */
    public var nowOptionAffordance: (@Composable (fillNow: () -> Unit) -> Unit)?
            by mutableStateOf(null)

    init {
        label = "Date/time"
    }

    @Composable
    @ReadOnlyComposable
    override fun beforeComposeContent() {
        super.beforeComposeContent()
        inputReviser = DateTimeFieldReviser(dateTimePattern)
        val secondaryColor = colorScheme.secondary
        visualTransformation = VisualTransformation {
            complementWithPattern(
                it.text, dateTimePattern
            ).toTransformedString(secondaryColor)
        }
        suffix = if (nowOptionEnabled) {
            {
                val customAffordance = nowOptionAffordance
                if (customAffordance != null) {
                    customAffordance(::fillNow)
                } else {
                    NowButton(enabled = enabled, onClick = ::fillNow)
                }
            }
        } else {
            null
        }
    }

    /**
     * Uses a monospaced font for aligned date and time input by default.
     */
    @Composable
    @ReadOnlyComposable
    override fun defaultTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = Monospace
    )

    override fun handleKeyEvent(keyEvent: KeyEvent): Boolean {
        if (nowOptionEnabled && enabled && keyEvent matches Ctrl(Key.N.key).down) {
            fillNow()
            return true
        }
        return false
    }

    /**
     * Fills the field with the current date and time, obtained through
     * [WallClock].
     *
     * The stored value is truncated to the resolution of [dateTimePattern], so
     * that it stays consistent with the text displayed in the field.
     */
    private fun fillNow() {
        if (enabled) {
            applyValue(WallClock.now.toTimestamp())
        }
    }

    override fun formatValue(value: Timestamp): String =
        formatDateTime(value, dateTimePattern, WallClock.zoneOffset)

    override fun parseValue(rawText: String): Timestamp =
        parseDateTime(rawText, dateTimePattern, WallClock.zoneOffset)
}

/**
 * The default button of the [DateTimeField]'s "now" affordance.
 *
 * It is a compact clickable icon (rather than a full-sized
 * [androidx.compose.material3.IconButton]), so that it doesn't increase the
 * height of the field. A round state layer behind the icon is lightened on hover
 * or focus and darkened while pressed, to give the usual visual feedback.
 *
 * The control exposes button semantics (a button role, an accessible click
 * action, a disabled state, keyboard focusability, and Enter/Space activation),
 * so that it is discoverable and operable via assistive technologies and
 * keyboard traversal.
 *
 * Mouse activation is handled with low-level pointer events (a primary-button
 * press on the icon followed by a release over it; a non-primary button or a
 * press dragged away before release does not activate it). This is used instead
 * of [androidx.compose.foundation.clickable] because the latter's gesture is
 * intermittently cancelled when placed inside the text field's decoration, where
 * the field competes for the same pointer events.
 *
 * @param enabled
 *         whether the button is enabled for interaction.
 * @param onClick
 *         a callback invoked when the button is clicked.
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun NowButton(enabled: Boolean, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val stateLayerColor = nowButtonStateLayerColor(
        enabled = enabled,
        pressed = pressed,
        active = hovered || focused,
        baseColor = colorScheme.onSurface,
        interaction = ChordsTheme.interaction
    )
    WithTooltip(tooltip = NowButtonDescription) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(NowButtonSize)
                .clip(CircleShape)
                .background(stateLayerColor)
                .nowButtonSemantics(enabled, onClick)
                .onFocusChanged { focused = it.isFocused }
                .onActivationKeys(enabled, onClick)
                .focusable(enabled)
                .pointerHoverIcon(Hand)
                .nowButtonPointerInput(
                    enabled,
                    setHovered = { hovered = it },
                    isPressed = { pressed },
                    setPressed = { pressed = it },
                    onClick = onClick
                )
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.size(NowButtonIconSize)
            )
        }
    }
}

/**
 * The color of the "now" button's state layer for the given interaction state.
 */
private fun nowButtonStateLayerColor(
    enabled: Boolean,
    pressed: Boolean,
    active: Boolean,
    baseColor: Color,
    interaction: ChordsInteraction
): Color = when {
    !enabled -> Color.Transparent
    pressed -> baseColor.copy(alpha = interaction.pressedStateAlpha)
    active -> baseColor.copy(alpha = interaction.hoveredStateAlpha)
    else -> Color.Transparent
}

/**
 * Adds button semantics to the "now" button, so that it is exposed to assistive
 * technologies as an actionable, optionally disabled, button.
 */
private fun Modifier.nowButtonSemantics(
    enabled: Boolean,
    onClick: () -> Unit
): Modifier = semantics(mergeDescendants = true) {
    role = Role.Button
    contentDescription = NowButtonDescription
    if (!enabled) {
        disabled()
    }
    onClick(label = NowButtonDescription) {
        if (enabled) {
            onClick()
        }
        enabled
    }
}

/**
 * Activates the "now" button with the Enter or Space key while it is focused.
 */
private fun Modifier.onActivationKeys(
    enabled: Boolean,
    onActivate: () -> Unit
): Modifier = onKeyEvent { event ->
    if (enabled &&
        (event matches Key.Enter.key.down || event matches Key.Spacebar.key.down)
    ) {
        onActivate()
        true
    } else {
        false
    }
}

/**
 * Handles mouse interaction with the "now" button, invoking [onClick] on a
 * primary-button press on the button followed by a release over it, and
 * reporting the hover and press states via [setHovered] and [setPressed].
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.nowButtonPointerInput(
    enabled: Boolean,
    setHovered: (Boolean) -> Unit,
    isPressed: () -> Boolean,
    setPressed: (Boolean) -> Unit,
    onClick: () -> Unit
): Modifier =
    onPointerEvent(Enter) { setHovered(true) }
        .onPointerEvent(Exit) {
            setHovered(false)
            setPressed(false)
        }
        .onPointerEvent(Press) { event ->
            setPressed(enabled && event.buttons.isPrimaryPressed)
        }
        .onPointerEvent(Release) {
            if (isPressed()) {
                setPressed(false)
                onClick()
            }
        }

/**
 * Converts this [Instant] into a Protobuf [Timestamp].
 */
internal fun Instant.toTimestamp(): Timestamp =
    Timestamp.newBuilder()
        .setSeconds(epochSecond)
        .setNanos(nano)
        .build()

/**
 * Formats the given [value] into the input field's raw text (the editable
 * characters only) according to [dateTimePattern].
 *
 * Only the components present in [dateTimePattern] are emitted, so any finer
 * resolution of [value] (e.g. seconds or nanoseconds when the pattern has
 * minute resolution) is not represented in the resulting text. This is the
 * inverse of [parseDateTime].
 *
 * @param value
 *         the timestamp to format.
 * @param dateTimePattern
 *         the pattern used to format the date/time.
 * @param zoneOffset
 *         the offset used to convert the instant into a local date/time.
 * @return the raw text representation of [value].
 */
internal fun formatDateTime(
    value: Timestamp,
    dateTimePattern: DateTimePattern,
    zoneOffset: ZoneOffset
): String {
    val instant = Instant.ofEpochSecond(value.seconds, value.nanos.toLong())
    return ofPattern(purifiedPattern(dateTimePattern)).format(
        OffsetDateTime.ofInstant(instant, zoneOffset)
    )
}

/**
 * Parses date and time from raw input field text into a Protobuf [Timestamp].
 *
 * The [rawText] contains only editable characters of the date/time value. The
 * separators specified by [dateTimePattern] are restored before parsing. The
 * resulting local date/time is interpreted at [zoneOffset].
 *
 * @param rawText
 *         the editable characters entered into the input field.
 * @param dateTimePattern
 *         the pattern used to restore separators and parse the date/time.
 * @param zoneOffset
 *         the offset used to convert the local date/time into an instant.
 * @return the parsed date/time represented as a Protobuf timestamp.
 * @throws ParseException
 *         if the text cannot be parsed or the resulting instant is outside
 *         the range supported by Protobuf timestamps.
 */
internal fun parseDateTime(
    rawText: String,
    dateTimePattern: DateTimePattern,
    zoneOffset: ZoneOffset
): Timestamp {
    val localDateTime = try {
        LocalDateTime.parse(
            complementWithPattern(rawText, dateTimePattern).string,
            ofPattern(dateTimePattern)
        )
    } catch (e: DateTimeParseException) {
        throw ParseException("Enter a valid value", e)
    }
    val instant = localDateTime.toInstant(zoneOffset)
    if (!Timestamps.isValid(instant.epochSecond, instant.nano)) {
        throw ParseException("Enter a date/time within the supported range")
    }
    return Timestamp.newBuilder()
        .setSeconds(instant.epochSecond)
        .setNanos(instant.nano)
        .build()
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
 *         a date/time pattern that is used by [DateTimeField].
 */
internal class DateTimeFieldReviser(
    private val dateTimePattern: DateTimePattern
) : InputReviser {

    override fun reviseRawTextContent(
        current: RawTextContent,
        candidate: RawTextContent
    ): RawTextContent {
        var updatedCandidate = candidate

        if (current.text != candidate.text) {
            updatedCandidate =
                if (current.selection.collapsed) {
                    updateCandidateWhenTextIsNotSelected(current, candidate)
                } else {
                    updateCandidateWhenTextIsSelected(current, candidate)
                }
        }

        updatedCandidate = DigitsOnly.reviseRawTextContent(current, updatedCandidate)

        val rawPatternLength = dateTimePattern.filter { it.isLetter() }.length
        return maxLength(rawPatternLength).reviseRawTextContent(current, updatedCandidate)
    }

    override fun filterKeyEvent(keyEvent: KeyEvent): Boolean {
        return keyEvent matches (!KeyRange.Companion.Digit).typed
    }

    /**
     * Updates user's input when entered text replaces the one selected by user.
     *
     * @param currentRawTextContent A [RawTextContent] that encapsulates current
     *   text input value and cursor position.
     * @param rawTextContentCandidate A [RawTextContent] that encapsulates
     *   updated text input value and updated cursor position.
     * @return [RawTextContent] that contains updated text input and
     *   updated cursor input position.
     */
    private fun updateCandidateWhenTextIsSelected(
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
     * @param currentRawTextContent A [RawTextContent] that encapsulates current
     *   text input value and cursor position.
     * @param rawTextContentCandidate A [RawTextContent] that encapsulates
     *   updated text input value and updated cursor position.
     * @return [RawTextContent] that contains updated text input and
     *   updated cursor input position.
     */
    private fun updateCandidateWhenTextIsNotSelected(
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
     * @return [RawTextContent] that contains updated text input and
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
