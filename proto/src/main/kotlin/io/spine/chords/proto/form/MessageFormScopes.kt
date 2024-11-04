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

package io.spine.chords.proto.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import com.google.protobuf.Message
import io.spine.chords.core.FocusRequestDispatcher
import io.spine.chords.core.FocusableComponent
import io.spine.chords.core.InputComponent
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageFieldValue
import io.spine.chords.runtime.MessageOneof

/**
 * A form's top-level scope introduced by [MessageForm.MultipartContent], which
 * defines the types of declarations available on this level
 * (see [FormPart] functions).
 *
 * @param M A type of message that is edited in the form.
 */
public interface MultipartFormScope<M : Message> {

    /**
     * [MessageForm] that backs this scope.
     */
    public val form: MessageForm<M>

    /**
     * A composable function, which defines the editors for a part or all
     * the form's fields.
     *
     * See the description of the [MessageForm] class for the instructions of
     * how to use this function.
     *
     * @param showPart A function that ensures that the respective part is
     *   revealed in the form's UI. For example, command message's fields can be
     *   split between multiple form parts that can be displayed on different
     *   wizard's pages. In this case, this parameter has to be passed
     *   a function that would display the respective part of the UI.
     * @param content A composable content that defines how the respective
     *   message's part is edited. It can contain an arbitrary UI, but ensure
     *   that each field editor component is wrapped using the
     *   [Field][FormFieldsScope.Field] function that is available via the
     *   nested [FormPartScope] that is provided as the receiver for this
     *   [content] function.
     */
    @Composable
    public fun FormPart(
        showPart: (() -> Unit)?,
        content: @Composable (FormPartScope<M>.() -> Unit)
    )

    /**
     * The same as the other `FormPart` function, which doesn't require the
     * `showPart` parameter (implies its value to be `null`).
     *
     * @param content a composable content that defines how this message's part
     *     is edited.
     */
    @Composable
    public fun FormPart(
        content: @Composable (FormPartScope<M>.() -> Unit)
    ) {
        FormPart(null, content)
    }
}

/**
 * An internal implementation of [MultipartFormScope].
 *
 * Not intended to be used for any application development directly.
 */
internal class MultipartFormScopeImpl<M : Message>(
    override val form: MessageForm<M>
) : MultipartFormScope<M> {
    @Composable
    override fun FormPart(
        showPart: (() -> Unit)?,
        content: @Composable FormPartScope<M>.() -> Unit
    ) {
        val formPart = remember(form) {
            FormPartScopeImpl(this, showPart, form.value.value)
        }
        formPart.content()
    }
}

/**
 * A kind of scope where form field editors can be declared
 * (using the [Field] function).
 *
 * This is a base type which roots its more concrete ancestors, which represent
 * different contexts where fields can be declared (see [FormPartScope]
 * and [OneOfFieldsScope]).
 *
 * @param M A type of the message edited in the form.
 */
public sealed interface FormFieldsScope<M : Message> {

    /**
     * A [FormPartScope] to which this scope belongs, or `this`
     * if this scope is a [FormPartScope].
     */
    public val formPartScope: FormPartScope<M>

    /**
     * A composable function that has to be placed inside the content of
     * the [MessageForm][io.onedam.elements.validation.MessageForm()] or
     * the [FormPart][MultipartFormScope.FormPart] function to define an editor
     * component for each of form's fields.
     *
     * The field editor component/UI itself has to be placed inside the
     * [content] function, and be set up using the field-specific members
     * introduced by the [FormFieldScope] context.
     *
     * @param field The message's field whose editor is being defined.
     * @param defaultValue A field value that should be displayed in respective
     *   field editor by default.
     * @param content Field editing component(s) that should be bound to members
     *   provided via [FormPartScope].
     * @param V A type of value within this field.
     */
    @Composable
    public fun <V : MessageFieldValue> Field(
        field: MessageField<M, V>,
        defaultValue: V?,
        content: @Composable FormFieldScope<V>.() -> Unit
    )

    /**
     * Same as the other `Field` function, with an implied `defaultValue`
     * parameter's value of `null`.
     */
    @Composable
    public fun <V : MessageFieldValue> Field(
        field: MessageField<M, V>,
        content: @Composable FormFieldScope<V>.() -> Unit
    ) {
        Field(field, null, content)
    }
}

/**
 * A scope introduced by the [MultipartFormScope.FormPart] function, which
 * represents a context in which message's field and oneof editors can
 * be declared.
 *
 * @param M A type of message that is edited in the form.
 */
