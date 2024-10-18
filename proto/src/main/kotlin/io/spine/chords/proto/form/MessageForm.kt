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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import com.google.protobuf.Message
import io.spine.chords.core.AbstractComponentCompanion
import io.spine.chords.core.FocusableComponent
import io.spine.chords.core.InputComponent
import io.spine.chords.core.InputContext
import io.spine.chords.proto.form.MessageForm.Companion.invoke
import io.spine.chords.proto.form.ValidationDisplayMode.DEFAULT
import io.spine.chords.proto.form.ValidationDisplayMode.LIVE
import io.spine.chords.proto.form.ValidationDisplayMode.MANUAL
import io.spine.chords.proto.net.UrlField
import io.spine.chords.proto.money.MoneyField
import io.spine.chords.proto.time.DateTimeField
import io.spine.chords.core.FocusRequestDispatcher
import io.spine.base.FieldPath
import io.spine.chords.core.ComponentProps
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageFieldValue
import io.spine.chords.runtime.MessageOneof
import io.spine.protobuf.ValidatingBuilder
import io.spine.chords.proto.value.protobuf.isDefault
import io.spine.validate.ConstraintViolation
import io.spine.validate.ValidationException
import io.spine.chords.proto.value.validate.formattedMessage
import io.spine.chords.runtime.messageDef

/**
 * Different modes of when form's validation errors should be displayed.
 */
public enum class ValidationDisplayMode {

    /**
     * Validation errors for form's fields are updated (displayed and hidden
     * according to the field values) while the user edits field values, without
     * waiting for filling in the whole form and without the need to trigger
     * validation errors display explicitly.
     */
    LIVE,

    /**
     * Validation errors are not updated while the user changes form's fields,
     * and are only updated when the form is asked for this explicitly via
     * its API.
     *
     * @see [MessageForm.updateValidationDisplay]
     */
    MANUAL,

    /**
     * Specifies that the validation mode depends on the parent form
     * (if it exists).
     *
     * If there is no parent form, then validation is updated live, as the user
     * changes form field values. This is identical to the behavior defined by
     * the [LIVE] value.
     *
     * If there is a parent form, the validation display mode is the same as
     * that of the parent form. Thus, this allows to "inherit" validation
     * behavior from the parent form. For convenience, in `MessageForm`
     * documentation, the form's mode when it is either set to [LIVE] explicitly
     * or is set to [DEFAULT] but inherits [LIVE] from the parent is said to be
     * "effectively live". Similarly, there's an "effectively manual" behavior
     * in case with the [MANUAL] mode being specified directly or indirectly for
     * the form.
     */
    DEFAULT
}

