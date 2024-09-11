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

package io.spine.chords.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.protobuf.Message
import io.spine.chords.ComponentCompanion
import io.spine.chords.ComponentProps
import io.spine.chords.InputComponent
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageFieldValue

/**
 * Lazily creates an instance of an input component of type [C], and renders
 * this component as an editor of field [field] within the
 * parent [MessageForm][io.spine.chords.form.MessageForm].
 *
 * Once an instance is created, it is saved using [remember] and is reused
 * for subsequent recompositions.
 *
 * For example in case of a component [C] being `SomeInputComponent`, it can be
 * used as an editor for `SOME_FIELD_NUMBER` inside a [MessageForm] like this:
 *
 * ```kotlin
 *     <MessageForm ...>
 *         ...
 *         SomeInputComponent(SOME_FIELD_NUMBER) {
 *             property1 = value1
 *             property2 = value2
 *             ...
 *         }
 *     </MessageForm>
 * ```
 *
 * Where `property1`, `property2`, etc. are properties of `SomeInputComponent`,
 * which additionally need to be configured for this field editor declaration.
 * If no property configurations are required for such a component instance
 * declaration, it can be expressed like this:
 *
 * ```kotlin
 *     <MessageForm ...>
 *         ...
 *         SomeInputComponent(SOME_FIELD_NUMBER)
 *     </MessageForm>
 * ```
 *
 * @receiver A context introduced by the parent form whose fields need to
 *   be edited.
 * @param C A type of component whose instance is being declared.
 * @param M A type of message, which contains a field that is edited by
 *   this component.
 * @param V A type of field (which belongs to the message of type [M]) that is
 *   edited by this component.
 *
 * @param field The message's field, which is edited by this input component.
 * @param props A lambda that receives a component's instance, and should
 *   configure its properties in a way that is needed for this component's
 *   instance. It is invoked before each recomposition of the component.
 * @return A component's instance that has been created for this
 *   declaration site.
 * @see ComponentCompanion.invoke
 */
context(FormFieldsScope<M>)
@Composable
public operator fun <
        C : InputComponent<V>,
        M : Message,
        V : MessageFieldValue
        >
        ComponentCompanion<C>.invoke(
    field: MessageField<M, V>,
    props: ComponentProps<C>? = null
): C {
    return createAndRender(props) {
        ContentWithinField(field)
    }
}

/**
 * Renders a composable component's content, which is wrapped into
 * the [Field][FormFieldsScope.Field] declaration in order to seamlessly
 * embed this component into
 * [MessageForm][io.spine.chords.form.MessageForm].
 *
 * NOTE: this method is not expected to be invoked by application developers,
 * and it is used internally within the library.
 *
 * A regular way to both lazily instantiate and render a component is via
 * the respective component's companion object's `invoke` operator (see the
 * [ComponentCompanion.invoke] operator functions).
 *
 * @receiver A context introduced by the parent form whose fields need to
 *   be edited.
 * @param M A type of message, which contains a field that is edited by
 *   this component.
 * @param V A type of field (which belongs to the message of type [M]) that is
 *   edited by this component.
 *
 * @param field The message's field, which is edited by this input component.
 * @param defaultValue A field value that should be displayed in the input
 *   component by default.
 */
context(FormFieldsScope<M>)
@Composable
internal fun <
        M : Message,
        V : MessageFieldValue
        > InputComponent<V>.ContentWithinField(
    field: MessageField<M, V>,
    defaultValue: V? = null
) {
    Field(field, defaultValue) {
        this@InputComponent.value = this@Field.fieldValue
        this@InputComponent.valueValid = this@Field.fieldValueValid
        this@InputComponent.externalValidationMessage = this@Field.externalValidationMessage
        this@InputComponent.onDirtyStateChange = { this@Field.notifyDirtyStateChanged(it) }
        this@InputComponent.valueRequired = this@Field.fieldRequired
        this@Field.focusRequestDispatcher.handleFocusRequest = { focus() }
        registerFieldValueEditor(this@InputComponent)

        Content()
    }
}
