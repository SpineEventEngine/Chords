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

package io.spine.chords.core.keyboard

import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyDown
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyUp
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import io.spine.chords.core.keyboard.KeyModifiers.Companion.NoModifiers
import java.awt.event.KeyEvent.KEY_TYPED

/**
 * Represents a particular key, key combination, or a set of key combinations
 * that need to be detected among given [KeyEvent]s,
 * including the type of such key events.
 *
 * Keystroke declarations can be matched against `KeyEvent` instances
 * using the [KeyEvent.matches] infix function. Here are some examples:
 * ```kotlin
 * val anyDigitTyped = keyEvent matches Digit.typed
 * val xLetterTyped = keyEvent matches 'x'.key.typed
 * val enterKeyDown = keyEvent matches Enter.key.down
 * val enterKeyUp = keyEvent matches Enter.key.up
 * ```
 *
 * `Keystroke`'s constructor shouldn't be used directly, and [KeyRange]
 *  based expressions should be used instead for obtaining `Keystroke`
 *  instances as shown below.
 *
 * A keystroke expression can either consist of just a [KeyRange] value
 * accompanied by an event type specifier (`typed`/`up`/`down`),
 * like `Whitespace.typed`, `'a'.key.typed`, or `Backspace.key.down`.
 *
 * Or it can also include a [KeyModifiers] value, like
 * `Ctrl(Enter.key).down` or `Ctrl.shift(Backspace.key).down`.
 *
 * It's also possible to use the [KeyRange.or] and [KeyRange.and] infix
 * for obtaining `Keystroke`s with more complex key ranges,
 * for example like this:
 * ```kotlin
 * (Ctrl(Enter.key) or '.'.key).up
 * ```
 *
 * Similarly, the [KeyRange.not] operator can be used if needed like this:
 * ```kotlin
 * (!Digit).typed
 * ```
 *
 * @property keyRange
 *         a [KeyRange] reference for [Keystroke] definition
 *         that includes keyboard keys.
 * @property down
 *         `true` if [Keystroke] matches `KeyDown` type of events,
 *         otherwise `false`.
 * @property typed
 *         `true` if [Keystroke] matches "key typed" events,
 *         otherwise `false`.
 * @property up
 *         `true` if [Keystroke] matches `KeyUp` type of events,
 *         otherwise `false`.
 */
public open class Keystroke internal constructor(
    private val keyRange: KeyRange,
    private val down: Boolean = false,
    private val typed: Boolean = false,
    private val up: Boolean = false
) {

    /**
     * Checks whether the key, modifiers and type that triggered the passed
     * [KeyEvent] corresponds to the one represented by this [Keystroke].
     *
     * @param event
     *         the [KeyEvent] whose key, modifiers and type are being checked.
     *
     * @return `true` if key, modifiers and type all match the one represented
     *         by this [Keystroke], otherwise `false`.
     */
    public fun matches(event: KeyEvent): Boolean {
        return when {
            !keyRange.matches(event) -> false

            down && event.type == KeyDown -> true

            up && event.type == KeyUp -> true

            typed && event.awtEventOrNull?.id == KEY_TYPED -> true

            else -> false
        }
    }
}

/**
 * An infix function to check if a [KeyEvent] corresponds
 * to the given [Keystroke].
 *
 * @param keystroke
 *         which defines keys, modifiers and type to be matched
 *         against given [KeyEvent].
 * @return `true` if key, modifiers and type of [KeyEvent] all match the one
 *         represented by this [Keystroke], otherwise `false`.
 */
public infix fun KeyEvent.matches(keystroke: Keystroke): Boolean {
    return keystroke.matches(this)
}

