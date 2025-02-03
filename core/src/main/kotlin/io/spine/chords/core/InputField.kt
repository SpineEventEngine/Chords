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

package io.spine.chords.core

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.VisualTransformation.Companion.None
import io.spine.chords.core.keyboard.KeyRange.Companion.Digit
import io.spine.chords.core.keyboard.KeyRange.Companion.Whitespace
import io.spine.chords.core.keyboard.matches
import io.spine.chords.core.primitive.preventWidthAutogrowing
import kotlin.math.min
import kotlin.reflect.KClass

/**
 * The text editing state, which includes both the text that is being edited
 * itself, and the cursor/selection related information (see [TextFieldValue]).
 */
public typealias RawTextContent = TextFieldValue

/**
 * A generic text field that allows entering a value of some data type in
 * text form.
 *
 * It accepts a value of type [V] in a [MutableState] via the [value] property.
 * The value stored therein is converted to text form to be displayed in
 * the field using the [formatValue] function, and the text entered or edited by
 * the user is in turn parsed with the [parseValue] function to obtain
 * the resulting value of type [V]. If a text that specifies a valid
 * value format is entered (if [parseValue] is able to successfully parse
 * the user-entered text), then the parsed value of type [V] is written back
 * into [value].
 *
 * If the text field's input string doesn't have a valid format (a [parseValue]
 * function throws [ValueParseException]), then the text field will be displayed
 * as having an error state and `null` will be stored in [value] until a valid
 * value is entered.
 *
 * Note that [parseValue] is not invoked if the current raw text has a length
 * of zero. In this case, the field is considered to be empty, and
 * the [MutableState] in [value] gets a value of `null` (as if this function
 * implicitly returned `null`).
 *
 * A [parseValue] and [formatValue] of `null` means that no custom parsing and
 * formatting takes place, and raw text will reflect whatever is stored in
 * [value] (and vice versa). [parseValue] and [formatValue] are  allowed to be
 * `null` only when [V] is `String`.
 *
 * ### Advanced formatting
 *
 * There are some advanced configuration options that allow to configure how
 * the text entered by the user is processed, parsed/formatted, and displayed.
 * This concerns the optional [inputReviser] and [visualTransformation]
 * properties and their relationship with what is called a raw text (see below).
 *
 * #### Raw text
 *
 * Raw text is an internal text representation of a value edited in the input
 * field. By default, raw text is what the user enters and what is displayed in
 * the field.
 *
 * Raw text is initialized with what is returned by [formatValue], and it is
 * what is passed to [parseValue] to obtain the resulting value of type [V]
 * (which is saved into the [value] property).
 *
 * #### Revising the entered text
 *
 * By default, raw text is directly edited by the user and whatever changes
 * the user makes (by typing, deleting, or pasting) are applied to raw text
 * of the input field. You can optionally change this by specifying
 * [inputReviser], whose
 * [reviseRawTextContent][InputReviser.reviseRawTextContent] method
 * will be invoked every time the user tries to edit raw text, and will have
 * a chance to modify what was typed and is going to be written
 * as a new raw text as a result of the user's entry.
 *
 * It is expected that [inputReviser] can remove or modify some of
 * the characters, but not add new ones. This can for example be used to stop
 * propagation of specific key events or transform the entered text
 * to upper case, if needed, etc.
 *
 * Note that when [inputReviser] is specified, any modifications that it
 * applies (returns from its
 * [reviseRawTextContent][InputReviser.reviseRawTextContent] method)
 * would be used as the input field's raw text. This means
 * that such modified raw text values would be supplied to the [parseValue]
 * function, and displayed within the field in the form that was received
 * from [inputReviser].
 *
 * #### Changing the way how raw text is displayed
 *
 * It is also possible to change the way how the raw text is actually presented
 * to the user by specifying the [visualTransformation] function. This works in
 * the same way as the `visualTransformation` parameter of the [TextField]
 * component.
 *
 * Unlike [inputReviser], any modifications applied using
 * [visualTransformation] only affect how raw text is presented to the user, but
 * doesn't actually change the value of raw text, and user's edits still go into
 * raw text.
 *
 * Some examples of applying a visual transformation would be
 * "masking" the typed characters for a password field, or inserting thousands
 * separator characters for a number editor field (see
 * the [VisualTransformation]'s documentation).
 *
 * ### Input validation and field value
 *
 * The value in the [value] [MutableState] is set to a non-`null` value of type
 * [V] whenever the currently edited field's text (raw text) can successfully be
 * parsed with the [parseValue] function. If [parseValue] fails to parse the
 * current field's raw text (and throws [ValueParseException]), then the value
 * in [value] [MutableState] is set to `null`.
 *
 * When current text's parsing fails (with [parseValue] throwing
 * [ValueParseException]), the validation error
 * [message][ValueParseException.validationErrorMessage] from the respective
 * [ValueParseException] is displayed near the field, and [valueValid]'s state
 * is set to `false`.
 *
 * If raw text is valid according to [parseValue] (no exception is thrown),
 * the value in [valueValid] is `true`.
 *
 * #### Handling empty input
 *
 * [InputField] by itself doesn't require a value to be entered, and allows
 * specifying an empty value. When a field is empty (it has a raw text being an
 * empty string), the field's value is considered to be valid, and results in
 * `null` being stored in [value]'s [MutableState].
 *
 * Note that the [parseValue] function is invoked only if the field is
 * not empty.
 *
 * If the field needs to be enforced to be a non-empty one, then this can be
 * done using an external validation (see the section above), for example,
 * using the [MessageForm][io.spine.chords.proto.form.MessageForm] component.
 *
 * #### A placeholder and prompt text
 *
 * Just like the standard `TextField` component, it is possible to specify
 * a content that will be displayed in the focused field when nothing is entered
 * in it (see the [placeholder] property). It can be specified as an arbitrary
 * composable content.
 *
 * Besides the placeholder, there's also an analogous feature that simplifies
 * a common scenario when a placeholder needs to display a prompt text (which is
 * typically expected to assist the user with understanding what or how should
 * be entered in the field, and is styled respectively). This is possible using
 * the [promptText] property, which is specified as a string value.
 *
 * Note that if [visualTransformation] replaces an empty input with some text
 * (e.g. an input mask), then neither `placeholder`, nor `promptText`
 * is displayed.
 *
 * When both `placeholder` and `promptText` properties are specified, then only
 * the `placeholder` is used.
 *
 * @param V
 *         a type of values that an input field allows to edit.
 * @constructor a constructor, which is used internally by the input component's
 *         implementation. Use [companion object's][ComponentCompanion]
 *         [invoke][ComponentCompanion.invoke] operators for instantiating
 *         and rendering any specific input component in any application code.
 */