public sealed interface FormPartScope<M : Message> : FormFieldsScope<M> {

    /**
     * A [MultipartFormScope] to which this form part belongs, which is
     * essentially the outermost form's scope.
     */
    public val formScope: MultipartFormScope<M>

    /**
     * A [State], which contains a current form-wide validation error (the one
     * that is not related to any particular field), if there's such an error,
     * or `null` if there's no such error currently.
     *
     * In case when a particular implemented form needs to display such errors,
     * its UI needs to be configured in a way that takes this [State] as
     * the source of such a message.
     */
    public val formValidationError: State<String?>

    /**
     * A composable function that has to be placed inside the content of
     * the [MessageForm][io.onedam.elements.validation.MessageForm()] or
     * the [FormPart][MultipartFormScope.FormPart] function to define a section
     * of the form that contains field editors for a specific message's
     * oneof declaration.
     *
     * The field editor declarations themselves have to be placed inside the
     * [content] function using the [Field] function, which has to be called
     * for declaring each oneof's field editor.
     *
     * @param oneof Name of the message's oneof being edited in the form.
     * @param content Composable content that specifies [Field] declarations
     *   that constitute this oneof.
     */
    @Composable
    public fun OneOfFields(
        oneof: MessageOneof<M>,
        content: @Composable (OneOfFieldsScope<M>.() -> Unit)
    )

    /**
     * Reveals this form part to be visible on the screen.
     *
     * For example in case of a form spread across a multipage wizard, this
     * method would be expected to switch to the respective wizard's page.
     */
    public fun showPart()
}

/**
 * A scope introduced by [FormPartScope.OneOfFields], which defines
 * the declarations available for declaring oneof field editors.
 *
 * @param M A type of message that is edited in the form.
 */
public sealed interface OneOfFieldsScope<M : Message> : FormFieldsScope<M> {

    /**
     * The currently selected oneof's field.
     */
    public val selectedField: MutableState<MessageField<M, MessageFieldValue>?>

    /**
     * A validation error message identified by the containing form, which, if
     * not `null`, has to be displayed for the respective oneof editing section.
     */
    public val validationMessage: State<String?>

    /**
     * Informs the form component about the component, which is used as
     * a selection toggle (e.g. radio button) for the specified oneof [field].
     *
     * This knowledge is required by the form component to be able to focus
     * respective field selectors when needed.
     */
    public fun <F: MessageFieldValue> registerFieldSelector(
        field: MessageField<M, F>,
        fieldSelector: FocusableComponent
    )
}

/**
 * A scope introduced by the [Field][FormFieldsScope.Field] function in order to
 * facilitate editing the respective field value.
 *
 * @param V A type of field's value.
 */
public sealed interface FormFieldScope<V : MessageFieldValue> {

    /**
     * Informs whether the field is required according to
     * the field's declaration.
     */
    public val fieldRequired: Boolean

    /**
     * A mutable state that contains the current edited value, which has to be
     * displayed in the field editor component/UI, and which should be updated
     * by the field editor when the field value is changed by the user.
     *
     * ## `null` values
     *
     * In any case a value of `null` in [fieldValue] generally means "value not
     * available". This can mean two different cases though:
     *
     *  - A "not present" (unset) field value in the underlying Protobuf message
     *    (a field whose value is not set, see
     *    https://protobuf.dev/programming-guides/field_presence/). An editor is
     *    also allowed to store `null` in this state itself if the editor
     *    supports specifying such a "not present" value.
     *
     *    Note that field editor shouldn't try to implement required field
     *    validation, since this is implemented by the [MessageForm] component
     *    itself, and storing `null` by the editor into [fieldValue] should also
     *    be accompanied by storing `true` in [fieldValueValid].
     *
     *    Whenever the component sets `null` in [fieldValue], this means that
     *    the respective message's field will not be set when the form builds
     *    the resulting message (leaving the field in a "not present" state).
     *    If such fields are marked as required the form will identify the
     *    respective errors (as well as other validation errors) that arise when
     *    building the resulting form's message and handle them appropriately,
     *    including passing the respective messages to the respective field
     *    editors (see [externalValidationMessage]).
     *
     *  - An invalid input inside of field editor. Whenever a field editor
     *    contains an input that it considers invalid (e.g., incomplete, or
     *    improperly formatted entry), it should store `null` into [fieldValue],
     *    and `false` into [fieldValueValid].
     *
     * @see fieldValueValid
     * @see externalValidationMessage
     */
    public val fieldValue: MutableState<V?>

