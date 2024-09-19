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
import io.spine.chords.core.primitive.CheckboxWithText

/**
 * A component that can be placed inside any variant of the `MessageForm`
 * component to allow the user to skip specifying a message's value.
 *
 * By default, the `MessageForm` component requires the user to enter a value.
 * This value might have all fields empty if they are not required, but the form
 * will still provide a non-`null` message value if all of its fields
 * pass validation. Adding [OptionalMessageCheckbox] allows the user to choose
 * whether some message value should still be specified within the form (and
 * a valid form's message will be a non-`null` value), or no value should be
 * provided (and form's message should be `null`).
 *
 * When the checkbox is checked, the form will construct a non-`null` message
 * value based on the currently entered form field values. When the checkbox is
 * unchecked, the form's message value will be `null`.
 *
 * @param text
 *         a checkbox's text.
 */
@Composable
public fun <M : Message> FormPartScope<M>.OptionalMessageCheckbox(
    text: String
) {
    formScope.OptionalMessageCheckbox(text)
}

/**
 * Same as the other `OptionalMessageCheckbox`, suitable for being placed inside
 * a multipart version of `MessageForm` (see [MessageForm.MultipartContent]).
 *
 * @param text
 *         a checkbox's text.
 */
@Composable
public fun <M : Message> MultipartFormScope<M>.OptionalMessageCheckbox(
    text: String
) {
    check(!form.valueRequired) {
        "OptionalMessageCheckbox can only be used with forms whose valueRequired is false."
    }
    CheckboxWithText(
        checked = form.enteringNonNullValue.value,
        onChange = { form.enteringNonNullValue.value = it },
        text
    )
}