/**
 * Represents one or more keyboard keys to be used for [Keystroke] definitions.
 *
 * When [KeyRange] represents more than one key, the resulting [Keystroke]
 * created with this [KeyRange] would match an event triggered
 * by pressing any of those keys.
 *
 * 'KeyRange''s constructor shouldn't be used directly, and either of
 * the following ways should be used instead to obtain KeyRange instances:
 * - Any predefined `KeyRange` instance from the `KeyRange`'s companion object
 *   (e.g. `Digit`, or `Whitespace`).
 * - A `KeyRange` instance based on any [Key] value, e.g.
 *   (`Enter.key`, `Backspace.key`, etc.). See [Key.key].
 * - A `KeyRange` instance based on any [Char] value, e.g.
 *   (`'a'.key`, `'b',key`, etc.). See [Char.key].
 *
 * An example of key event expression interested in any digit key press:
 * ```kotlin
 * Digit.down
 * ```
 *
 * @property condition
 *         lambda function that checks whether the key that triggered
 *         the passed [KeyEvent] belongs to any of the keys represented
 *         by this [KeyRange].
 *         If it evaluates to `true`, it indicates that the key that
 *         triggered [KeyEvent] belongs to the given [KeyRange],
 *         otherwise it returns `false`.
 * @property keyModifiers
 *         reference to a [KeyModifiers] if a [Keystroke] definition
 *         includes key modifiers in it. If `null`, it means that checking of
 *         modifiers in [KeyEvent] is not needed, which is the case when
 *         this [KeyRange] represents combination of multiple [KeyRange]s.
 */
public open class KeyRange internal constructor(
    internal val condition: (KeyEvent) -> Boolean,
    private val keyModifiers: KeyModifiers? = null
) {

    /**
     * This property uses this [KeyRange] to create [Keystroke]
     * which matches "key typed" events (see [KEY_TYPED]).
     *
     * @return a new [Keystroke] that matches a key typed event triggered
     *         by pressing any of keys represented by this [KeyRange].
     * @see KEY_TYPED
     * @see KeyEvent.awtEventOrNull
     */
    public val typed: Keystroke
        get() {
            return Keystroke(keyRange = this, typed = true)
        }

    /**
     * This property uses this [KeyRange] to create [Keystroke]
     * which matches `KeyDown` type of events (as defined by [KeyEvent.type]).
     *
     * @return a new [Keystroke] that matches a key down event triggered
     *         by pressing any of keys represented by this [KeyRange].
     */
    public val down: Keystroke
        get() {
            return Keystroke(keyRange = this, down = true)
        }

    /**
     * This property uses this [KeyRange] to create [Keystroke]
     * which matches `KeyUp` type of events (as defined by [KeyEvent.type]).
     *
     * @return a new [Keystroke] that matches a key up event triggered
     *         by pressing any of keys represented by this [KeyRange].
     */
    public val up: Keystroke
        get() {
            return Keystroke(keyRange = this, up = true)
        }

    /**
     * Creates [KeyRange] which checks whether the key that triggered
     * the passed [KeyEvent] belongs to any of the keys represented
     * by this [KeyRange] or any of the keys represented by [other] [KeyRange].
     *
     * @param other
     *         the other [KeyRange] to be used when creating new one.
     * @return new [KeyRange] representing the logical OR combination
     *         of two [KeyRange]s.
     */
    public infix fun or(other: KeyRange): KeyRange {
        return KeyRange({ matches(it) || other.matches(it) })
    }

    /**
     * Creates [KeyRange] which checks whether the key that triggered
     * the passed [KeyEvent] belongs to any of the keys represented
     * by this [KeyRange] and any of the keys represented by [other] [KeyRange].
     *
     * @param other
     *         the other [KeyRange] to be used when creating new one.
     * @return new [KeyRange] representing the logical AND combination
     *         of two [KeyRange]s.
     */
    public infix fun and(other: KeyRange): KeyRange {
        return KeyRange({ matches(it) && other.matches(it) })
    }

    /**
     * Creates [KeyRange] which checks whether the key that triggered
     * the passed [KeyEvent] doesn't belong to any of the keys represented
     * by this [KeyRange].
     *
     * @return new [KeyRange] representing the logical negation
     *         of this [KeyRange].
     */
    public operator fun not(): KeyRange {
        return KeyRange({ event -> !matches(event) }, keyModifiers)
    }

    /**
     * Checks whether the key that triggered the passed [KeyEvent] belongs
     * to any of the keys represented by this [KeyRange].
     *
     * @param event
     *         the [KeyEvent] whose key is being checked.
     *
     * @return `true` if key belongs to any of the keys
     *         represented by this [KeyRange], otherwise `false`.
     */
    public fun matches(event: KeyEvent): Boolean {
        if (keyModifiers != null) {
            val modifierConditionsPassed = keyModifiers.matches(event)

            return modifierConditionsPassed && condition(event)
        }

        return condition(event)
    }

    public companion object {

        /**
         * A [KeyRange] that matches key events from pressing any digit key.
         */
        public object Digit :
            KeyRange({ event -> event.awtEventOrNull?.keyChar?.isDigit() ?: false }, NoModifiers)

        /**
         * A [KeyRange] that matches key events from pressing
         * any whitespace key.
         */
        public object Whitespace :
            KeyRange(
                { event -> event.awtEventOrNull?.keyChar?.isWhitespace() ?: false },
                NoModifiers
            )
    }
}

