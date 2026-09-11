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

package io.spine.chords.proto.form

import androidx.compose.runtime.Composable
import com.google.protobuf.Message
import io.spine.chords.core.AbstractComponentSetup
import io.spine.chords.core.ComponentSetup
import io.spine.chords.core.InputComponent
import io.spine.chords.core.appshell.Props
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageFieldValue

/**
 * Declares an [InputComponent] as the editor of [field] in the containing [MessageForm].
 *
 * The field retains its editor, including invalid input, while hidden. Returning with
 * the same component setup reuses it and applies current [props]. Switching to another
 * oneof alternative clears its input. Declare one editor per field at a time.
 *
 * Custom setup instances must stay stable across recompositions and form-part visits
 * to retain their editors. Editors must acquire and release composition-bound resources
 * in their content; initialization runs only once per instance.
 *
 * For example, inside a form part for
 * [BankAccount][io.spine.chords.proto.value.money.BankAccount]:
 *
 * ```kotlin
 * StringField(BankAccountDef.number) {
 *     label = "Account number"
 * }
 * ```
 *
 * Import `io.spine.chords.proto.form.invoke` to use this shorthand. If IntelliJ IDEA
 * does not offer the import, temporarily write `StringField.invoke(BankAccountDef.number)`
 * and remove `.invoke` after importing the extension.
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
 * @return The editor instance registered for the parent field.
 * @throws IllegalArgumentException If multiple editors for this field are composed at once.
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
    props: Props<C>? = null
): C = DeclareFieldEditor(field = field, props = props)

/**
 * Registers a field through the regular [FormFieldsScope.Field] lifecycle,
 * then obtains and renders its editor with the current properties.
 *
 * Both ordinary input components and nested forms use this declaration path.
 * The setup must consistently create editors of type [C] for this field.
 */
context(FormFieldsScope<M>)
@Composable
internal fun <
        C : InputComponent<V>,
        M : Message,
        V : MessageFieldValue
> AbstractComponentSetup.DeclareFieldEditor(
    field: MessageField<M, V>,
    defaultValue: V? = null,
    props: Props<C>? = null
): C {
    val fieldsScope = this@FormFieldsScope as FormFieldsScopeImpl<M>
    return fieldsScope.WithField(field = field, defaultValue = defaultValue) {
        Editor(this@DeclareFieldEditor, props)
    }
}