/**
 * A kind of input component, which allows creating a field-wise editor UI
 * (form) for creating and editing a Protobuf message's value.
 *
 * Here's an example of using `MessageForm` with components that already support
 * being placed into a form by themselves (have the `Field` declaration inside):
 *
 * ```
 *     val userContact: MutableState<UserContact> = ...
 *     ...
 *     MessageForm(
 *         value = userContact
 *         builder = { UserContact.newBuilder() })
 *     ) {
 *         PersonNameField(
 *                 fieldNumber = UserContact.NAME,
 *                 label = "Name"
 *         )
 *         UrlField(
 *                 fieldNumber = UserContact.WEBSITE_URL,
 *                 label = "Website URL"
 *         )
 *     }
 * ```
 *
 * Here's also a basic example of using `MessageForm` with a field editor
 * component that wasn't created for being used with `MessageForm` (a standard
 * Compose's [TextField][androidx.compose.material3.TextField] component). This
 * requires wrapping the actual field editor component into the
 * [Field][FormFieldsScope.Field] function (see the sections below for
 * the details):
 *
 * ```
 *     val color = remember { mutableStateOf<Color?>(null) }
 *     Column {
 *         MessageForm(value = color, builder = { newBuilder() }) {
 *             Field(RED_FIELD_NUMBER) {
 *                 val valid = remember { mutableStateOf(true) }
 *                 TextField(
 *                     value = round((fieldValue.value ?: 0.0f) * 100).toInt().toString(),
 *                     onValueChange = {
 *                         fieldValue.value = try {
 *                             fieldValueValid.value = true
 *                             it.toInt() / 100.0f
 *                         } catch (e: NumberFormatException) {
 *                             fieldValueValid.value = false
 *                             null
 *                         }
 *                     },
 *                     isError = !valid.value,
 *                     label = { Text("R [0 - 100]") }
 *                 )
 *             }
 *             // ... same for GREEN_FIELD_NUMBER and BLUE_FIELD_NUMBER ...
 *             ValidationErrorText(formValidationError)
 *         }
 *     }
 *     println("color = [r=${color.value?.red}, g=${color.value?.green}, b=${color.value?.blue}]")
 * ```
 *
 * ### Specifying form's content
 *
 * Unlike other input components, `MessageForm` doesn't introduce any visual
 * output or layout capabilities by itself, and rather provides an API for
 * defining per-field editor components within its [content] composable
 * function. The composable declarations provided via [content] specify what
 * will actually be displayed as a form, and can contain any components and
 * layout inside.
 *
 * The main requirement for declaring the composable [content] is that it should
 * contain per-field editor components for all message's fields that need to be
 * specified in order to create a value of type [M]. In order to bind each field
 * editor component to its respective message's field, each field editor
 * component has to be wrapped into the [Field][FormFieldsScope.Field]
 * composable function, which provides the scope  that introduces the variables
 * required for such a binding (see [FormFieldScope]). Most importantly,
 * [FormFieldScope] provides the `fieldValue` as [MutableState], which
 * contains the current field value, which has to be displayed by the editor
 * component, and which should accept any value changes from
 * the editor component.
 *
 * ### The current form's value
 *
 * By default, when the form is displayed for the first time, the values that
 * are propagated to field editor components are defined by respective field
 * values in the message stored in [MutableState] provided with
 * the [value] parameter.
 *
 * Upon any change made in any declared field editor component, an attempt to
 * create a message [M] is made from all the currently entered field values. If
 * it succeeds, the respective validated value of type [M] is set into the
 * [value] state, and the value in the [valueValid] state is set to `true`.
 *
 * In case when any of the field editors report their own internal validation
 * errors (see [FormFieldScope.fieldValueValid]), or if any validation
 * failures are identified by the form when building a message based from the
 * currently entered field values (when invoking the [builder]'s
 * [vBuild][ValidatingBuilder.vBuild] method), this prevents the form from a
 * successful creation of the resulting value [M], and results in `null` to be
 * stored in [value], and `false` to be stored in [valueValid].
 *
 * ### Support for specifying a missing value
 *
 * Just like simple input components, such as [UrlField] or [MoneyField], there
 * can be circumstances when [MessageForm] might need to be specified with
 * an empty (missing) value. Unlike a default `Message` instance that has not
 * present (unset) field values, a missing value means "no message at all" and
 * is represented by a value of `null`. Allowing the user to skip specifying
 * a value in the form might be useful for example when the form represents
 * an editor for an optional field of another message.
 *
 * To allow such an option for the user, set the form's [valueRequired] property
 * to `false`, and place the [OptionalMessageCheckbox] at the top-level scope of
 * the form ([MultipartFormScope], or [FormPartScope]), introduced by either of
 * `MessageForm` composable functions.
 *
 * If a checkbox is unchecked, a form would always report a [value]'s value of
 * `null`, and its [valueValid] state with a value of `true`.
 *
 * ### Displaying per-field validation errors
 *
 * If any validation failures occur when creating a message from values
 * specified in form's fields (as reported by the respective message builder's
 * `vBuild()` method), the respective validation error messages are propagated
 * to all corresponding field editor components as the
 * `externalValidationMessage` variable that can be found in the
 * [FormFieldScope] context introduced by the [Field][FormFieldsScope.Field]
 * function. The `externalValidationMessage` variable obtains a non-`null`
 * string value whenever there's some validation error that should be displayed,
 * and the way how the editor component is declared should ensure that the
 * respective validation error is displayed.
 *
 * ### Nested forms
 *
 * Being a kind of input component, similar to other input components (such as
 * [DateTimeField] or [UrlField]), [MessageForm] can also be nested as a field
 * editor component inside [content] of some other [MessageForm], where
 * a form-like editor is needed for some of its fields.
 *
 * This way it's possible to declare arbitrarily deep nested forms whenever
 * needed for editing respective nested message structures.
 *
 * In the absence of validation errors, the outermost form provides a respective
 * message that includes all the data from all nested forms and their
 * respective fields.
 *
 * Here's an example:
 *
 * ```
 *     val userContact: MutableState<UserContact> = ...
 *     ...
 *     MessageForm(
 *             builder = { UserContact.newBuilder() },
 *             message = userContact
 *     ) {
 *         Field(fieldNumber = UserContact.NAME) {
 *             MessageForm(
 *                     builder = { PersonName.newBuilder() }
 *             ) {
 *                 Field(fieldNumber = PersonName.GIVEN_NAME_FIELD_NUMBER) {
 *                     InputField(
 *                             value = fieldValue,
 *                             label = "Given name"
 *                     )
 *                 }
 *                 Field(fieldNumber = PersonName.FAMILY_NAME_FIELD_NUMBER) {
 *                     InputField(
 *                             value = fieldValue,
 *                             label = "Family name"
 *                     )
 *                 }
 *             }
 *         }
 *         ...
 *     }
 * ```
 *
 * ### Multipart forms
 *
 * In cases when the message's form needs to be split into multiple parts (for
 * example to create a multi-page wizard), the form is declared in the same way
 * as for a regular usage scenario above, but form instance has to be declared
 * with the [Multipart] function, whose content should in turn contain one or
 * more [FormPart][MultipartFormScope.FormPart] declarations where the actual
 * form field editors should be placed.
 *
 * In this way each [FormPart][MultipartFormScope.FormPart] declaration would
 * cover only a part of message's fields, and all
 * [FormPart][MultipartFormScope.FormPart] declarations in the same
 * [MultipartContent] would work collectively as a single form.
 *
 * ```
 *      val userContact: MutableState<UserContact> = ...
 *      val form = remember { MessageForm(...) }
 *      ...
 *      MessageForm.Multipart(
 *          value = userContact,
 *          builder = { UserContact.newBuilder() }
 *      ) {
 *          FormPart {
 *              PersonNameField(...)
 *              UrlField(...)
 *          }
 *          FormPart {
 *              ...
 *          }
 *          ...
 *      }
 *  ```
 *
 * Since the [MessageForm] instance that hosts
 * [FormPart][MultipartFormScope.FormPart] function calls collects the field
 * values that were specified on different form's parts, such form parts are not
 * required to be present all at once at the same time, and can be displayed
 * sequentially, if needed.
 *
 * ### Messages are validated as a whole
 *
 * Note that, similar to the fact that you can't create a part of an object,
 * it's not possible to validate only a part of a form (just one of the
 * [FormPart][MultipartFormScope.FormPart] declarations). A form can only be
 * validated as a whole with all of its parts. It's possible to validate nested
 * forms individually if needed though.
 *
 * Thus, in order to implement such per-part validation, consider combining
 * the "parent" message's fields into groups so that each group would reside in
 * its own message, and would have its own sub-form, which you can validate
 * as needed.
 *
 * ### Instance-based usage
 *
 * The usage examples above follow a declarative style where a `MessageForm`'s
 * instance is created implicitly along with the form's content definition.
 *
 * In some cases it might also be useful to separate `MessageForm` instance
 * creation, and its rendering (or rendering of its individual parts in case of
 * multipart forms).
 *
 * This can be done using the [create] function of `MessageForm`'s companion
 * object, which just creates a [MessageForm] instance, and can be invoked in
 * any context (not just in a `@Composable` function). Then, once you obtain
 * a reference to a newly created `MessageForm`, you can declare the actual
 * form's content using either [Content] function (for singlepart forms), or
 * [MultipartContent] (for multipart forms).
 *
 * ### Displaying validation errors manually
 *
 * The `MessageForm` component always provides an up-to-date input validation
 * state via its [valueValid] property. Nevertheless, it's possible to customize
 * when validation error messages are displayed to the user.
 *
 * There are two aspects that can be utilized to gain more control of form's
 * validation display:
 * - The [validationDisplayMode] parameter can be adjusted to choose between
 *   the live and manual validation errors display (see [ValidationDisplayMode]
 *   for details).
 * - The [updateValidationDisplay] method can be invoked explicitly to update
 *   (show or hide) form validation errors programmatically. This makes sense
 *   only when using the form's display mode is effectively manual.
 *
 * @param M
 *         a type of the message being edited with the form.
 */