/**
 * Creates [KeyRange] that matches events from typing a given character.
 *
 * @receiver the character for which the [KeyRange] is being created.
 * @return a [KeyRange] that represents the key event for typing this character.
 */
public val Char.key: KeyRange
    get() = KeyRange({ it.awtEventOrNull?.keyChar == this }, NoModifiers)

/**
 * Creates [KeyRange] that matches events from typing a given [Key].
 *
 * @receiver the [Key] instance for which the [KeyRange] is being created.
 * @return a [KeyRange] that represents the key event for typing this [Key].
 */
public val Key.key: KeyRange
    get() = KeyRange({ it.key == this }, NoModifiers)

/**
 * Key modifier(s) that can optionally be specified when
 * defining a keystroke (see [Keystroke]).
 *
 * It is used for defining keystrokes that are formed
 * with single or multiple key modifiers.
 *
 * An example of key event expression that uses Ctrl and Shift modifiers:
 * ```kotlin
 * Ctrl.shift(Digit).typed
 * ```
 *
 * @property isCtrlPressed
 *         'true' if [KeyModifiers] includes Ctrl modifier, otherwise 'false'.
 * @property isShiftPressed
 *         'true' if [KeyModifiers] includes Shift modifier, otherwise 'false'.
 * @property isAltPressed
 *         'true' if [KeyModifiers] includes Alt modifier, otherwise 'false'.
 * @property isMetaPressed
 *         'true' if [KeyModifiers] includes Meta modifier, otherwise 'false'.
 */