@Stable
public open class InputField<V> : InputComponent<V>() {

    /**
     * A [MutableState] that holds the value to be displayed, and receives
     * the updated values of type [V] as they are entered in the field, and was
     * validated by [parseValue] to be valid.
     *
     * A value of `null` corresponds to the empty input (when raw text is
     * an empty string), or an invalid input (when an input cannot be parsed as
     * a valid value by [parseValue]). A value of [valueValid] describes whether
     * the field has a valid value.
     */
    override var value: MutableState<V?>
        get() = super.value
        set(value) {
            super.value = value
        }

    /**
     * A [MutableState], whose value is maintained by this component depending
     * on whether the component's current entry is valid or not.
     *
     * If an invalid value is entered ([parseValue] throws
     * [ValueParseException]), then the value in this [MutableState] is set
     * to `false`. Otherwise, it's set to `true`.
     */
    override var valueValid: MutableState<Boolean>
        get() = super.valueValid
        set(value) {
            super.valueValid = value
        }

    /**
     * A callback, which is triggered after the value in the [MutableState] in
     * [value] has been updated with a newly edited value.
     */
        public var onValueChange: ((V?) -> Unit)? = null

    /**
     * A label for the component.
     */
    public var label: String? by mutableStateOf(null)

    /**
     * A Text displayed as an entry prompt when an empty field is focused.
     *
     * Mutually exclusive with [placeholder].
     */
    public var promptText: String? by mutableStateOf(null)

    /**
     * A text field's placeholder that is displayed when an empty field
     * is focused.
     *
     * Mutually exclusive with [promptText].
     */
    public var placeholder: @Composable (() -> Unit)? by mutableStateOf(null)

    /**
     * A [Modifier] to be applied to the component.
     */
    public var modifier: Modifier by mutableStateOf(Modifier)

    /**
     * A style for text field's text.
     */
    public var textStyle: TextStyle? by mutableStateOf(null)

