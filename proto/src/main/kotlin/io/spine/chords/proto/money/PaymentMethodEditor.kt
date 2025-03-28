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

package io.spine.chords.proto.money

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.spine.chords.core.ComponentSetup
import io.spine.chords.core.ValidationErrorText
import io.spine.chords.proto.form.CustomMessageForm
import io.spine.chords.proto.form.FormPartScope
import io.spine.chords.proto.form.OneofRadioButton
import io.spine.chords.proto.form.OptionalMessageCheckbox
import io.spine.chords.proto.form.invoke
import io.spine.chords.proto.value.money.PaymentMethod
import io.spine.chords.proto.value.money.PaymentMethodDef.bankAccount
import io.spine.chords.proto.value.money.PaymentMethodDef.method
import io.spine.chords.proto.value.money.PaymentMethodDef.paymentCard

/**
 * A component that edits a [PaymentMethod].
 */
public class PaymentMethodEditor : CustomMessageForm<PaymentMethod>(
    { PaymentMethod.newBuilder() }
) {
    public companion object : ComponentSetup<PaymentMethodEditor>({ PaymentMethodEditor() })

    /**
     * Identifies the component's appearance parameters.
     */
    public var look: Look = Look()

    /**
     * An object, which defines the component's appearance parameters.
     *
     * @param interFieldPadding A horizontal distance between the payment card
     *   and bank account fields.
     * @param selectorsOffset A vertical distance between radio button selectors
     *   and their respective fields.
     * @param optionalCheckboxOffset A vertical distance between the checkbox,
     *   which is displayed when [required] is `false`, and the rest of
     *   the controls within the component.
     */
    public data class Look(
        public var interFieldPadding: Dp = 40.dp,
        public var selectorsOffset: Dp = 8.dp,
        public var optionalCheckboxOffset: Dp = 16.dp
    )

    @Composable
    override fun FormPartScope<PaymentMethod>.customContent() {
        Column {
            if (!required) {
                Row {
                    OptionalMessageCheckbox("Specify payment method")
                }
                Row(modifier = Modifier.height(look.optionalCheckboxOffset)) {}
            }
            OneOfFields(method) {
                Row(horizontalArrangement = spacedBy(look.interFieldPadding)) {
                    Column(verticalArrangement = spacedBy(look.selectorsOffset)) {
                        OneofRadioButton(paymentCard, "Payment card")
                        PaymentCardNumberField(paymentCard)
                    }
                    Column(verticalArrangement = spacedBy(look.selectorsOffset)) {
                        OneofRadioButton(bankAccount, "Bank Account")
                        BankAccountField(bankAccount)
                    }
                }
                if (validationMessage.value != null) {
                    Row {
                        ValidationErrorText(validationMessage)
                    }
                }
            }
        }
    }
}