    /**
     * A mutable state, whose value should be kept up to date by the editor
     * component/UI, by storing the editor's current input validation
     * status in it.
     *
     * A field editor should store `true` inside [fieldValueValid], if
     * the editor is in a state where it has a valid entry needed to
     * specify a value of type [V]. The convention is that an editor should also
     * store a current up-to-date value inside [fieldValue] if it's in
     * a valid state.
     *
     * A "not present" (unset) field editor's input value (if it's supported by
     * the editor) should by convention be interpreted as a valid value by
     * the field editor itself, so it should store `true` in [fieldValueValid],
     * and `null` in [fieldValue] in this case.
     *
     * Whenever the component has an invalid (e.g., incomplete) entry, it has
     * to store `false` in [fieldValueValid], and `null` in [fieldValue].
     *
     * @see fieldValue
     */
    public val fieldValueValid: MutableState<Boolean>

    /**
     * A validation error message identified by the containing form, which, if
     * not `null`, has to be displayed by the field editor component/UI.
     *
     * Note that this message is identified by the form, and is not related to
     * [fieldValueValid], which signifies the validation state that is
     * identified by the field editor itself. If there's both a validation error
     * that is identified by the editor itself internally, and there's
     * a non-`null` value passed in [externalValidationMessage], it is up to the
     * component to either display both errors or choose one of them.
     * It typically seems fine to display only the internal editor's validation
     * errors for practical and usability reasons though in such cases.
     */
    public val externalValidationMessage: State<String?>

    /**
     * A focus request dispatcher that has to be attached to the field editor
     * component to enable its focusing by the form validation functionality.
     */
    public val focusRequestDispatcher: FocusRequestDispatcher

    /**
     * If the value editor is implemented as a class-based component (descendant
     * from [InputComponent]), it's recommended to register registers the given
     * `ValueEditor` instance by invoking this function.
     *
     * Such registration allows to perform deep hierarchical validation
     * activities that need to work on the whole hierarchy of forms (or other
     * value editors).
     */
    public fun registerFieldValueEditor(fieldValueEditor: InputComponent<V>)

    /**
     * This function is recommended to be invoked by the field editor component
     * to notify the form that the field's editor has switched to/from the dirty
     * state (from/to being empty).
     *
     * Having an editor to invoke this method appropriately is not critical, but
     * is recommended for improving the user's experience for the kinds of
     * field editors, which can be in a state when no valid (e.g. no
     * sufficiently complete) value can be specified on some intermediate stages
     * of data entry (when a field editor is already not empty, but, being
     * invalid, still report `null` in `fieldValue`).
     *
     * Implementing the appropriate invoking of this function by such editors
     * makes the functionality of automatic selection of parent oneof radio
     * buttons or optional checkboxes to be more intuitive. This basically
     * ensures that such parent field selectors are automatically switched right
     * when the user starts specifying a value, without waiting for a valid
     * value to emerge on later stages of editing.
     */
    public fun notifyDirtyStateChanged(dirty: Boolean)
}

/**
 * An internal implementation of [FormFieldsScope].
 *
 * Not intended to be used for any application development directly.
 */
internal abstract class FormFieldsScopeImpl<M : Message>(
    val formScope: MultipartFormScope<M>,
    val message: M?
) : FormFieldsScope<M> {
    @Composable
    override fun <V : MessageFieldValue> Field(
        field: MessageField<M, V>,
        defaultValue: V?,
        content: @Composable FormFieldScope<V>.() -> Unit
    ) {
        // We have to store fields by their common base type MessageFieldValue
        // in the form, and we cannot use neither `in` nor `out` for the type
        // parameter since the respective field in MessageField is mutable.
        @Suppress("UNCHECKED_CAST")
        val baseTypedField = field as MessageField<M, MessageFieldValue>
        val formField = registerField(baseTypedField, defaultValue)

        // A narrowing typcast is required since we store fields by their common
        // base type, and still need to expose it with a respective concrete one
        // via the respective DSL here.
        @Suppress("UNCHECKED_CAST")
        (formField.scope as FormFieldScopeImpl<M, V>).content()
        formField.update()
    }

    /**
     * Registers the message field that is edited in the form.
     *
     * @param field The message's field being registered.
     * @param defaultValue A value that should be displayed in the respective
     *   field editor by default.
     */
    internal open fun registerField(
        field: MessageField<M, MessageFieldValue>,
        defaultValue: MessageFieldValue?
    ): MessageForm<M>.FormField = formScope.form.registerField(field) {
        val initialValue = if (message != null) {
            if (field.hasValue(message)) {
                field.valueIn(message)
            } else {
                null
            }
        } else {
            defaultValue
        }
        createFormField(formScope.form, field, initialValue)
    }

    protected open fun createFormField(
        form: MessageForm<M>,
        field: MessageField<M, MessageFieldValue>,
        initialValue: MessageFieldValue?
    ): MessageForm<M>.FormField =
        form.FormField(this, field, initialValue)
}