    /**
     * An [InputReviser] that can be specified to modify the user's input before
     * it is applied to the input field.
     */
    protected var inputReviser: InputReviser? by mutableStateOf(null)

    /**
     * Transforms the raw text typed in by the user to form a text displayed in
     * the field (without affecting how the text is stored in raw text, or
     * parsed and formatted with).
     */
    protected open val visualTransformation: VisualTransformation
        @Composable
        @ReadOnlyComposable
        get() = None

    /**
     * An intermediate text field's text, which is a text that cannot be
     * recognized as having a valid format, but is still displayed in the field
     * to let the user complete or correct it to a valid form.
     *
     * If the text field has a valid format, then this property is `null`.
     */
    private var intermediateText by mutableStateOf<String?>(null)

    private val ownValidationMessage = mutableStateOf<String?>(null)

    /**
     * Represents the selection range of text (start and end indices)
     * within the text field.
     *
     * If the start and end positions are the same, this indicates the position
     * of the input cursor within the text field.
     */
    private var selection by mutableStateOf(TextRange(0))

    /**
     * An optional prefix to be displayed before the input text in
     * the text field.
     */
    protected open var prefix: (@Composable () -> Unit)? = null

    /**
     * An optional suffix to be displayed after the input text in
     * the text field.
     */
    protected open var suffix: (@Composable () -> Unit)? = null

    /**
     * A composable, which defines how the field's supporting text
     * is to be displayed.
     */
    protected open var supportingText: (@Composable (String) -> Unit) = { Text(it) }

    /**
     * A function that should validate the given input field's raw text, and, if
     * it has a valid format for parsing a value of type [V], and satisfies all
     * constraints that might have been defined for the target value, then it
     * should return the respective value of type [V] that corresponds to
     * that text.
     *
     * ### Handling parsing failures
     *
     * If the text doesn't have a valid format, or doesn't satisfy any of the
     * constraints that are defined for the edited value, then the function
     * should throw [ValueParseException].
     *
     * More specifically, besides just parsing the raw text and thus ensuring
     * that it can be interpreted as a value of type [V] (for example a date
     * string having some specific format), this method is also allowed to
     * perform additional validation for the parsed value to ensure that it
     * satisfies any of the constraints that are relevant for the edited value.
     *
     * For example, in case of parsing a date value, the successfully parsed
     * date value can be checked to be in a certain range (e.g. if it has to be
     * a date in the past).
     *
     * Upon an inability to either parse a string representation of [V] (raw
     * text), or upon successfully parsing a string value, but then detecting
     * that it doesn't match some respective constraints, this method should
     * throw [ValueParseException] whose `validationErrorMessage` property can
     * be set with a human-readable message that specifies the reason of
     * the failure, which will be displayed near the field.
     *
     * ### Helpers for typical implementations
     *
     * Note, there are some helper functions, which correspond to some typical
     * parsing scenarios, and they can be helpful to simplify implementing
     * respective [parseValue] for these cases, like this:
     *
     * - [exceptionBasedParser] can be used when the actual value parser being
     *   used throws its own specific exception to signify the parsing failure.
     *   ```
     *      override fun parseValue(rawText: String): Url =
     *         exceptionBasedParser(IllegalArgumentException::class, "Enter a valid URL value") {
     *             Url::class.parse(rawText)
     *         }
     *   ```
     *
     * @param rawText
     *         an input field's raw text that needs to be parsed for creating
     *         a value of type [V].
     * @return a valid value of type [V] that is the result of
     *         parsing [rawText].
     * @throws ValueParseException if [rawText] cannot be parsed as
     *         a valid value.
     * @see exceptionBasedParser
     */
    @Throws(ValueParseException::class)
    @Suppress("UNCHECKED_CAST") // implying editing a string by default
    protected open fun parseValue(rawText: String): V = rawText as V

    /**
     * A function that returns the text representation that should be displayed
     * in the text field for the given value of type [V].
     */
    protected open fun formatValue(value: V): String = value as String