// Seems all class's functions are better to have in this class.
@Suppress("TooManyFunctions")
public open class MessageForm<M : Message> :
    InputComponent<M>(), InputContext {

    /**
     * Form instance declaration and creation API.
     */
    public companion object : AbstractComponentCompanion(
        { MessageForm<Message>() }
    ) {

        /**
         * Declares a `MessageForm` instance, which is not bound to a parent
         * form automatically, and can be bound to the respective data field
         * manually as needed.
         *
         * The form's content that is specified with the [content] parameter is
         * expected to include field editors for all message's fields, which
         * are required to create a valid value of type [M].
         *
         * @param M A type of the message being edited with the form.
         * @param B A type of the message builder.
         * @param value The message value to be edited within the form.
         * @param builder A lambda that should create and return a new builder for
         *   a message of type [M].
         * @param props A lambda that can set any additional props on the form.
         * @param onBeforeBuild A lambda that allows to amend the message
         *   after any valid field is entered to it.
         * @param content A form's content, which can contain an arbitrary layout along
         *   with field editor declarations.
         * @return A form's instance that has been created for this declaration site.
         */
        @Composable
        public operator fun <M : Message, B : ValidatingBuilder<out M>> invoke(
            value: MutableState<M?>,
            builder: () -> B,
            props: ComponentProps<MessageForm<M>> = ComponentProps {},
            onBeforeBuild: ((ValidatingBuilder<out M>) -> ValidatingBuilder<out M>) = { it },
            content: @Composable FormPartScope<M>.() -> Unit
        ): MessageForm<M> = Multipart(value, builder, props, onBeforeBuild) {
            FormPart(content)
        }

        /**
         * Declares a `MessageForm` instance, which is automatically bound to
         * edit a parent form's field identified by [field].
         *
         * The form's content that is specified with the [content] parameter is
         * expected to include field editors for all message's fields, which
         * are required to create a valid value of type [M].
         *
         * @receiver The context introduced by the parent form.
         * @param PM Parent message type.
         * @param M A type of the message being edited with the form.
         * @param B A type of the message builder.
         *
         * @param field The parent message's field whose message is to be edited
         *   within this form.
         * @param builder A lambda that should create and return a new builder
         *   for a message of type [M].
         * @param props A lambda that can set any additional props on the form.
         * @param defaultValue A value that should be displayed in the form by default.
         * @param onBeforeBuild A lambda that allows to amend the message
         *   after any valid field is entered to it.
         * @param content A form's content, which can contain an arbitrary
         *   layout along with field editor declarations.
         * @return A form's instance that has been created for this
         *   declaration site.
         */
        context(FormFieldsScope<PM>)
        @Composable
        public operator fun <
                PM : Message,
                M : Message,
                B : ValidatingBuilder<out M>
                >
                invoke(
            field: MessageField<PM, M>,
            builder: () -> B,
            props: ComponentProps<MessageForm<M>> = ComponentProps {},
            defaultValue: M? = null,
            onBeforeBuild: ((ValidatingBuilder<out M>) -> ValidatingBuilder<out M>) = { it },
            content: @Composable FormPartScope<M>.() -> Unit
        ): MessageForm<M> = Multipart(field, builder, props, defaultValue, onBeforeBuild) {
            FormPart(content)
        }

        /**
         * Declares a multipart `MessageForm` instance, which is not bound to
         * a parent form automatically, and can be bound to the respective data
         * field manually as needed.
         *
         * The form's content that is specified with the [content] parameter
         * should specify each form's part with using
         * [FormPart][MultipartFormScope.FormPart] declarations, which, in turn,
         * should contain the respective field editors.
         *
         * @param M A type of the message being edited with the form.
         * @param B A type of the message builder.
         *
         * @param value The message value to be edited within the form.
         * @param builder A lambda that should create and return a new builder
         *   for a message of type [M].
         * @param props A lambda that can set any additional props on the form.
         * @param onBeforeBuild A lambda that allows to amend the message
         *   after any valid field is entered to it.
         * @param content A form's content, which can contain an arbitrary
         *   layout along with field editor declarations.
         * @return A form's instance that has been created for this
         *   declaration site.
         */
        @Composable
        public fun <M : Message, B: ValidatingBuilder<out M>> Multipart(
            value: MutableState<M?>,
            builder: () -> B,
            props: ComponentProps<MessageForm<M>> = ComponentProps {},
            onBeforeBuild: ((ValidatingBuilder<out M>) -> ValidatingBuilder<out M>) = { it },
            content: @Composable MultipartFormScope<M>.() -> Unit
        ): MessageForm<M> = createAndRender({
            this.value = value

            // Storing the builder as ValidatingBuilder internally.
            @Suppress("UNCHECKED_CAST")
            this.builder = builder as () -> ValidatingBuilder<M>
            this.onBeforeBuild = onBeforeBuild
            multipartContent = content
            props.run { configure() }
        }) {
            Content()
        }

        /**
         * Declares a multipart `MessageForm` instance, which is automatically
         * bound to edit a message in a parent form's field identified
         * by [field].
         *
         * The form's content that is specified with the [content] parameter
         * should specify each form's part with using
         * [FormPart][MultipartFormScope.FormPart] declarations, which, in turn,
         * should contain the respective field editors.
         *
         * @receiver The context introduced by the parent form.
         * @param PM Parent message type.
         * @param M A type of the message being edited with the form.
         * @param B A type of the message builder.
         *
         * @param field The parent message's field whose message is to be edited
         *   within this form.
         * @param builder A lambda that should create and return a new builder
         *   for a message of type [M].
         * @param props A lambda that can set any additional props on the form.
         * @param defaultValue A value that should be displayed in the form by default.
         * @param onBeforeBuild A lambda that allows to amend the message
         *   after any valid field is entered to it.
         * @param content A form's content, which can contain an arbitrary
         *   layout along with field editor declarations.
         * @return a form's instance that has been created for this
         *         declaration site.
         */
        context(FormFieldsScope<PM>)
        @Composable
        public fun <
                PM : Message,
                M : Message,
                B : ValidatingBuilder<out M>
                >
                Multipart(
            field: MessageField<PM, M>,
            builder: () -> B,
            props: ComponentProps<MessageForm<M>> = ComponentProps {},
            defaultValue: M? = null,
            onBeforeBuild: ((ValidatingBuilder<out M>) -> ValidatingBuilder<out M>) = { it },
            content: @Composable MultipartFormScope<M>.() -> Unit
        ): MessageForm<M> = createAndRender({

            // Storing the builder as ValidatingBuilder internally.
            @Suppress("UNCHECKED_CAST")
            this.builder = builder as () -> ValidatingBuilder<M>
            this.onBeforeBuild = onBeforeBuild
            multipartContent = content
            props.run { configure() }
        }) {
            ContentWithinField(field, defaultValue)
        }

        /**
         * Creates a [MessageForm] instance without rendering it in
         * the composable content with the same call.
         *
         * This method can be used to create a form's instance outside
         * a composable context, and render it separately. A form's instance
         * created in this way, has to be rendered  explicitly by calling either
         * its [Content] method (for singlepart forms) or its
         * [MultipartContent] method (for rendering a multipart form) in
         * a composable context where it needs to be displayed.
         *
         * @param M A type of the message being edited with the form.
         * @param B A type of the message builder.
         *
         * @param value The message value to be edited within the form.
         * @param builder A lambda that should create and return a new builder
         *   for a message of type [M].
         * @param props A lambda that can set any additional props on the form.
         * @return A form's instance that has been created for this
         *   declaration site.
         */
        public fun <M : Message, B: ValidatingBuilder<out M>> create(
            value: MutableState<M?>,
            builder: () -> B,
            props: ComponentProps<MessageForm<M>> = ComponentProps {}
        ): MessageForm<M> =
            super.create(null) {
                this.value = value

                // Storing the builder as ValidatingBuilder internally.
                @Suppress("UNCHECKED_CAST")
                this.builder = builder as () -> ValidatingBuilder<M>
                props.run { configure() }
            }
    }

    /**
     * An internal form's representation of a message's oneof.
     *
     * @param oneof Message's oneof, which is going to be edited in the form.
     * @param initialSelectedField The initially selected oneof's field.
     */
    internal inner class FormOneof(
        val oneof: MessageOneof<M>,
        initialSelectedField: MessageField<M, MessageFieldValue>?
    ) {

        /**
         * The field that is currently marked as selected among this
         * oneof's fields.
         */
        val selectedMessageField: MutableState<MessageField<M, MessageFieldValue>?> =
            mutableStateOf(initialSelectedField)

        /**
         * A current validation message pertaining to this oneof in general (not
         * to some of its fields in particular).
         */
        val validationMessage: MutableState<String?> = mutableStateOf(null)

        /**
         * Fields that have been registered inside of this oneof in this form.
         */
        val fields = hashMapOf<MessageField<M, MessageFieldValue>, FormField>()

        /**
         * Oneof field selectors (e.g. [OneofRadioButton] components)
         * represented by their respective [FocusableComponent] instances.
         */
        val fieldSelectors = hashMapOf<MessageField<M, MessageFieldValue>, FocusableComponent>()

        /**
         * A [FormField] that corresponds to the field currently selected in
         * this oneof.
         */
        val selectedFormField: FormField?
            get() {
                return fields[selectedMessageField.value]
            }

        /**
         * The last value of [selectedMessageField] that has been observed, which
         * is needed to detect further [selectedMessageField] state changes.
         */
        private var lastSelectedField: MessageField<M, MessageFieldValue>? = initialSelectedField

        /**
         * Takes a note of a form field that was declared in the oneof
         * represented by this instance.
         */
        fun registerFormField(
            field: MessageField<M, MessageFieldValue>,
            formField: FormField
        ) {
            fields[field] = formField
        }

        /**
         * Registers the given field selector component with the
         * specified field.
         *
         * @param field The field whose selector should be registered.
         * @param fieldSelector A field selector component
         *   (such as [OneofRadioButton]).
         */
        internal fun registerFieldSelector(
            field: MessageField<M, MessageFieldValue>,
            fieldSelector: FocusableComponent
        ) {
            check(oneof.fields.contains(field)) {
                "The oneof field (${field.name}) being registered doesn't " +
                "belong to this oneof (${oneof.name})"
            }
            fieldSelectors[field] = fieldSelector
        }

        /**
         * Allows this [FormOneof] to perform any updates that need to be done
         * in a composable context (such as observing [State] changes).
         */
        @Composable
        fun update() {
            if (selectedMessageField.value != lastSelectedField) {
                lastSelectedField = selectedMessageField.value
                handleSelectedFieldNumberChange(selectedMessageField.value)
            }
        }

        /**
         * Invoked when the user changes the "selected" field in this oneof.
         *
         * @param newSelectedField The newly selected field.
         */
        private fun handleSelectedFieldNumberChange(
            newSelectedField: MessageField<M, MessageFieldValue>?
        ) {
            clearOtherFields(newSelectedField)
            fields[newSelectedField]?.focusEditor()
            updateDirty()
        }

        /**
         * Clears all oneof field values except the specified one.
         *
         * @param exceptThisField The field whose value should be retained (and
         *   others cleared).
         */
        private fun clearOtherFields(exceptThisField: MessageField<M, MessageFieldValue>?) {
            fields.entries.forEach {
                if (it.key != exceptThisField) {
                    val field = it.value
                    field.editor?.clear() ?: field.clearValueSilently()
                }
            }
        }
    }

    /**
     * An information that supports editing of a particular message's field
     * in the form.
     *
     * @property formFieldsScopeImpl A part of the form that this field
     *   belongs to.
     * @param initialValue A field value that should be set as a default value
     *   in the respective field editor.
     * @property field The field represented by this instance.
     * @property formOneof A oneof to which this field belongs.
     */
    internal inner class FormField(
        internal val formFieldsScopeImpl: FormFieldsScopeImpl<M>,
        internal val field: MessageField<M, MessageFieldValue>,
        initialValue: MessageFieldValue? = null,
        val formOneof: FormOneof? = null
    ) {

        /**
         * A flag that indicates whether the form requires the field's value to
         * be a non-`null` value.
         *
         * It is set to `true`, if this field is marked as required in
         * the message's declaration, and is `false` otherwise.
         */
        val required = field.required

        /**
         * [FocusRequestDispatcher], which is responsible for
         * focusing the field.
         */
        val focusRequestDispatcher: FocusRequestDispatcher = FocusRequestDispatcher()

        /**
         * The currently edited field's value.
         *
         * A value of `null` means that the value for this field is missing
         * (e.g. the field's value is not specified, or a valid value hasn't
         * been entered yet).
         */
        val value: MutableState<MessageFieldValue?> = mutableStateOf(
            if (
                formOneof == null ||
                formOneof.selectedMessageField.value == field
            ) {
                initialValue
            } else {
                // Unselected oneof fields shouldn't have a value.
                null
            }
        )

        /**
         * A [FormFieldScope] instance for DSL declarations related to
         * this field.
         */
        val scope: FormFieldScopeImpl<M, MessageFieldValue> =
            FormFieldScopeImpl(this, this@MessageForm)

        /**
         * The last field's value (in the [value] state) has been observed,
         * which is stored to be able to detect further [value] state updates.
         */
        private var lastObservedValue: MessageFieldValue? = value.value

        /**
         * A field-related validation error message, if the previous form
         * validation has indicated a failure for this field.
         *
         * The value stored here serves as a means of propagating the form-level
         * validation message to the respective field editor component, which
         * will just have to display it.
         *
         * A value of `null` means that there's currently no validation error
         * for this field.
         *
         * This has a non-`null` value only when a form-level validation has
         * indicated an error for this field, and is not affected by the field
         * editor's internal validation state, which is kept independently
         * (see [valueValid]).
         *
         * @see [valueValid]
         */
        val externalValidationMessage: MutableState<String?> = mutableStateOf(null)

        /**
         * A [MutableState] that should be maintained by the respective field
         * editor component in order to reflect the editor's internal validation
         * state (not the validation state resulting in form the
         * form's validation).
         *
         * Note that [valueValid] only reflects the field editor's own
         * perspective on the field's validity state, which basically reflects
         * its ability to produce a value based on current user's input.
         * The form can perform additional validation over the values provided
         * by field editors, but the status of the form-level validation is
         * independent from [valueValid].
         *
         * @see [externalValidationMessage]
         */
        val valueValid: MutableState<Boolean> = mutableStateOf(true)

        /**
         * A component that edits the field value, if it was registered for
         * this field.
         *
         * Having this information is required for performing a "deep"
         * validation cascade (validating a form with all of its
         * nested subforms).
         */
        var editor: InputComponent<out MessageFieldValue>? = null

        /**
         * Tracks whether the field is currently in a "dirty" state (has any
         * entry, no matter valid or not), as reported by the respective field
         * editor component.
         *
         * A value of `null` means that the dirty state is not known yet
         * (the component hasn't notified of its "dirty" state yet).
         *
         * @see [FormFieldScope.notifyDirtyStateChanged]
         */
        private var dirty: Boolean? = null

        /**
         * Reports whether the field can effectively be considered to be in
         * a "dirty" state.
         *
         * More precisely, this property is `true` if either the field editor
         * has reported a "genuine" dirty state, or a field editor component has
         * a non-`null` and non-default value (albeit not being reported as
         * being "dirty" explicitly). The second case can technically sometimes
         * be possible if an editor component is implemented in a way that
         * doesn't bother to report its dirty state
         * (by invoking [FormFieldScope.notifyDirtyStateChanged]).
         *
         * @see [FormFieldScope.notifyDirtyStateChanged]
         */
        val effectivelyDirty: Boolean
            get() {
                var eDirtyField = _effectivelyDirty
                if (eDirtyField == null) {
                    eDirtyField = (dirty == true) ||
                            (value.value is Message && !(value.value as Message).isDefault())
                    _effectivelyDirty = eDirtyField
                }
                return eDirtyField
            }

        /**
         * A backing property that contains a current cached value for
         * `effectivelyDirty`. A value of `null` means that a recalculation of
         * a cached value is needed.
         */
        private var _effectivelyDirty: Boolean? = null
        private var lastObservedEffectivelyDirty: Boolean? = null

        init {
            formOneof?.registerFormField(field, this)
        }

        /**
         * Requests the field's editor to receive a focus.
         */
        fun focusEditor() {
            formFieldsScopeImpl.formPartScope.showPart()
            focusRequestDispatcher.changeReschedulingKey()
            val oneofFieldSelector = formOneof?.fieldSelectors?.get(field)
            oneofFieldSelector?.focus()
            if (focusRequestDispatcher.isAttached()) {
                focusRequest.value = focusRequestDispatcher
            }
        }

        /**
         * Allows this [FormField] to perform any updates that need to be done
         * in a composable context (such as observing [State] changes).
         */
        @Composable
        fun update() {
            val currentValue = value.value
            if (currentValue != lastObservedValue) {
                handleValueEdited()
                lastObservedValue = currentValue
            }
        }

        /**
         * Invoked when the user edits the field's value.
         *
         * The value in the [value] state contains the newly edited value by
         * the time this method is invoked.
         */
        private fun handleValueEdited() {
            checkEffectivelyDirtyStateChange()
        }

        private fun selectThisOneofField() {
            val oneof = formOneof
            if (oneof != null) {
                // Make this field to be the "selected" oneof option once
                // the user places some value inside ef this field.
                oneof.selectedMessageField.value = field
            }
        }

        /**
         * Clears (sets to `null`) the value stored in the [value] state without
         * triggering [handleValueEdited].
         */
        fun clearValueSilently() {
            value.value = null
            lastObservedValue = null
            _effectivelyDirty = false
            lastObservedEffectivelyDirty = false
        }

        fun dirtyStateChanged(dirty: Boolean) {
            this.dirty = dirty
            checkEffectivelyDirtyStateChange()
        }

        private fun checkEffectivelyDirtyStateChange() {
            _effectivelyDirty = null
            val effectivelyDirty = effectivelyDirty
            if (lastObservedEffectivelyDirty != effectivelyDirty) {
                lastObservedEffectivelyDirty = effectivelyDirty
                if (effectivelyDirty) {
                    selectThisOneofField()
                }
                this@MessageForm.updateDirty()
            }
        }
    }

    /**
     * A singlepart form's content to be rendered within the form.
     */
    protected var content: (@Composable FormPartScope<M>.() -> Unit)? = null

    /**
     * A multipart form's content to be rendered within the form.
     */
    protected var multipartContent: (@Composable MultipartFormScope<M>.() -> Unit)? = null


    /**
     * A lambda that should create and return a new builder for a message
     * of type [M].
     *
     * Note that we're storing the builder by a generic `ValidatingBuilder<M>`
     * interface internally to prevent having to expose an additional
     * builder's type parameter via the `MessageForm`'s public API, which would
     * "contaminates" other types with this type parameter, make the usage of
     * `MessageField` more complex, and didn't add much value to
     * the user anyway.
     */
    protected lateinit var builder: () -> ValidatingBuilder<M>

    /**
     * Specifies when form's validation errors should be updated.
     */
    public var validationDisplayMode: ValidationDisplayMode by mutableStateOf(DEFAULT)

    /**
     * Allows to programmatically amend the message builder before the message is built.
     *
     * This callback is invoked upon every attempt to build the message edited in the form,
     * which happens when any message's field is edited by the user. When this callback is invoked,
     * the message builder's fields have already been set from all form's field editors,
     * which currently have valid values. Note that there is no guarantee that the message
     * that is about to be built is going to be valid.
     *
     * The altered builder should be returned as a result of this method.
     * For example, if we wanted to set message's `field1` and `field2` explicitly,
     * this could be done like this:
     *
     * ```
     *     MessageForm(..., onBeforeBuild = {
     *         with(it) {
     *             field1 = field1Value
     *             field2 = field2Value
     *         }
     *         it
     *     },  ...) ...
     * ```
     */
    protected var onBeforeBuild: ((ValidatingBuilder<out M>) -> ValidatingBuilder<out M>) = { it }

    init {
        valueRequired = true
    }

    private val messageDef get() = builder().messageDef()

    /**
     * Message's fields that are a part of this form.
     *
     * Each entry represents a mapping from [MessageField] value, which
     * identifies a message's field to the respective [FormField] object.
     */
    private val fields = linkedMapOf<MessageField<M, out MessageFieldValue>, FormField>()

    /**
     * Message's oneofs that are a part of this form.
     *
     * This stands for a Protobuf message's `oneof` declarations that are being
     * edited in the form, mapped to by their respective names.
     */
    private val oneofs = linkedMapOf<MessageOneof<M>, FormOneof>()

    /**
     * The latest cached message value that was emitted by [MessageForm] for
     * the purpose of observing external updates.
     */
    private var lastEmittedMessageValue: M? = null

    /**
     * A current form-wide validation error (the one that is not related to any
     * particular field), if there's such an error.
     *
     * If there's no such an error, this state object holds a `null` value.
     */
    public val formValidationError: MutableState<String?> = mutableStateOf(null)

    /**
     * A focus request dispatcher, which, when not `null`, specifies the field
     * that has to be focused as soon as possible.
     *
     * Since the actual focusing can only be invoked from a composable context,
     * such an intermediary is required to allow scheduling of focus requests
     * from inside of code that is not a part of a composable context.
     */
    private val focusRequest: MutableState<FocusRequestDispatcher?> = mutableStateOf(null)

    /**
     * A flag that informs whether this form effectively uses the [LIVE]
     * validation display mode.
     *
     * By "effectively" it's meant here that this property will report a `true`
     * value either when this form's [validationDisplayMode] is explicitly set
     * to [LIVE], or if it is set to [DEFAULT] for this form, and a value of
     * [LIVE] is inherited from the parent form.
     */
    override val effectivelyLiveValidation: Boolean
        get() = recoveringFromManualValidationErrors || when (validationDisplayMode) {
            LIVE -> true
            MANUAL -> false
            DEFAULT -> inputContext?.effectivelyLiveValidation ?: true
        }

    /**
     * A value of `true` denotes a state when a form that has a `MANUAL`
     * validation display mode didn't pass a manual validation request (some of
     * its fields have been reported as having validation errors).
     *
     * In this mode we don't want any field whose value has been corrected to
     * still display a validation display mode, so this flag effectively makes
     * the validation in this form's state to behave as a `LIVE` one.
     */
    private var recoveringFromManualValidationErrors = false

    /**
     * `true` if the form was set up by the user to provide a non-`null` value,
     * and `false`, if the user has requested `null` to be the value of
     * the form.
     *
     * Note that a value of `true` doesn't yet necessarily mean that a form
     * already has enough data entered to provide a non-`null` value in it's
     * `message`, but the form in this mode will still enforce the user to enter
     * a non-`null` value via validation.
     *
     * This state is intended to be used in cases when a form edits a value of
     * an optional field, and is basically a state that holds the user's
     * decision of whether the respective value should be specified.
     */
    public val enteringNonNullValue: MutableState<Boolean> = mutableStateOf(false)

    /**
     * The last state of `formScope.enteringNonNullValue` property that has been
     * observed, which is used to observe extraneous changes to this property.
     */
    private var lastObservedEnteringNonNullValue: Boolean = false

    /**
     * A [MultipartFormScope] instance, which is an outermost scope for
     * declarations within this form.
     */
    private val formScope = MultipartFormScopeImpl(this)

    /**
     * Has a value of `true` when the form is in the "dirty" state.
     *
     * A "dirty" state means that a component currently displays any data
     * (either valid or invalid). For the form this means that any of its fields
     * display any valid or invalid data (e.g. even if the user has entered
     * something which is not valid so far).
     *
     * A value of `false` means that neither of the form's fields displays
     * any data.
     */
    public val dirty: Boolean get() = _dirty
    private var _dirty = false

    override fun initialize() {
        super.initialize()
        // TODO:2024-03-15:dmitry.pikhulya: document specifying required properties
        require(this::builder.isInitialized) {
            "MessageForm's `builder` property must be specified."
        }

        lastEmittedMessageValue = value.value
        _dirty = identifyInitialDirtyState(value.value)
        enteringNonNullValue.value = identifyInitialEnteringNonNullValue()
        lastObservedEnteringNonNullValue = enteringNonNullValue.value
    }

    /**
     * Renders the form's contents in a composable context, which only needs
     * to be done explicitly if the form's instance has been created using
     * the [create] function.
     *
     * This method shouldn't be invoked if the form's instance is declared with
     * any version of the [invoke] operator, or using the [Multipart] instance
     * declaration function.
     *
     * This content rendering method is applicable for forms, which don't need
     * to be split into parts that are rendered separately.
     *
     * @see MultipartContent
     */
    @Composable
    public fun Content(content: @Composable FormPartScope<M>.() -> Unit) {
        this.content = content
        Content()
    }

    /**
     * Renderings the form's contents in a composable context, which only needs
     * to be done explicitly if the form's instance has been created using
     * the [create] function.
     *
     * This method shouldn't be invoked if the form's instance is declared with
     * any version of the [invoke] operator, or using the [Multipart] instance
     * declaration function.
     *
     * This content rendering method is applicable for forms, which need to be
     * split into parts that are rendered separately. Use
     * [FormPart][MultipartFormScope.FormPart] for declaring each of individual
     * form's parts.
     *
     * @see Content
     */
    @Composable
    public fun MultipartContent(content: @Composable MultipartFormScope<M>.() -> Unit) {
        multipartContent = content
        Content()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun content() {
        if (lastEmittedMessageValue != null && value.value == null) {
            if (enteringNonNullValue.value) {
                enteringNonNullValue.value = identifyInitialEnteringNonNullValue()
            }
        }

        val enteringNonNullValue = enteringNonNullValue.value
        if (!enteringNonNullValue && lastObservedEnteringNonNullValue) {
            clear()
            lastObservedEnteringNonNullValue = false
        }
        lastObservedEnteringNonNullValue = enteringNonNullValue

        content?.let {
            formScope.FormPart(it)
        }
        multipartContent?.invoke(formScope)

        updateMessage(effectivelyLiveValidation, false)
        lastEmittedMessageValue = value.value

        val focusRequestDispatcher = focusRequest.value
        val currentInputModeManager = LocalInputModeManager.current
        LaunchedEffect(
            focusRequestDispatcher,
            focusRequestDispatcher?.requestReschedulingKey?.value
        ) {
            // TODO:2024-04-23:dmitry.pikhulya: This works around some
            //  programmatic focus request failures after mouse clicks. See here:
            //    https://github.com/JetBrains/compose-multiplatform/issues/4629#issuecomment-2064072834
            //    https://github.com/JetBrains/compose-multiplatform/issues/2818#issuecomment-1452529129
            //  It should be removed when the issues above are fixed.
            //  The respective 1DAM issue:
            //    https://github.com/Projects-tm/1DAM/issues/76
            currentInputModeManager.requestInputMode(InputMode.Keyboard)

            focusRequestDispatcher?.requestFocus()
            // Clear the current focus request unless it has been changed
            // in the `requestFocus()` call above!
            if (focusRequest.value == focusRequestDispatcher) {
                focusRequest.value = null
            }
        }
    }

    private fun identifyInitialDirtyState(initialMessageValue: M?): Boolean = when {
        initialMessageValue == null ->
            false

        !initialMessageValue.isDefault() ->
            true

        messageDef.oneofs.any { oneof ->
            oneof.selectedField(initialMessageValue) != null
        } ->
            true

        else ->
            false
    }

    private fun identifyInitialEnteringNonNullValue(): Boolean = when {
        valueRequired || value.value != null ->
            true

        messageDef.fields.isEmpty() -> {
            // Zero-field messages will never receive "dirty" notifications from
            // its field editors (as there are no fields to edit), and so
            // `enteringNonNullValue` won't be set automatically for such
            // messages.
            //
            // Such zero-field messages are still possible, and can
            // make sense in some cases though. One of such cases (maybe the
            // only one that makes sense) is having such a message type as one
            // (or more) of oneof fields.
            //
            // Hence, we're setting enteringNonNullValue = true automatically
            // for such messages, for the form to provide a non-`null` value
            // even if the field wasn't declared as a required one.
            true
        }

        else ->
            false
    }


    /**
     * Clears the data entered in the form.
     */
    override fun clear() {
        clearValidationDisplay()
        fields.values.forEach {
            it.value.value = null
            it.editor?.clear()
        }
    }

    /**
     * Registers the field edited in the form.
     *
     * Invoked internally by [Field][FormFieldsScope.Field] and not expected to
     * be invoked explicitly.
     *
     * @param field
     *         the message's field that is being registered.
     * @param createFormField
     *         a callback that creates a [FormField] instance that should
     *         be registered
     */
    internal fun registerField(
        field: MessageField<M, out MessageFieldValue>,
        createFormField: () -> FormField
    ): FormField =
        fields.getOrPut(field, createFormField)

    /**
     * Registers the message's oneof edited in the form.
     *
     * Invoked internally by [OneOfFields][FormPartScope.OneOfFields] and not
     * expected to be invoked explicitly.
     *
     * @param oneof A oneof being registered.
     */
    internal fun registerOneof(
        oneof: MessageOneof<M>
    ): FormOneof = oneofs.getOrPut(oneof) {
        val initialSelectedField =
            if (value.value != null) {
                oneof.selectedField(value.value!!)
            } else {
                null
            }
        FormOneof(oneof, initialSelectedField)
    }

    private fun updateDirty() {
        val dirty =
            fields.values.any { it.effectivelyDirty } ||
            oneofs.values.any { it.selectedMessageField.value != null }
        if (!_dirty && dirty) {
            enteringNonNullValue.value = true
        }
        if (_dirty != dirty) {
            _dirty = dirty
            onDirtyStateChange?.invoke(dirty)
        }
    }

    /**
     * Tries to build a message using all valid field values entered in the form
     * so far, and update the [value] state accordingly, optionally displaying
     * validation results if validation failures are encountered.
     *
     * If there are no validation failures while building the updated message's
     * value, then the value in the [value] state is updated with
     * the newly built message, and the [valueValid] state is set to `true`.
     * Otherwise, the [value] state's value is set to a value of `null`, and
     * the [valueValid] state's value is set to `false`.
     *
     * The validation is performed using whatever rules are built in with
     * the standard Spine's `vBuild()` functionality of [ValidatingBuilder].
     * The fields that are reported as having validation failures in this
     * process are marked as having errors in the UI, and respective error
     * messages propagated to the editor components.
     *
     * Note that any of the field editors can also have their own internal
     * validation errors according to the current input (which could mean an
     * invalid or incomplete entry in some of the fields). In this case, the
     * form's validation is also considered to fail.
     *
     * This method states a validation error (sets [valueValid] to `true`, and
     * [value] to `null`),  if any of the validation constraints fail during
     * `vBuild()`, or if any fields have their internal validation errors.
     * The form's status is updated to reflect the validation failures before
     * the exception is thrown, which can include focusing the respective field
     * whose entered data has caused the validation failure, and displaying
     * respective validation errors.
     *
     * @param updateValidationErrors
     *         specifies whether a failure to create a message should be
     *         accompanied by displaying the respective validation errors.
     * @param focusInvalidField
     *         specifies whether the first field that has caused a validation
     *         failure (if any) should receive a focus.
     */
    private fun updateMessage(
        updateValidationErrors: Boolean = true,
        focusInvalidField: Boolean = true
    ) {
        if (updateValidationErrors) {
            clearValidationDisplay()
            fields.values.forEach {
                it.editor?.updateValidationDisplay(focusInvalidField)
            }
        }

        if (!enteringNonNullValue.value) {
            check(!valueRequired) {
                "enteringNonNullValue cannot be set to false " +
                        "on a form whose valueRequired is true."
            }
            value.value = null
            valueValid.value = true
            return
        }

        val validatedMessage = try {
            var builder = builderWithCurrentFields()
            val beforeBuildCallback = onBeforeBuild
            builder = beforeBuildCallback(builder)
            builder.vBuild()
        } catch (e: ValidationException) {
            if (updateValidationErrors) {
                showConstraintViolations(e.constraintViolations)
                if (focusInvalidField) {
                    focus()
                }
                recoveringFromManualValidationErrors = true
            }
            valueValid.value = false
            value.value = null
            return
        }

        val nestedErrorsPresent =
            enteringNonNullValue.value &&
                    fields.values.any {
                        !it.valueValid.value
                    }
        if (updateValidationErrors) {
            recoveringFromManualValidationErrors = nestedErrorsPresent
        }
        valueValid.value = !nestedErrorsPresent
        value.value = if (nestedErrorsPresent) {
            if (focusInvalidField) {
                focus()
            }
            null
        } else {
            validatedMessage
        }
    }

    /**
     * Updates the displayed validation errors according to the currently
     * entered editor's data.
     *
     * Note that the form's [valueValid] state is nevertheless always kept up to
     * date upon any updates in the form's fields, regardless of whether
     * [updateValidationDisplay] has been called or not. So this method just
     * ensures that the displayed messages are up-to-date as well, which makes
     * sense only when the form's [validationDisplayMode] is effectively manual.
     *
     * @param focusInvalidPart
     *         `true` instructs the method to focus the editor for the first
     *         form's field that has a validation error. If `false` is passed,
     *         or the form doesn't have invalid fields currently, the focus is
     *         not affected.
     */
    override fun updateValidationDisplay(focusInvalidPart: Boolean) {
        if (!enteringNonNullValue.value) {
            return
        }

        updateMessage(true, focusInvalidPart)
    }

    private fun clearValidationDisplay() {
        formValidationError.value = null
        fields.values.forEach {
            it.externalValidationMessage.value = null
        }
        oneofs.values.forEach {
            it.validationMessage.value = null
        }
    }

    private fun builderWithCurrentFields(): ValidatingBuilder<out M> {
        val builder = builder()
        fields.values.forEach { formField ->
            val fieldValue = formField.value.value
            if (fieldValue != null && formField.formOneof == null) {
                formField.field.setValue(builder, fieldValue)
            }
        }
        oneofs.values.forEach { oneof ->
            val selectedFormField = oneof.selectedFormField
            if (selectedFormField != null) {
                val fieldValue = selectedFormField.value.value
                if (fieldValue != null) {
                    selectedFormField.field.setValue(builder, fieldValue)
                }
            }
        }
        return builder
    }

    private fun showConstraintViolations(
        constraintViolations: List<ConstraintViolation>
    ) {
        constraintViolations.forEach { constraintViolation ->
            val fieldPath = constraintViolation.fieldPath
            if (fieldPath.fieldNameCount > 0) {
                showFieldConstraintViolation(fieldPath, constraintViolation)
            } else {
                formValidationError.value = constraintViolation.formattedMessage
            }
        }
    }

    private fun formFieldByName(fieldName: String): FormField? =
        fields.values.find { it.field.name == fieldName }

    private fun showFieldConstraintViolation(
        fieldPath: FieldPath,
        constraintViolation: ConstraintViolation
    ) {
        val fieldName = fieldPath.getFieldName(0)

        val formField = formFieldByName(fieldName)
        if (formField != null) {
            formField.externalValidationMessage.value = constraintViolation.formattedMessage
        } else {
            val oneofEntry = oneofs.entries.find { it.key.name == fieldName }
            checkNotNull(oneofEntry) {
                "Neither message's field nor oneof was found with this name: $fieldName."
            }
            oneofEntry.value.validationMessage.value = constraintViolation.formattedMessage
        }
    }

    /**
     * Focuses the form.
     *
     * More precisely, it focuses the first field, or the first invalid field
     * (if there's at least one invalid field).
     */
    override fun focus() {
        val fieldToFocus =
            fields.values.firstOrNull { field ->
                !field.valueValid.value ||
                        field.externalValidationMessage.value != null
            } ?: run {
                val formOneof = oneofs.values.firstOrNull {
                    it.validationMessage.value != null ||
                    it.let {
                        val selectedField = it.selectedFormField
                        selectedField != null && selectedField.value.value == null
                    }
                }
                formOneof?.selectedFormField
                    ?: formOneof?.fields?.values?.firstOrNull()
            } ?: run {
                val firstField = fields.values.firstOrNull()

                // If the first field is a oneof field, make sure that the
                // selected oneof option actually gets the focus by default.
                firstField?.formOneof?.selectedFormField ?: firstField
            }

        fieldToFocus?.focusEditor()
    }
}
