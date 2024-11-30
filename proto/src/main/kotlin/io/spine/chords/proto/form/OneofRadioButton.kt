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
import com.google.protobuf.Message
import io.spine.chords.core.AbstractComponentSetup
import io.spine.chords.core.FocusRequestDispatcher
import io.spine.chords.core.FocusableComponent
import io.spine.chords.core.primitive.RadioButtonWithText
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageFieldValue
import io.spine.protobuf.ValidatingBuilder

/**
 * A radio button, which can be placed into a
 * [OneOfFields][FormPartScope.OneOfFields] declaration to allow the user to
 * switch between oneof fields.
 */
public class OneofRadioButton : FocusableComponent() {
    public companion object : AbstractComponentSetup({ OneofRadioButton() }) {

        /**
         * The version of `OneofFields`, which expects the editor for
         * the respective oneof field to be declared independently.
         *
         * When using this version of declaring `OneofRadioButton`, just ensure
         * to have a respective field editor in the same way that you would do
         * for any other field.
         *
         * @receiver A scope of type [OneOfFieldsScope], which is introduced
         *   by the parent [OneOfFields][FormPartScope.OneOfFields] declaration
         *   that corresponds to the oneof being edited.
         * @param M A type of message to which the respective oneof
         *   field belongs.
         * @param F A type of the oneof field.
         *
         * @param field The message's field that corresponds to this radio
         *   button (should match the one specified for the corresponding
         *   [Field][FormFieldsScope.Field] associated with this radio button).
         * @param text A text displayed for this radio button.
         * @return A [OneofRadioButton] instance that corresponds to
         *   this declaration.
         */
        context(OneOfFieldsScope<M>)
        @Composable
        public operator fun <M : Message, F: MessageFieldValue> invoke(
            field: MessageField<M, F>,
            text: String
        ): OneofRadioButton = createAndRender({
            registerFieldSelector(field, this)
        }) {
            RadioButtonWithText(
                selected = selectedField.value == field,
                onClick = {
                    @Suppress(
                        // `selectedField` treats all fields
                        // as `MessageFieldValue`.
                        "UNCHECKED_CAST"
                    )
                    selectedField.value = field as MessageField<M, MessageFieldValue>
                },
                enabled = formPartScope.formScope.form.editorsEnabled.value,
                text = text,
                focusRequestDispatcher = FocusRequestDispatcher(focusRequester)
            )
        }

        /**
         * Similar to the other version of `OneofRadioButton`, but also
         * implicitly adds a [MessageForm][io.onedam.elements.form.MessageForm()]
         * component for editing the contents of the specified field.
         *
         * It is applicable only in cases when the field specified by
         * [field] is a message-typed field, and is useful when you intend
         * to create an in-place form for editing the contents of that
         * field's message.
         *
         * @receiver A scope of type [OneOfFieldsScope], which is introduced
         *    by the parent [OneOfFields][FormPartScope.OneOfFields] declaration
         *    that corresponds to the oneof being edited.
         * @param M A type of message to which the respective oneof
         *   field belongs.
         * @param F A type of message that is edited in the field associated
         *   with this radio button.
         *
         * @param field The message's field that corresponds to this radio
         *   button (should match the one specified for the corresponding
         *   [Field][FormFieldsScope.Field] associated with this radio button).
         * @param text A text displayed for this radio button.
         * @param builder A function that should create and return a new builder
         *   for a message of type [F].
         * @param content A composable function that defines the content for the
         *   nested form that edits the message in field [field]. Each field
         *   editor in the nested form should be wrapped using the
         *   [Field][FormFieldsScope.Field] function available in the
         *   [FormPartScope] context provided for [content].
         * @return A [OneofRadioButton] instance that corresponds to
         *   this declaration.
         */
        context(OneOfFieldsScope<M>)
        @Composable
        public operator fun <M : Message, F : Message> invoke(
            field: MessageField<M, F>,
            text: String,
            builder: () -> ValidatingBuilder<out F>,
            content: (@Composable FormPartScope<F>.() -> Unit)? = null
        ): OneofRadioButton {
            val instance = OneofRadioButton(field, text)
            MessageForm(
                field,
                builder,
                content = content ?: {}
            )
            return instance
        }
    }

    @Composable
    override fun content() {
        // No composable content in addition to the one declared in
        // the component's invoker is needed.
    }
}