    @Composable
    override fun content(): Unit = recompositionWorkaround {
        val textStyle = textStyle ?: LocalTextStyle.current
        val rawTextContent = getRawTextContent()

        val interactionSource = remember { MutableInteractionSource() }
        val focused = interactionSource.collectIsFocusedAsState()

        val validationErrorText = ownValidationMessage.value ?: externalValidationMessage?.value

        TextField(
            singleLine = true,
            value = rawTextContent,
            label = label?.let { { Text(text = it) } },
            isError = validationErrorText != null,
            supportingText = (validationErrorText ?: "").let {
                {
                    supportingText(it)
                }
            },
            onValueChange = {
                handleChange(
                    rawTextContent,
                    it,
                    inputReviser,
                    value,
                    onValueChange
                )
            },
            visualTransformation = inputTransformation(visualTransformation, focused.value),
            placeholder = placeholder ?: {
                Text(
                    promptText ?: "",
                    fontFamily = textStyle.fontFamily,
                    color = MaterialTheme.colorScheme.secondary
                )
            },
            prefix = prefix,
            suffix = suffix,
            interactionSource = interactionSource,
            enabled = enabled,
            textStyle = textStyle,
            modifier = modifier(modifier)
                .focusRequester(focusRequester)
                .preventWidthAutogrowing()
                .onPreviewKeyEvent { inputReviser?.filterKeyEvent(it) == true }
        )
    }

    override fun clear() {
        super.clear()
        intermediateText = null
        selection = TextRange(0)
        ownValidationMessage.value = null
        onDirtyStateChange?.invoke(false)
    }

    /**
     * Given a modifier, which is going to be applied to the displayed
     * [TextField], provides an opportunity to modify it according to any
     * requirements of the respective input field implementation.
     *
     * @param modifier
     *         a [Modifier], which is going to be set to the [TextField]
     *         displayed by this `InputField`.
     * @return a modified version of a given [modifier] if any modifications are
     *         required.
     */
    protected open fun modifier(modifier: Modifier): Modifier = modifier

    /**
     * Handles the component's internal text field's `onValueChange` event,
     * which basically means handling a user's *attempt* to change the text
     * (type or remove some characters, paste, etc.).
     *
     * If the incoming text has a valid format and thus can be recognized as
     * a respective valid value of type [V], then this method updates the value
     * in [valueMutableState] accordingly. Otherwise, if it is not valid yet, it
     * just updates [intermediateText] in order to accept the newly edited text
     * as a temporary incomplete text representation of some value. An empty
     * string is translated as a `null` value being
     * saved in [valueMutableState].
     *
     * @param currentRawTextContent
     *         a [RawTextContent] that represents the current content
     *         that text field has, before user has modified it.
     * @param newRawTextContentCandidate
     *         the modified input field's raw text content that includes
     *         the current modification that the user is trying to make,
     *         but before it has been passed through [InputReviser].
     * @param inputReviser
     *         an optional function that can be specified to modify the user's
     *         input before it is processed and saved as the new component's
     *         raw text content.
     * @param onValueChange
     *         the input field's `onValueChange` callback that has to be
     *         triggered by this method if the new text has a valid format and
     *         can be recognized as a respective entity.
     * @param valueMutableState
     *         an input field's `value` property that holds the value to be
     *         updated with a newly edited one.
     */
    @Suppress("SwallowedException" /* Exception value is not needed. */)
    private fun handleChange(
        currentRawTextContent: RawTextContent,
        newRawTextContentCandidate: RawTextContent,
        inputReviser: InputReviser?,
        valueMutableState: MutableState<V?>,
        onValueChange: ((V?) -> Unit)? = null
    ) {
        val revisedTextContent = inputReviser?.reviseRawTextContent(
            currentRawTextContent,
            newRawTextContentCandidate
        ) ?: newRawTextContentCandidate

        val value = if (revisedTextContent.text != "") {
            if (currentRawTextContent.text == "") {
                onDirtyStateChange?.invoke(true)
            }
            try {
                parseValue(revisedTextContent.text)
            } catch (e: ValueParseException) {
                ownValidationMessage.value = e.validationErrorMessage
                valueValid.value = false
                intermediateText = revisedTextContent.text
                selection = revisedTextContent.selection
                return
            } catch (
                // This generic catch is needed to identify, and kindly report
                // cases of uncontrolled throwing of any unexpected exceptions.
                @Suppress("TooGenericExceptionCaught")
                e: Throwable
            ) {
                throw IllegalStateException(
                    "Unexpected exception thrown by `InputField.parseValue`: " +
                            "${e.javaClass.name}.\n" +
                    "Make sure to explicitly throw `${ValueParseException::class.qualifiedName}` " +
                            "instead\n" +
                    "in order to report validation failures during parsing." +
                    "You can also consider changing the implementation of `parseValue` to use " +
                            "either\n" +
                    "`exceptionBasedParser` or `vBuildBasedParser` function as a convenience.",
                    e
                )
            }
        } else {
            if (currentRawTextContent.text != "") {
                onDirtyStateChange?.invoke(false)
            }
            null
        }

        ownValidationMessage.value = null
        valueValid.value = true

        valueMutableState.value = value
        onValueChange?.invoke(value)
        intermediateText = null
        selection = revisedTextContent.selection
    }

