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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Common data that affects input components contained in context of an object
 * implemented by this interface (e.g. a form).
 */
@Stable
public interface InputContext {
    public val effectivelyLiveValidation: Boolean
}


/**
 * A base class for class-based input component implementations.
 *
 * Here's an example of creating an input component that allows entering
 * a string value:
 *
 * ```kotlin
 *     public class StringInputComponent
 *     private constructor(props: () -> Unit) : InputComponent<String>(props) {
 *         public companion object : ComponentCompanion<StringInputComponent>({
 *             StringInputComponent(it)
 *         })
 *
 *         @Composable
 *         override fun content() {
 *             TextField(
 *                 value = value.value ?: "",
 *                 onValueChange = { value.value = it }
 *             )
 *         }
 *     }
 * ```
 *
 * Such a component can be used like this:
 * ```kotlin
 *     val someString = remember { mutableStateOf<String?>("") }
 *     StringInputComponent { value = someString }
 *     println("someString = ${someString.value}")
 * ```
 *
 * #### Input component's value
 *
 * By convention, all input components have to be implemented in a way that
 * always keeps the value of [value] state up-to-date according to any data
 * specified by the user in the component.
 *
 * As soon as the user makes any change in any respective part of component's
 * editor, the [value] is updated with a corresponding value of type [V] that
 * reflects the current component's entry. Likewise, whenever the value inside
 * the [value] state is changed outside a component, the component should
 * update the data displayed and edited inside it according to that value.
 *
 * #### Input validation and component's value
 *
 * Depending on the nature of data type edited in the component, and the way how
 * such data is edited in the component, it is possible that some values or
 * input combinations specified by the user within the component might be
 * considered as not valid ones. For example an email field can identify whether
 * an entered text is formatted as a valid email or not, etc.
 *
 * The convention of how input components should handle such cases are
 * as follows:
 *
 * - Whenever the component contains a valid data entry at any given point in
 *   time, it provides a respective value of type [V] inside of [value] state,
 *   and reports a value of `true` in its [valueValid] state.
 *
 * - Whenever the component contains an invalid data entry at any given point in
 *   time, it provides `null` inside of [value], and `false` inside
 *   of [valueValid].
 *
 *   Besides, to ensure a good user's experience, and make the current state
 *   explicit to the user, a component is responsible for notifying the user of
 *   such an invalid entry to help one to understand and correct the entry
 *   as needed. E.g., it could typically be a respective validation error
 *   message displayed inside the component.
 *
 * #### Internal and external validation
 *
 * The validation-related information above describes the [InputComponent]'s
 * ability to enforce a proper input needed to specify a value of type [V].
 * This is called an internal validation for convenience here. This kind of
 * validation is performed by the input component itself, before it comes up
 * with an actual value that it stores in the [value] property. The status of
 * this kind of validation is reflected in the [valueValid] state.
 *
 * It might also be needed for an application to validate field values after
 * they have been entered using `InputComponent`s. For example a form might need
 * to check that its field values are consistent with each other, etc. Such kind
 * of validation is performed outside input components (e.g. in the form where
 * such fields are included).
 *
 * When such "external" validation procedure detects that the value entered into
 * a particular input component doesn't satisfy some additional requirements, it
 * can enforce this input component to be displayed as an invalid one to
 * the user despite the input component itself didn't detect any validation
 * errors and has emitted the respective value of type [V], accompanied by its
 * `valueValid` having a value of `true`. In such cases the form that contains
 * such input component generates the respective "external" validation message
 * that it passes to the input component so that it can display it inside.
 *
 * In practice for any implementation of input component this means that
 * whenever [externalValidationMessage] contains a non-`null` value, the input
 * component has to display that message in any way that the component typically
 * displays its own validation errors. An input component cannot change the text
 * inside [externalValidationMessage] itself, and should just display it
 * whenever it contains a non-`null` value. Note that
 * [externalValidationMessage] concerns only the external validation, and it is
 * not related to the component's own internal validation state, so it is
 * a normal situation that a component reports its [valueValid] state to contain
 * `true`, but [externalValidationMessage] can contain a non-`null` value at
 * the same time.
 *
 * It should also be noted that from the user's standpoint there's no
 * separation between the internal and external validation errors, and these
 * notions just address two possible sources of validation errors for
 * input fields.
 *
 * #### The "dirty" state
 *
 * An input component also discerns a so called "dirty" state.  An input
 * component is considered to be "dirty" if it contains any entry relative to
 * its `null` [value] state, regardless of whether it is classified to be valid
 * or not.
 *
 * Note that a dirty state is not always the same as having a non-`null` value
 * in [value]. For example a text-based input field that has some invalid data
 * entered still reports `null` in the [value] state, but is already considered
 * to be dirty.
 *
 * Identifying a component's dirty state can for example be useful in cases when
 * the very beginning of data entry in an empty field needs to be detected
 * (e.g. for implementing a smarter behavior in hierarchical entry forms having
 * optionally filled sections).
 *
 * Whenever the input component transitions from/to the dirty state, it has
 * to invoke the [onDirtyStateChange] callback.
 *
 * @param V
 *         a type of values that this input component allows to edit.
 * @constructor a constructor, which is used internally by the input component's
 *         implementation. Use [companion object's][ComponentCompanion]
 *         [invoke][ComponentCompanion.invoke] operators for instantiating
 *         and rendering any specific input component in any application code.
 */