/**
 * An internal implementation of [OneOfFieldsScope].
 *
 * Not intended to be used for any application development directly.
 */
private class OneOfFieldsScopeImpl<M : Message>(
    private val formOneof: MessageForm<M>.FormOneof,
    formPartScopeImpl: FormPartScopeImpl<M>
) : FormFieldsScopeImpl<M>(
    formPartScopeImpl.formScope, formPartScopeImpl.message
), OneOfFieldsScope<M> {

    override val formPartScope: FormPartScope<M> = formPartScopeImpl

    override val selectedField: MutableState<MessageField<M, MessageFieldValue>?>
        get() = formOneof.selectedMessageField

    override val validationMessage: State<String?>
        get() = formOneof.validationMessage

    override fun createFormField(
        form: MessageForm<M>,
        field: MessageField<M, MessageFieldValue>,
        initialValue: MessageFieldValue?
    ): MessageForm<M>.FormField =
        form.FormField(this, field, initialValue, formOneof)

    override fun <F: MessageFieldValue> registerFieldSelector(
        field: MessageField<M, F>,
        fieldSelector: FocusableComponent
    ) {
        formOneof.registerFieldSelector(field, fieldSelector)
    }
}

/**
 * An internal implementation of [FormPartScope].
 *
 * Not intended to be used for any application development directly.
 *
 * @param M A type of message that is edited in the form.
 *
 * @param formScope A [MultipartFormScope], which contains this form
 *   part declaration.
 * @param showPart A function that ensures that the respective part is revealed
 *   in the form's UI.
 */
internal class FormPartScopeImpl<M : Message>(
    formScope: MultipartFormScope<M>,
    showPart: (() -> Unit)?,
    message: M?
) : FormPartScope<M>, FormFieldsScopeImpl<M>(formScope, message) {

    private val _showPart: (() -> Unit)? = showPart

    override val formValidationError: State<String?>
        get() = formScope.form.formValidationError

    override val formPartScope: FormPartScope<M> = this

    /**
     * Registers the message's oneof that is edited in the form.
     *
     * @param oneof The message's oneof that is being registered.
     * @param content A composable function that should define the content
     *   displayed for this oneof section.
     */
    @Composable
    override fun OneOfFields(
        oneof: MessageOneof<M>,
        content: @Composable (OneOfFieldsScope<M>.() -> Unit)
    ) {
        val formOneof = registerOneof(oneof)
        val oneOfFieldsScope = OneOfFieldsScopeImpl(formOneof, this)
        oneOfFieldsScope.content()
        formOneof.update()
    }

    /**
     * Registers the message's oneof that is edited in the form.
     *
     * @param oneof A name of the oneof being registered.
     */
    fun registerOneof(oneof: MessageOneof<M>): MessageForm<M>.FormOneof {
        return formScope.form.registerOneof(oneof)
    }

    override fun showPart() {
        _showPart?.invoke()
    }
}

/**
 * An internal implementation of [FormFieldScope].
 *
 * Not intended to be used for any application development directly.
 *
 * @param M A type of message that this field belongs to.
 * @param V A type of field's value.
 *
 * @param formField A field represented by this scope object.
 * @param form A form to which this field belongs.
 */
internal class FormFieldScopeImpl<M : Message, V : MessageFieldValue>(
    private val formField: MessageForm<M>.FormField,
    val form: MessageForm<M>
) : FormFieldScope<V> {
    override val fieldRequired = formField.required
    override val fieldValue: MutableState<V?>
        // Fields are stored as a base `MessageFieldValue` type internally.
        @Suppress("UNCHECKED_CAST")
        get() = formField.value as MutableState<V?>
    override val fieldValueValid: MutableState<Boolean>
        get() = formField.valueValid
    override val externalValidationMessage: MutableState<String?>
        get() = formField.externalValidationMessage

    override val focusRequestDispatcher: FocusRequestDispatcher
        get() = formField.focusRequestDispatcher

    override fun registerFieldValueEditor(fieldValueEditor: InputComponent<V>) {
        formField.editor = fieldValueEditor
        fieldValueEditor.inputContext = form
    }

    override fun notifyDirtyStateChanged(dirty: Boolean) {
        formField.dirtyStateChanged(dirty)
    }
}