    /**
     * Retrieves the [RawTextContent] for an input field, potentially applying
     * revision before displaying the content. This function handles the logic
     * for displaying the appropriate text content based on the current state
     * of the input field.
     */
    @Composable
    private fun getRawTextContent(): RawTextContent {
        val previousInputReviser = remember { mutableStateOf(inputReviser) }
        val currentRawTextContent =
            RawTextContent(intermediateText ?: value.value?.let { formatValue(it) }
            ?: "", selection)

        if (previousInputReviser.value != inputReviser) {
            previousInputReviser.value = inputReviser

            if (intermediateText != null) {
                return getRevisedRawTextContent(currentRawTextContent)
            }
        }

        return currentRawTextContent
    }

    /**
     * Retrieves the revised raw text content based on the current state of the
     * input field.
     *
     * @param currentRawTextContent
     *         the current [RawTextContent] for an input field, before revision.
     */
    private fun getRevisedRawTextContent(
        currentRawTextContent: RawTextContent
    ): RawTextContent {
        val revisedRawTextContent = inputReviser?.reviseRawTextContent(
            RawTextContent(""),
            currentRawTextContent
        ) ?: currentRawTextContent

        if (revisedRawTextContent.text == "") {
            value.value = null
            intermediateText = null
            selection = revisedRawTextContent.selection
            ownValidationMessage.value = null
        } else {
            if (valueValid.value) {
                intermediateText = null
                selection = revisedRawTextContent.selection
                ownValidationMessage.value = null
            } else {
                intermediateText = revisedRawTextContent.text
                selection = revisedRawTextContent.selection
            }
        }

        return revisedRawTextContent
    }
}

/**
 * An exception that is thrown when [InputField]'s `parseValue` callback
 * cannot parse a value entered by the user.
 *
 * @property validationErrorMessage
 *         a human-readable input value validation error message that describes
 *         what was found to be wrong about the user's entry, which couldn't be
 *         parsed as a valid value.
 * @param cause
 *         an exception that is being declared as the cause for this exception.
 */
public class ValueParseException(
    public val validationErrorMessage: String = "Enter a valid value",
    cause: Throwable? = null
) : RuntimeException(
    "Parsing failed with error: $validationErrorMessage", cause
) {
    public companion object {
        private const val serialVersionUID: Long = -4668463236512226876L
    }
}

/**
 * A functional interface that specifies a method for parsing the [InputField]'s
 * raw text.
 */
public fun interface InputFieldParser<V> {

    /**
     * A function that should validate the given input field's raw text, and, if
     * it has a valid format for parsing a value of type [V], and satisfies all
     * constraints that might have been defined for the target value, then it
     * should return the respective value of type [V] that corresponds to
     * that text.
     *
     * If the text doesn't have a valid format, or doesn't satisfy any of the
     * constraints that are defined for the edited value, then the function
     * should throw [ValueParseException].
     *
     * More specifically, besides just parsing the raw text and thus ensuring
     * that it can be interpreted as a value of type [V] (for example a date
     * string having some specific format), this method is also allowed to
     * perform additional validation for the parsed value to ensure that it
     * satisfies any of the constraints that are relevant for the edited value.
     *
     * For example, in case of parsing a date value, the successfully parsed
     * date value can be checked to be in a certain range (e.g. if it has to be
     * a date in the past).
     *
     * Upon an inability to either parse a string representation of [V] (raw
     * text), or upon successfully parsing a string value, but then detecting
     * that it doesn't match some respective constraints, this method should
     * throw [ValueParseException] whose `validationErrorMessage` property can
     * be set with a human-readable message that specifies the reason of
     * the failure, which will be displayed near the field.
     *
     * @param rawText
     *         an input field's raw text that needs to be parsed for creating
     *         a value of type [V].
     * @return a valid value of type [V] that is the result of
     *         parsing [rawText].
     * @throws ValueParseException if [rawText] cannot be parsed as
     *         a valid value.
     */
    @Throws(ValueParseException::class)
    public fun parse(rawText: String): V
}