@Stable
public abstract class InputComponent<V> : FocusableComponent() {

    /**
     * A [MutableState] that holds the value to be displayed in the input
     * component, and receives the updated values of type [V] as they are
     * entered in the component, as soon as the user's entry was identified by
     * the component to be valid.
     *
     * A value of `null` corresponds to a "not present" value if the input
     * component is in the respective state, or an invalid input. A value of
     * [valueValid] at the same time describes whether the field has
     * a valid value.
     */
    public open lateinit var value: MutableState<V?>

    /**
     * A [MutableState], whose value is maintained by this component depending
     * on whether the component's current entry is considered to be valid by
     * the component itself or not.
     *
     * If an invalid value is entered, then the value in this [MutableState] is
     * set to `false`. Otherwise, it's set to `true`.
     */
    public open lateinit var valueValid: MutableState<Boolean>

    /**
     * Specifies whether the valid entry within the component can only result in
     * a non-`null` value.
     *
     * A value of `false` means that an input component can report its certain
     * entry state as representing a `null` value, and still have `valueValid`
     * to be `true`.
     *
     * A value of `true` means that the input component can only report a `null`
     * value if its current entry is invalid (when it reports `valueValid` to
     * be `false`).
     * TODO:2024-03-17:dmitry.pikhulya: see how this property can be
     *     supported in all input components, not just in MessageForm
     */
    public var valueRequired: Boolean by mutableStateOf(false)

    /**
     * This property is dedicated to be set by the form automatically for
     * providing an external validation error that should be displayed by
     * the component, if the value within this [MutableState] is not `null`.
     *
     * The value in this [State] is identified by the environment where this
     * input component is used (e.g., by a form that it's a part of), and thus
     * such an external validation state is identified independently in addition
     * to the component's internal validation.
     *
     * NOTE: this property would typically never need to be configured by
     * the application's code when declaring a component in some composable
     * function. Instead, it should be used by the actual component's
     * implementation to ensure that the value specified in this property is
     * displayed as needed.
     */
    public open var externalValidationMessage: State<String?>? = null

    /**
     * A callback, which is invoked every time when a component transitions
     * between an empty and dirty state (in any direction). A callback has
     * an argument of `true`, when the new state is dirty, and `false` when it
     * becomes empty.
     */
    public open var onDirtyStateChange: ((Boolean) -> Unit)? = null

    /**
     * A context that contains any data that affects input components that
     * it contains.
     *
     * A form that contains this editor (meaning this editor serves as an editor
     * for one of the parent form's fields).
     */
    public var inputContext: InputContext? = null
        set(value) {
            if (field == null) {
                field = value
            } else {
                require(field == value) { "Input context can't be changed." }
            }
        }

    /**
     * Specifies whether the field is enabled for receiving the user's input.
     */
    public var enabled: Boolean by mutableStateOf(true)

    @Composable
    @ReadOnlyComposable
    override fun initialize() {
        super.initialize()
        if (!this::value.isInitialized) {
            value = mutableStateOf(null)
        }
        if (!this::valueValid.isInitialized) {
            valueValid = mutableStateOf(true)
        }
    }

    /**
     * Updates input component's validation error(s) or other way of displaying
     * the editor's validation state according to the currently entered data.
     *
     * @param focusInvalidPart
     *         `true` instructs the method to focus the component's editable
     *         part whose current data entry has triggered the validation
     *         failure within the component. If there are no validation errors,
     *         or if `false` is passed, the current focus is not affected.
     */
    public open fun updateValidationDisplay(focusInvalidPart: Boolean = false) {
    }

    /**
     * Clears any data that is entered in the field (both valid and invalid).
     */
    public open fun clear() {
        value.value = null
    }
}
