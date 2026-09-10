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
import io.kotest.matchers.shouldBe
import io.spine.chords.proto.TestApplication
import io.spine.chords.proto.form.given.MessageFormFixtures.TrimmingInputField
import io.spine.chords.proto.form.given.MessageFormFixtures.account
import io.spine.chords.proto.form.given.MessageFormFixtures.accountForm
import io.spine.chords.proto.form.given.MessageFormFixtures.inScene
import io.spine.chords.proto.form.given.MessageFormFixtures.paymentForm
import io.spine.chords.proto.value.money.BankAccount
import io.spine.chords.proto.value.money.BankAccountDef
import io.spine.chords.proto.value.money.PaymentMethod
import io.spine.chords.proto.value.money.PaymentMethodDef
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies initial field values and late declarations after input changes.
 */
@DisplayName("`FormFieldsScope` should")
internal class FormFieldsScopeSpec {

    /**
     * A form-wide clear also applies to fields whose part has never been displayed.
     */
    @Test
    fun `keep a later form part cleared while preserving its initial values`() {
        val form = accountForm(account("123"))
        val showPart = mutableStateOf(false)
        lateinit var input: TrimmingInputField

        inScene({
            form.MultipartContent {
                if (showPart.value) {
                    FormPart { input = TrimmingInputField(BankAccountDef.number) }
                }
            }
        }) { scene ->
            form.clear()
            showPart.value = true
            scene.render()

            input.value.value shouldBe null
            form.dirty shouldBe true

            input.enterText("123")
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Defaults of fields first declared after a clear remain the original comparison values.
     */
    @Test
    fun `keep a later field default cleared until the user restores it`() {
        val form = accountForm()
        val showField = mutableStateOf(false)
        lateinit var field: FormFieldScope<String>

        inScene({
            form.Content {
                if (showField.value) {
                    Field(BankAccountDef.number, "123") { field = this }
                }
            }
        }) { scene ->
            form.clear()
            showField.value = true
            scene.render()

            field.fieldValue.value shouldBe null
            form.dirty shouldBe true

            field.fieldValue.value = "123"
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Neither a late oneof nor its nested editor may restore input discarded by clear.
     */
    @Test
    fun `keep a later nested oneof cleared and recognize restored original input`() {
        val initial = PaymentMethod.newBuilder().setBankAccount(account("123")).build()
        val form = paymentForm(initial)
        val showPart = mutableStateOf(false)
        lateinit var oneof: OneOfFieldsScope<PaymentMethod>
        lateinit var input: TrimmingInputField

        inScene({
            form.MultipartContent {
                if (showPart.value) {
                    FormPart {
                        OneOfFields(PaymentMethodDef.method) {
                            oneof = this
                            MessageForm(PaymentMethodDef.bankAccount, BankAccount::newBuilder) {
                                input = TrimmingInputField(BankAccountDef.number)
                            }
                        }
                    }
                }
            }
        }) { scene ->
            form.clear()
            showPart.value = true
            scene.render()

            oneof.selectedField.value shouldBe null
            input.value.value shouldBe null
            form.dirty shouldBe true

            input.enterText("123")
            scene.render()
            form.value.value shouldBe initial
            form.dirty shouldBe false
        }
    }

    /**
     * Returning to an absent optional message must not erase undisplayed defaults.
     */
    @Test
    fun `retain late defaults after optional selection is restored`() {
        val form = accountForm().apply { required = false }
        val showField = mutableStateOf(false)
        lateinit var field: FormFieldScope<String>

        inScene({
            form.Content {
                if (showField.value) {
                    Field(BankAccountDef.number, "123") { field = this }
                }
            }
        }) { scene ->
            form.enteringNonNullValue.value = true
            scene.render()
            form.enteringNonNullValue.value = false
            scene.render()
            showField.value = true
            scene.render()

            field.fieldValue.value shouldBe "123"
            form.value.value shouldBe null
            form.dirty shouldBe false
        }
    }

    /**
     * Clearing a displayed default by unchecking an optional form restores its absence.
     */
    @Test
    fun `ignore cleared field differences while an initially absent form is unselected`() {
        val form = accountForm().apply { required = false }

        inScene({ form.Content { Field(BankAccountDef.number, "123") {} } }) { scene ->
            form.enteringNonNullValue.value = true
            scene.render()
            form.enteringNonNullValue.value = false
            scene.render()

            form.value.value shouldBe null
            form.dirty shouldBe false
        }
    }

    /**
     * A late nested editor must compare earlier field edits with the original message.
     */
    @Test
    fun `keep original and current input distinct when a nested editor appears late`() {
        val initial = PaymentMethod.newBuilder().setBankAccount(account("123")).build()
        val form = paymentForm(initial)
        val showEditor = mutableStateOf(false)
        lateinit var field: FormFieldScope<BankAccount>
        lateinit var input: TrimmingInputField

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    Field(PaymentMethodDef.bankAccount) { field = this }
                    if (showEditor.value) {
                        MessageForm(PaymentMethodDef.bankAccount, BankAccount::newBuilder) {
                            input = TrimmingInputField(BankAccountDef.number)
                        }
                    }
                }
            }
        }) { scene ->
            field.fieldValue.value = account("456")
            scene.render()
            showEditor.value = true
            scene.render()
            input.value.value shouldBe "456"
            form.dirty shouldBe true

            input.enterText("123")
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Optional deselection must preserve undisplayed defaults throughout nested forms.
     */
    @Test
    fun `preserve nested late defaults after the optional parent is unchecked`() {
        val form = paymentForm().apply { required = false }
        val showField = mutableStateOf(false)
        lateinit var field: FormFieldScope<String>

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    MessageForm(PaymentMethodDef.bankAccount, BankAccount::newBuilder) {
                        if (showField.value) {
                            Field(BankAccountDef.number, "123") { field = this }
                        }
                    }
                }
            }
        }) { scene ->
            form.enteringNonNullValue.value = true
            scene.render()
            form.enteringNonNullValue.value = false
            scene.render()
            showField.value = true
            scene.render()

            field.fieldValue.value shouldBe "123"
            form.value.value shouldBe null
            form.dirty shouldBe false
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
