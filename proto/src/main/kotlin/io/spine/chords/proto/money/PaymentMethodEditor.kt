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

package io.spine.chords.proto.money

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Top
import androidx.compose.ui.unit.dp
import com.google.protobuf.Message
import io.spine.chords.proto.form.FormFieldsScope
import io.spine.chords.proto.form.MessageForm
import io.spine.chords.proto.form.OneofRadioButton
import io.spine.chords.proto.form.OptionalMessageCheckbox
import io.spine.chords.proto.form.invoke
import io.spine.chords.layout.InputRow
import io.spine.chords.runtime.MessageField
import io.spine.money.PaymentMethod
import io.spine.money.bankAccount
import io.spine.money.method
import io.spine.money.paymentCard

/**
 * A component that edits a [PaymentMethod].
 *
 * It is intended to be used with [MessageForm][io.spine.chords.form.MessageForm]
 * and is automatically bound to edit one of the respective fields of the
 * message that is edited in the containing form.
 *
 * @receiver A context introduced by the parent form, whose field is to
 *   be edited.
 * @param M a type of message to which the edited `PaymentMethod` field belongs.
 *
 * @param field the form message's field, whose value should be edited with
 *   this component.
 */
@Composable
public fun <M : Message> FormFieldsScope<M>.PaymentMethodEditor(
    field: MessageField<M, PaymentMethod>
) {
    MessageForm(field, { PaymentMethod.newBuilder() }) {
        InputRow(
            padding = PaddingValues()
        ) {
            OptionalMessageCheckbox("Specify payment method")
        }
        InputRow(
            padding = PaddingValues()
        ) {
            OneOfFields(PaymentMethod::class.method) {
                Column(
                    verticalArrangement = spacedBy(4.dp, Top)
                ) {
                    OneofRadioButton(PaymentMethod::class.paymentCard, "Payment card")
                    PaymentCardNumberField(PaymentMethod::class.paymentCard)
                }
                Column(
                    verticalArrangement = spacedBy(4.dp)
                ) {
                    OneofRadioButton(PaymentMethod::class.bankAccount, "Bank Account")
                    BankAccountField(PaymentMethod::class.bankAccount)
                }
            }
        }
    }
}
