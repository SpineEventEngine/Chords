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
import io.spine.chords.runtime.MessageField

/**
 * A checkbox that includes a given text on the right.
 *
 * This variant of the component is intended to be used with
 * [MessageForm][io.spine.chords.proto.form.MessageForm] or
 * [CommandForm][io.spine.chords.client.form.CommandMessageForm] and is
 * automatically bound to edit one of the respective fields of the message
 * that is edited in the containing form.
 *
 * @receiver A context introduced by the parent form, whose field is to
 *   be edited.
 * @type M a message type to which the edited boolean field belongs.
 *
 * @param field The form message's field, whose value should be edited with
 *   this component.
 * @param defaultValue A value to be set for the field by default.
 * @param text A text displayed to the right of the checkbox.
 * @param onChange Invoked when the user has changed the "checked" state.
 * @param enabled Indicates whether the component is enabled for receiving
 *   the user input.
 */
@Composable
public fun <M : Message>FormFieldsScope<M>.CheckboxWithText(
    field: MessageField<M, Boolean>,
    defaultValue: Boolean = false,
    text: String,
    onChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true
) {
    val form = formPartScope.formScope.form
    Field(field, defaultValue) {
        io.spine.chords.core.primitive.CheckboxWithText(
            checked = fieldValue,
            onChange = onChange,
            text = text,
            enabled = enabled && form.editorsEnabled.value,
            focusRequestDispatcher = focusRequestDispatcher,
            externalValidationMessage = externalValidationMessage
        )
    }
}