public open class KeyModifiers
private constructor(
    private val isCtrlPressed: Boolean = false,
    private val isShiftPressed: Boolean = false,
    private val isAltPressed: Boolean = false,
    private val isMetaPressed: Boolean = false
) {

    /**
     * Returns a [KeyModifiers] instance with the Ctrl modifier included.
     *
     * This property returns a new [KeyModifiers] instance that is identical
     * to the current instance, except it includes the Ctrl modifier in
     * addition to the existing modifiers.
     */
    public val ctrl: KeyModifiers
        get() {
            return KeyModifiers(true, isShiftPressed, isAltPressed, isMetaPressed)
        }

    /**
     * Returns a [KeyModifiers] instance with the Shift modifier included.
     *
     * This property returns a new [KeyModifiers] instance that is identical
     * to the current instance, except it includes the Shift modifier in
     * addition to the existing modifiers.
     */
    public val shift: KeyModifiers
        get() {
            return KeyModifiers(isCtrlPressed, true, isAltPressed, isMetaPressed)
        }

    /**
     * Returns a [KeyModifiers] instance with the Alt modifier included.
     *
     * This property returns a new [KeyModifiers] instance that is identical
     * to the current instance, except it includes the Alt modifier in
     * addition to the existing modifiers.
     */
    public val alt: KeyModifiers
        get() {
            return KeyModifiers(isCtrlPressed, isShiftPressed, true, isMetaPressed)
        }

    /**
     * Returns a [KeyModifiers] instance with the Meta modifier included.
     *
     * This property returns a new [KeyModifiers] instance that is identical
     * to the current instance, except it includes the Meta modifier in
     * addition to the existing modifiers.
     */
    public val meta: KeyModifiers
        get() {
            return KeyModifiers(isCtrlPressed, isShiftPressed, isAltPressed, true)
        }

    /**
     * Attaches the modifier(s) to the given [KeyRange].
     *
     * For example, `Ctrl.alt(Enter.key)` returns a [KeyRange] instance
     * that corresponds to the Ctrl+Alt+Enter key combination.
     *
     * @param keyRange
     *         the [KeyRange] to which the modifier(s) will be attached.
     *
     * @return the value of [keyRange] that was modified to include
     *         key modifiers represented by this [KeyModifiers] instance.
     */
    public operator fun invoke(keyRange: KeyRange): KeyRange {
        return KeyRange(keyRange.condition, this)
    }

    /**
     * Checks whether the modifiers in [KeyEvent] correspond to the
     * modifiers represented by this [KeyModifiers].
     *
     * @param event
     *         the [KeyEvent] whose modifiers are being checked.
     *
     * @return `true` if all keystroke modifiers correspond to exactly
     *         all modifiers represented by [KeyModifiers], otherwise `false`.
     */
    public fun matches(event: KeyEvent): Boolean {
        return event.isCtrlPressed == isCtrlPressed &&
                event.isAltPressed == isAltPressed &&
                event.isShiftPressed == isShiftPressed &&
                event.isMetaPressed == isMetaPressed
    }

    public companion object {

        /**
         * A [KeyModifiers] value that represents a Ctrl modifier.
         */
        public object Ctrl : KeyModifiers(isCtrlPressed = true)

        /**
         * A [KeyModifiers] value that represents a Shift modifier.
         */
        public object Shift : KeyModifiers(isShiftPressed = true)

        /**
         * A [KeyModifiers] value that represents an Alt modifier.
         */
        public object Alt : KeyModifiers(isAltPressed = true)

        /**
         * A [KeyModifiers] value that represents a Meta modifier.
         */
        public object Meta : KeyModifiers(isMetaPressed = true)

        /**
         * A [KeyModifiers] value that doesn't include any modifiers
         * (matches only those KeyEvents that don't have any modifiers).
         *
         * It shouldn't be used in any application code, and is intended only
         * for an internal usage inside the [KeyRange] class.
         *
         * If you need to create a `Keystroke` instance that matches key events
         * without any modifiers, just don't include any modifiers in the
         * respective `Keystroke` expression. For example, `Enter.key.down` will
         * match only presses of the Enter key when they are made
         * without any modifiers.
         */
        internal object NoModifiers : KeyModifiers()
    }
}

/**
 * Attaches an [onKeyEvent] handler that invokes [handler] whenever [KeyEvent]
 * matches the given [keystroke].
 *
 * @receiver a [Modifier] where key event handler needs to be attached.
 * @param keystroke
 *         a keystroke that needs to be recognized.
 * @param preview
 *         if `true`, makes the respective key events to be observed on
 *         the preview stage (using [onPreviewKeyEvent]
 *         instead of [onKeyEvent]).
 * @param handler
 *         a handler that should be invoked whenever the given [keystroke]
 *         is recognized. The handler function can invoke
 *         [stopPropagation][KeystrokeHandlerScope.stopPropagation], then
 *         further event's propagation is stopped.
 * @return the same [Modifier] instance on which this function was invoked.
 * @see Modifier.onKeyEvent
 */
public fun Modifier.on(
    keystroke: Keystroke,
    preview: Boolean = false,
    handler: KeystrokeHandlerScope.(KeyEvent) -> Unit
): Modifier {
    val recognizeKeystrokes: (KeyEvent) -> Boolean = {
        if (it matches keystroke) {
            var stopEventPropagation = false
            val scope = object : KeystrokeHandlerScope {
                override fun stopPropagation() {
                    stopEventPropagation = true
                }
            }
            scope.handler(it)
            stopEventPropagation
        } else {
            false
        }
    }
    return if (preview) {
        onPreviewKeyEvent(recognizeKeystrokes)
    } else {
        onKeyEvent(recognizeKeystrokes)
    }
}

/**
 * A scope that provides function(s) useful in context of keystroke
 * handler implementations created using the [on] extension.
 */
public interface KeystrokeHandlerScope {

    /**
     * Makes further event propagation to be stopped.
     *
     * Under the hood, invoking this function instructs the respective
     * [onKeyEvent] handler to return `true`. If this function is not invoked,
     * the event handler will return `false` (which means further event
     * propagation is not stopped).
     */
    public fun stopPropagation()
}