/**
 * An interface that allows filtering and modifying the user's input
 * before it goes into [InputField].
 *
 * Provides methods to revise the entered input text and to
 * stop propagation of specific key events.
 *
 * Input revision is performed in two stages:
 * 1. Each incoming key press (event) in the field undergoes initial check
 *    through the [filterKeyEvent] method first. This step can selectively stop
 *    propagation of certain events.
 *
 *    For instance, if a key typing event is stopped here, it prevents the
 *    corresponding raw text content modification and skips triggering the
 *    next stage. As a result, the associated character does not appear
 *    in the field.
 *
 * 2. If a key event that involves modifying the field's content
 *    was admitted by [filterKeyEvent], the respective modification is then
 *    revised using the [reviseRawTextContent] method.
 *
 *    In cases where specific characters are consistently removed in
 *    [reviseRawTextContent], it is imperative to also stop the corresponding
 *    key events within [filterKeyEvent]. This ensures the appropriate behavior
 *    of the cursor in such scenarios.
 *
 * @see [InputField]
 */
public interface InputReviser {

    /**
     * A function, which is invoked upon any user's attempt to modify the input
     * field's raw text content, which has an ability to amend the user's entry
     * before it is accepted as a new input field's raw text content.
     *
     * See the "Revising the entered text" section in
     * [InputField]'s description.
     *
     * @param currentRawTextContent
     *         a [RawTextContent], which represents the current value
     *         of text that field has, before user modification.
     * @param rawTextContentCandidate
     *         a [RawTextContent] which holds raw text content that was
     *         just modified by the user.
     * @return [RawTextContent] which holds revised user input text.
     */
    public fun reviseRawTextContent(
        currentRawTextContent: RawTextContent,
        rawTextContentCandidate: RawTextContent
    ): RawTextContent

    /**
     * A function, which is invoked when user presses any key inside
     * input field, which has ability to stop further propagation of pressed
     * key event.
     *
     * This function should only make decision whether to stop propagation of
     * pressed key event, it should not have any side effects.
     * Its purpose is to filter key events of interest and should not be misused
     * for implementing sophisticated key processing logic for purposes
     * other than key event filtering.
     *
     * @param keyEvent
     *         a key event which occurs when user presses key.
     * @return `true` if [keyEvent] should be stopped from
     *         further propagation, `false` otherwise.
     */
    public fun filterKeyEvent(keyEvent: KeyEvent): Boolean

