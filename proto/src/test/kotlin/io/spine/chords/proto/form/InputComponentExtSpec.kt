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

import androidx.compose.runtime.mutableStateOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.chords.core.primitive.StringField
import io.spine.chords.proto.TestApplication
import io.spine.chords.proto.form.given.MessageFormFixtures.AccountFormSetup
import io.spine.chords.proto.form.given.MessageFormFixtures.TrimmingInputField
import io.spine.chords.proto.form.given.MessageFormFixtures.account
import io.spine.chords.proto.form.given.MessageFormFixtures.accountForm
import io.spine.chords.proto.form.given.MessageFormFixtures.inScene
import io.spine.chords.proto.form.given.MessageFormFixtures.paymentForm
import io.spine.chords.proto.value.money.BankAccount
import io.spine.chords.proto.value.money.BankAccountDef
import io.spine.chords.proto.value.money.PaymentCardNumber
import io.spine.chords.proto.value.money.PaymentMethod
import io.spine.chords.proto.value.money.PaymentMethodDef
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies ownership and lifetime of field-bound forms and their input editors.
 */
@DisplayName("`InputComponentExt` should")
internal class InputComponentExtSpec {

    /**
     * Reusing a nested form must not construct and discard another instance.
     */
    @Test
    fun `create a nested editor only once across form part visits`() {
        val form = paymentForm()
        val showPart = mutableStateOf(true)
        var creations = 0
        val setup = AccountFormSetup { creations++ }
        lateinit var nestedForm: MessageForm<BankAccount>

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    if (showPart.value) {
                        nestedForm = setup { Field(BankAccountDef.number) {} }
                    }
                }
            }
        }) { scene ->
            val firstForm = nestedForm
            creations shouldBe 1

            showPart.value = false
            scene.render()
            showPart.value = true
            scene.render()

            nestedForm shouldBeSameInstanceAs firstForm
            creations shouldBe 1
        }
    }

    /**
     * Invalid text belongs to the field editor even while its page is hidden.
     */
    @Test
    fun `retain invalid input and apply current props when a field returns`() {
        val form = accountForm()
        val showPart = mutableStateOf(true)
        val label = mutableStateOf("First label")
        lateinit var input: TrimmingInputField

        inScene({
            form.Content {
                if (showPart.value) {
                    input = TrimmingInputField(BankAccountDef.number) {
                        this.label = label.value
                        onValidate = { "Invalid account number." }
                    }
                }
            }
        }) { scene ->
            val firstInput = input
            input.enterText("invalid")
            scene.render()
            form.dirty shouldBe true

            showPart.value = false
            scene.render()
            label.value = "Updated label"
            showPart.value = true
            scene.render()

            input shouldBeSameInstanceAs firstInput
            input.label shouldBe label.value
            input.value.value shouldBe null
            input.valid.value shouldBe false
            form.dirty shouldBe true

            input.enterText("")
            scene.render()
            input.valid.value shouldBe true
            form.dirty shouldBe false
        }
    }

    /**
     * Keeping only the nested form loses the invalid text held by its own editor.
     */
    @Test
    fun `retain invalid nested input across form part visits`() {
        val form = paymentForm()
        val showPart = mutableStateOf(true)
        lateinit var input: TrimmingInputField

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    if (showPart.value) {
                        MessageForm(PaymentMethodDef.bankAccount, BankAccount::newBuilder) {
                            input = TrimmingInputField(BankAccountDef.number) {
                                onValidate = { "Invalid account number." }
                            }
                        }
                    }
                }
            }
        }) { scene ->
            val firstInput = input
            input.enterText("invalid")
            scene.render()
            form.dirty shouldBe true

            showPart.value = false
            scene.render()
            showPart.value = true
            scene.render()

            input shouldBeSameInstanceAs firstInput
            input.value.value shouldBe null
            input.valid.value shouldBe false
            form.dirty shouldBe true
        }
    }

    /**
     * A declaration site may display another parent form without sharing its editor.
     */
    @Test
    fun `keep editors with their parent when the displayed form changes`() {
        val firstForm = accountForm(account("123"))
        val secondForm = accountForm(account("456"))
        val currentForm = mutableStateOf(firstForm)
        lateinit var input: TrimmingInputField

        inScene({
            currentForm.value.Content {
                input = TrimmingInputField(BankAccountDef.number)
            }
        }) { scene ->
            val firstInput = input
            input.enterText("789")
            scene.render()

            currentForm.value = secondForm
            scene.render()
            input.value.value shouldBe "456"
            secondForm.dirty shouldBe false
            input.inputContext shouldBeSameInstanceAs secondForm

            currentForm.value = firstForm
            scene.render()
            input shouldBeSameInstanceAs firstInput
            input.value.value shouldBe "789"
            firstForm.dirty shouldBe true
        }
    }

    /**
     * A hidden, cleared nested alternative must not invalidate the selected one.
     */
    @Test
    fun `exclude a hidden nested alternative from validation after switching`() {
        val initial = PaymentMethod.newBuilder().setBankAccount(account("123")).build()
        val form = paymentForm(initial)
        val bank = PaymentMethodDef.method.fields.first { it.name == "bank_account" }
        val card = PaymentMethodDef.method.fields.first { it.name == "payment_card" }
        lateinit var oneof: OneOfFieldsScope<PaymentMethod>
        lateinit var input: TrimmingInputField
        lateinit var cardField: FormFieldScope<PaymentCardNumber>

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    oneof = this
                    if (selectedField.value == bank) {
                        MessageForm(PaymentMethodDef.bankAccount, BankAccount::newBuilder) {
                            input = TrimmingInputField(BankAccountDef.number) {
                                onValidate = { "Invalid account number." }
                            }
                        }
                    } else {
                        Field(PaymentMethodDef.paymentCard) { cardField = this }
                    }
                }
            }
        }) { scene ->
            input.enterText("invalid")
            scene.render()
            form.valid.value shouldBe false

            oneof.selectedField.value = card
            scene.render()
            val cardNumber = PaymentCardNumber.newBuilder().setValue("1234").vBuild()
            cardField.fieldValue.value = cardNumber
            scene.render()

            form.valid.value shouldBe true
            form.value.value?.paymentCard shouldBe cardNumber

            form.updateValidationDisplay()
            scene.render()
            form.valid.value shouldBe true
            form.value.value?.paymentCard shouldBe cardNumber
        }
    }

    /**
     * Two active declarations must fail instead of repeatedly overwriting shared props.
     */
    @Test
    fun `reject simultaneous editors for the same field`() {
        val form = accountForm()

        shouldThrow<IllegalArgumentException> {
            inScene({
                form.Content {
                    StringField(BankAccountDef.number) { label = "First" }
                    StringField(BankAccountDef.number) { label = "Second" }
                }
            }) {}
        }
    }

    /**
     * Shared defaults must be available before constructing a component.
     */
    private companion object {

        /**
         * Installs the application once for this test JVM.
         */
        @JvmStatic
        @BeforeAll
        fun setUpApplication() {
            TestApplication.install()
        }
    }
}
