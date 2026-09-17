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

package io.spine.chords.proto.form.validation

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.chords.proto.TestApplication
import io.spine.chords.proto.form.FormFieldScope
import io.spine.chords.proto.form.MessageForm
import io.spine.chords.proto.form.OneOfFieldsScope
import io.spine.chords.proto.form.given.MessageFormFixtures.account
import io.spine.chords.proto.form.given.MessageFormFixtures.inScene
import io.spine.chords.proto.form.given.MessageFormFixtures.paymentForm
import io.spine.chords.proto.form.invoke
import io.spine.chords.proto.money.BankAccountField
import io.spine.chords.proto.money.PaymentCardNumberField
import io.spine.chords.proto.value.money.BankAccount
import io.spine.chords.proto.value.money.BankAccountDef
import io.spine.chords.proto.value.money.PaymentCardNumber
import io.spine.chords.proto.value.money.PaymentMethod
import io.spine.chords.proto.value.money.PaymentMethodDef
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Verifies that form validation identifies the selected editor that needs input.
 */
@DisplayName("`MessageForm` should")
internal class MessageFormSpec {

    /**
     * Initial empty details stay clean through repeated validation and clearing later edits.
     */
    @ParameterizedTest
    @ValueSource(ints = [0, 1])
    fun `keep untouched payment details clean after validation`(selectedIndex: Int) {
        val builder = PaymentMethod.newBuilder()
        if (selectedIndex == 0) {
            builder.setPaymentCard(PaymentCardNumber.getDefaultInstance())
        } else {
            builder.setBankAccount(BankAccount.getDefaultInstance())
        }
        val form = paymentForm(builder.buildPartial())
        lateinit var card: FormFieldScope<PaymentCardNumber>
        lateinit var bank: FormFieldScope<BankAccount>

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    Field(PaymentMethodDef.paymentCard) { card = this }
                    Field(PaymentMethodDef.bankAccount) { bank = this }
                    PaymentCardNumberField(PaymentMethodDef.paymentCard)
                    BankAccountField(PaymentMethodDef.bankAccount)
                }
            }
        }) { scene ->
            form.dirty shouldBe false

            form.updateValidationDisplay(focusInvalidPart = false)
            scene.render()
            form.valid.value shouldBe false
            form.dirty shouldBe false

            form.updateValidationDisplay(focusInvalidPart = false)
            scene.render()
            form.dirty shouldBe false

            if (selectedIndex == 0) {
                card.fieldValue.value = PaymentCardNumber.newBuilder()
                    .setValue("4242424242424242")
                    .vBuild()
            } else {
                bank.fieldValue.value = account("12345678")
            }
            scene.render()
            form.dirty shouldBe true

            form.updateValidationDisplay(focusInvalidPart = false)
            scene.render()
            form.dirty shouldBe true

            if (selectedIndex == 0) card.fieldValue.value = null else bank.fieldValue.value = null
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * A selected oneof alternative needs feedback on its empty editor, not on the group.
     */
    @ParameterizedTest
    @ValueSource(ints = [0, 1])
    fun `validate the selected empty oneof field`(selectedIndex: Int) {
        val form = paymentForm()
        lateinit var oneof: OneOfFieldsScope<PaymentMethod>
        lateinit var card: FormFieldScope<PaymentCardNumber>
        lateinit var bank: FormFieldScope<BankAccount>

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    oneof = this
                    Field(PaymentMethodDef.paymentCard) { card = this }
                    Field(PaymentMethodDef.bankAccount) { bank = this }
                }
            }
        }) { scene ->
            form.updateValidationDisplay(focusInvalidPart = false)
            oneof.validationMessage.value
                .shouldNotBeNull()
            card.externalValidationMessage.value shouldBe null
            bank.externalValidationMessage.value shouldBe null

            val fields = listOf(card, bank)
            val choices = listOf("payment_card", "bank_account")
            oneof.selectedField.value = PaymentMethodDef.method.fields
                .single { it.name == choices[selectedIndex] }
            scene.render()
            form.updateValidationDisplay(focusInvalidPart = false)

            form.valid.value shouldBe false
            fields[selectedIndex].externalValidationMessage.value
                .shouldNotBeNull()
            fields[1 - selectedIndex].externalValidationMessage.value shouldBe null
            oneof.validationMessage.value shouldBe null

            if (selectedIndex == 0) {
                card.fieldValue.value = PaymentCardNumber.newBuilder()
                    .setValue("4242424242424242")
                    .vBuild()
            } else {
                bank.fieldValue.value = account("12345678")
            }
            scene.render()
            form.valid.value shouldBe true
            fields[selectedIndex].externalValidationMessage.value shouldBe null

            if (selectedIndex == 0) card.fieldValue.value = null else bank.fieldValue.value = null
            scene.render()
            form.updateValidationDisplay(focusInvalidPart = false)
            fields[selectedIndex].externalValidationMessage.value
                .shouldNotBeNull()

            oneof.selectedField.value = PaymentMethodDef.method.fields
                .single { it.name == choices[1 - selectedIndex] }
            scene.render()
            fields[selectedIndex].externalValidationMessage.value shouldBe null
            fields[1 - selectedIndex].externalValidationMessage.value
                .shouldNotBeNull()
            oneof.validationMessage.value shouldBe null
        }
    }

    /**
     * A nested editor's own field error must not be duplicated as a missing oneof value.
     */
    @Test
    fun `keep nested validation feedback within the selected oneof editor`() {
        val initial = PaymentMethod.newBuilder()
            .setBankAccount(account(""))
            .buildPartial()
        val form = paymentForm(initial)
        lateinit var oneof: OneOfFieldsScope<PaymentMethod>
        lateinit var number: FormFieldScope<String>

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    oneof = this
                    MessageForm(PaymentMethodDef.bankAccount, BankAccount::newBuilder) {
                        Field(BankAccountDef.number) { number = this }
                    }
                }
            }
        }) { scene ->
            form.updateValidationDisplay(focusInvalidPart = false)
            scene.render()

            form.valid.value shouldBe false
            number.externalValidationMessage.value
                .shouldNotBeNull()
            oneof.validationMessage.value shouldBe null

            number.fieldValue.value = "12345678"
            scene.render()
            form.valid.value shouldBe true
            number.externalValidationMessage.value shouldBe null
        }
    }

    /**
     * Supplies application defaults for the off-screen forms.
     */
    private companion object {

        /**
         * Installs the shared application before composing editors.
         */
        @JvmStatic
        @BeforeAll
        fun setUpApplication() {
            TestApplication.install()
        }
    }
}
