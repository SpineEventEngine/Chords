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

package io.spine.chords.proto.form

import androidx.compose.runtime.Composable
import com.google.protobuf.Message
import io.spine.chords.core.ComponentProps
import io.spine.chords.core.ComponentSetup
import io.spine.chords.core.InputComponent
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageFieldValue

/**
 * This extension adds a way to declare any [InputComponent] [C] as an editor of
 * field [field] within the parent
 * [MessageForm][io.spine.chords.proto.form.MessageForm].
 *
 * Technically, this is an
 * [operator function](https://kotlinlang.org/docs/operator-overloading.html#invoke-operator),
 * which allows using component's classes like functions.
 *
 * For example in case of
 * a component [C] being `SomeInputComponent`, it can be
 * placed inside a [MessageForm] that edits a message of type `ParentMessage` to
 * edit some of its fields (e.g. `ParentMessage.parentField1`) like this:
 *
 * ```kotlin
 *     <MessageForm ...>
 *         SomeInputComponent(ParentMessageDef.parentField1) {
 *             property1 = value1
 *             property2 = value2
 *             ...
 *         }
 *         ...
 *     </MessageForm>
 * ```
 *
 * Where `property1`, `property2`, etc. are properties of `SomeInputComponent`,
 * which might additionally need to be configured for this particular field
 * editor declaration. If no property configurations are required for this
 * `SomeInputComponent` declaration, it can be expressed like this:
 *
 * ```kotlin
 *     <MessageForm ...>
 *         SomeInputComponent(ParentMessageDef.parentField1)
 *         ...
 *     </MessageForm>
 * ```
 *
 * ### A practical tip for importing this extension
 *
 * The shorthand syntax above (e.g. `SomeInputComponent(ParentMessageDef.parentField1)`)
 * is using this `invoke` operator function implicitly, and the "full" syntax
 * for the same expression would be like this:
 * `SomeInputComponent.Companion.invoke(ParentMessageDef.parentField1)`.
 * Nevertheless, for readability and conciseness it's not recommended to use
 * this "full" form, and a shorthand implicit syntax is recommended.
 *
 * Like any extension, the use of this function requires importing it though!
 * Unfortunately IntelliJ IDEA doesn't automatically offer to import this
 * `invoke` operator function when writing the shorthand notation, and importing
 * this function explicitly can be tedious.
 *
 * As a simple practical solution, IntelliJ IDEA can be "asked" to import it
 * automatically if you temporarily write the `.invoke` call explicitly,
 * like this:
 *
 * ```
 *     SomeInputComponent.invoke(ParentMessageDef.parentField1)`
 * ```
 *
 * This will make the IDEA to offer the respective import, after which you can
 * just remove the explicit `.invoke` call:
 *
 * ```
 *     SomeInputComponent(ParentMessageDef.parentField1)`
 * ```
 *
 * This needs to be done only once per file, while using the shortened notation
 * throughout the file after this.
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
 * @see ComponentSetup.invoke
 */
context(FormFieldsScope<M>)
@Composable
public operator fun <
        C : InputComponent<V>,
        M : Message,
        V : MessageFieldValue
> ComponentSetup<C>.invoke(
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
 * [MessageForm][io.spine.chords.proto.form.MessageForm].
 *
 * NOTE: this method is not expected to be invoked by application developers,
 * and it is used internally within the library.
 *
 * A regular way to both lazily instantiate and render a component is via
 * the respective component's companion object's `invoke` operator (see the
 * [ComponentSetup.invoke] operator functions).
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
        this@InputComponent.enabled = this@Field.fieldEnabled.value
        this@Field.focusRequestDispatcher.handleFocusRequest = { focus() }
        registerFieldValueEditor(this@InputComponent)

        Content()
    }
}
