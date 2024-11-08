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

import com.google.protobuf.Message
import io.spine.protobuf.ValidatingBuilder
import io.spine.chords.core.Component

/**
 * A base class, which should be used for implementing custom form components
 * that edit values of some specific type [M].
 *
 * Custom components which extend this class can have the same capabilities as
 * [MessageForm], but they would typically not require the user (developer) to
 * provide a message's builder and the content (message field editors) for the
 * form, since they would be a part of the custom form's implementation itself.
 *
 * If it is needed to create a custom form component for editing, e.g.
 * `PersonName` message values, it can be done like this:
 *
 * - Create a subclass of `CustomMessageForm` (`PersonNameForm` in this case),
 *   while providing the respective message builder as the `MessageForm`
 *   constructor's parameter.
 *
 * - Add a companion object of type
 *   [ComponentSetup][io.spine.chords.core.ComponentSetup] to ensure that the
 *   component can actually be used (like any other class-based [Component]]).
 *
 * - Override either [customContent] (for rendering ordinary singlepart forms),
 *   or [customMultipartContent] (if a multipart form is required), which should
 *   contain field editors in the same way as you would fill the respective
 *   `MessageForm` if it would be declared as a standalone form (see the
 *   [MessageForm]'s documentation for details).
 *
 * Here's an example:
 * ```
 *     public class PersonNameForm : MessageForm<PersonName>({ PersonName.newBuilder() }) {
 *         public companion object : ComponentSetup<PersonNameForm>({ PersonNameForm() })
 *
 *         @Composable
 *         override fun FormPartScope<PaymentMethod>.customContent() {
 *           // Form's contents: field editors, layout, etc.
 *         }
 *     }
 * ```
 *
 * Custom form components declared in this way can be used like other regular
 * [InputComponent][io.spine.chords.core.InputComponent]. Here's an example
 * of how the custom `PersonNameForm` component defined above can be used inside
 * some composable function:
 *
 * ```
 *     val personName: MutableState<PersonName> = getPersonName()
 *     PersonNameForm { value = personName }
 * ```
 *
 * Besides, similar to other [InputComponent][io.spine.chords.core.InputComponent]s,
 * such a custom form component can be placed as an editor of some field within
 * [MessageForm]. Say a parent form edits a message of type `ParentMessage`, and
 * it has a field `parentField1` of type `PersonName`. Then a custom
 * `PersonNameForm` component can be placed into the form to edit this field
 * like this:
 *
 * ```kotlin
 *     <MessageForm ...>
 *         PersonNameForm(ParentMessageDef.parentField1)
 *         ...
 *     </MessageForm>
 * ```
 *
 * See the respective [invoke][io.spine.chords.proto.form.invoke] extension
 * for the details.
 *
 * @param M A type of message edited by the form.
 *
 * @constructor Initializes the custom message form instance.
 * @param builder A lambda, which should create a builder for a message of
 *   type [M].
 */
public open class CustomMessageForm<M : Message>
protected constructor(builder: () -> ValidatingBuilder<M>) : MessageForm<M>() {
    init {
        this.builder = builder
    }
}