    public companion object {

        /**
         * An [InputReviser], which accepts only digits.
         */
        public val DigitsOnly: InputReviser = object : InputReviser {

            override fun reviseRawTextContent(
                currentRawTextContent: RawTextContent,
                rawTextContentCandidate: RawTextContent
            ): RawTextContent {
                return rawTextContentCandidate.copy(
                    rawTextContentCandidate.text.filter { it.isDigit() }
                )
            }

            override fun filterKeyEvent(keyEvent: KeyEvent): Boolean {
                return keyEvent matches (!Digit).typed
            }
        }

        /**
         * An [InputReviser], which accepts everything except whitespaces.
         */
        public val NonWhitespaces: InputReviser = object : InputReviser {

            override fun reviseRawTextContent(
                currentRawTextContent: RawTextContent,
                rawTextContentCandidate: RawTextContent
            ): RawTextContent {
                return rawTextContentCandidate.copy(
                    rawTextContentCandidate.text.filter { !it.isWhitespace() }
                )
            }

            override fun filterKeyEvent(keyEvent: KeyEvent): Boolean {
                return keyEvent matches Whitespace.typed
            }
        }

        /**
         * Provides an [InputReviser], which truncates the text to
         * the specified maximum length.
         *
         * @param maxLength
         *         a maximum number of characters that can be
         *         retained (inclusive).
         * @return a requested [InputReviser].
         */
        public fun maxLength(maxLength: Int): InputReviser = object : InputReviser {

            override fun reviseRawTextContent(
                currentRawTextContent: RawTextContent,
                rawTextContentCandidate: RawTextContent
            ): RawTextContent {
                return rawTextContentCandidate.copy(
                    rawTextContentCandidate.text.substring(
                        0,
                        min(maxLength, rawTextContentCandidate.text.length)
                    )
                )
            }

            override fun filterKeyEvent(keyEvent: KeyEvent): Boolean {
                return false
            }
        }

        /**
         * An infix function, which chains two [InputReviser]s in a way that
         * when the raw text content candidate is revised, it first gets revised
         * by the first one (the infix receiver) and then the revised content is
         * passed for further revision by [additionalReviser], and when
         * determining if a key event should be stopped from further
         * propagation, it is first determined by the receiver [InputReviser]
         * and if it is not stopped from propagation by the receiver,
         * then it gets determined by [additionalReviser].
         *
         * @receiver the first reviser that should process the raw text
         *           content candidate and key event.
         * @param additionalReviser
         *         the second reviser that should process the raw text
         *         content candidate and key event.
         * @return a new `InputReviser`, which combines the action of two
         *         other revisers.
         */
        public infix fun InputReviser.then(
            additionalReviser: InputReviser
        ): InputReviser = object : InputReviser {

            override fun reviseRawTextContent(
                currentRawTextContent: RawTextContent,
                rawTextContentCandidate: RawTextContent
            ): RawTextContent {
                val intermediateRevision = this@then.reviseRawTextContent(
                    currentRawTextContent,
                    rawTextContentCandidate
                )
                return additionalReviser.reviseRawTextContent(
                    currentRawTextContent,
                    intermediateRevision
                )
            }

            override fun filterKeyEvent(keyEvent: KeyEvent): Boolean {
                return this@then.filterKeyEvent(keyEvent) ||
                        additionalReviser.filterKeyEvent(keyEvent)
            }
        }
    }
}

/**
 * Provides a [VisualTransformation], which ensures that the input field's
 * `visualTransformation` is not applied to an empty unfocused field.
 *
 * This improves the user's experience if the field's `visualTransformation`
 * transforms an empty value into some displayed value (e.g. for displaying
 * an input pattern). Similar to how the prompt text works, it's more convenient
 * to ensure that such an output appears only when the field is focused.
 * This way all empty fields are displayed in the same way (a field's label is
 * displayed inside the field) until they are focused or filled in.
 */
@Composable
private fun inputTransformation(
    visualTransformation: VisualTransformation?,
    focused: Boolean
): VisualTransformation {
    return VisualTransformation { rawText ->
        if ((visualTransformation != null) &&
            (focused || rawText.isNotBlank())
        ) {
            visualTransformation.filter(rawText)
        } else {
            TransformedText(rawText, OffsetMapping.Identity)
        }
    }
}


/**
 * A helper method designed to be used within [InputField.parseValue], to help
 * fulfill its contract in a more compact way, which is useful when there's
 * a parser that is expected to throw a particular exception in case of
 * parsing failure.
 *
 * More precisely it runs the given [parser], and when it identifies that
 * it has thrown  [parseFailureException] to signify the parsing failure, it
 * fulfills the [InputField.parseValue]'s contract by throwing
 * [ValueParseException] with the given [failureMessage].
 *
 * @param parseFailureException
 *         an exception expected to be thrown by [parser] when it identifies
 *         a parsing error.
 * @param failureMessage
 *         a parsing failure message that will be reported when throwing
 *         [ValueParseException] (in case when parsing failure occurs).
 * @param parser
 *         a lambda that is run to perform the actual parsing.
 * @return the result of successful parsing.
 * @throws ValueParseException
 *         when [parser] has thrown an exception whose class is either
 *         the same as [parseFailureException] or extends that class.
 * @see [InputField.formatValue]
 */
public inline fun <V> exceptionBasedParser(
    parseFailureException: KClass<out Throwable>,
    failureMessage: String = "Enter a valid value",
    parser: () -> V,
): V =
    try {
        parser()
    } catch (
        // This method in itself still provides another way of narrow
        // catching of exceptions (see [ExceptionClass]).
        @Suppress("TooGenericExceptionCaught")
        e: Throwable
    ) {
        if (!(parseFailureException.javaObjectType.isAssignableFrom(e.javaClass))) {
            throw e
        }
        throw ValueParseException(failureMessage, e)
    }
