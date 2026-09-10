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

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.chords.proto.TestApplication
import io.spine.chords.proto.form.given.MessageFormSpecEnv.ObserveDirty
import io.spine.chords.proto.form.given.MessageFormSpecEnv.TrimmingInputField
import io.spine.chords.proto.form.given.MessageFormSpecEnv.account
import io.spine.chords.proto.form.given.MessageFormSpecEnv.accountForm
import io.spine.chords.proto.form.given.MessageFormSpecEnv.inScene
import io.spine.chords.proto.form.given.MessageFormSpecEnv.paymentForm
import io.spine.chords.proto.value.money.BankAccount
import io.spine.chords.proto.value.money.BankAccountDef
import io.spine.chords.proto.value.money.PaymentCardNumber
import io.spine.chords.proto.value.money.PaymentMethod
import io.spine.chords.proto.value.money.PaymentMethodDef
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests editing through the existing form dirty-state API.
 */
@DisplayName("`MessageForm` should")
internal class MessageFormSpec {

    /**
     * Restoring the original value clears dirty state after editing or deletion.
     */
    @Test
    fun `track editing clearing and restoring a prefilled field`() {
        val form = accountForm(account("123"))
        lateinit var field: FormFieldScope<String>

        inScene({
            form.Content {
                Field(BankAccountDef.number) { field = this }
            }
        }) { scene ->
            form.dirty shouldBe false
            field.fieldValue.value shouldBe "123"

            field.fieldValue.value = "456"
            scene.render()
            form.dirty shouldBe true

            field.fieldValue.value = null
            scene.render()
            form.dirty shouldBe true

            field.fieldValue.value = "123"
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Defaults belong to the starting input even when no initial message exists.
     */
    @Test
    fun `retain a field default and user edits across recomposition`() {
        val form = accountForm()
        val defaultNumber = mutableStateOf("123")
        lateinit var field: FormFieldScope<String>

        inScene({
            form.Content {
                Field(BankAccountDef.number, defaultNumber.value) { field = this }
            }
        }) { scene ->
            form.dirty shouldBe false
            val originalState = field.fieldValue
            field.fieldValue.value = "456"
            defaultNumber.value = "789"
            scene.render()

            field.fieldValue shouldBeSameInstanceAs originalState
            field.fieldValue.value shouldBe "456"
            form.dirty shouldBe true

            field.fieldValue.value = "123"
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * A null parsed value must not hide an invalid or partially entered input.
     */
    @Test
    fun `clear dirty state when invalid input is removed`() {
        val form = accountForm()
        lateinit var field: FormFieldScope<String>

        inScene({
            form.Content {
                Field(BankAccountDef.number) { field = this }
            }
        }) { scene ->
            form.dirty shouldBe false
            field.fieldValueValid.value = false
            scene.render()
            form.dirty shouldBe true

            field.fieldValueValid.value = true
            field.notifyDirtyStateChanged(true)
            scene.render()
            form.dirty shouldBe true

            field.notifyDirtyStateChanged(false)
            scene.render()
            form.dirty shouldBe false

            field.fieldValue.value = "123"
            scene.render()
            form.dirty shouldBe true
            field.fieldValue.value = null
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Editing one instance does not affect another form for the same message type.
     */
    @Test
    fun `keep changes independent between forms`() {
        val first = accountForm(account("123"))
        val second = accountForm(account("456"))
        lateinit var field: FormFieldScope<String>

        inScene({
            first.Content { Field(BankAccountDef.number) { field = this } }
            second.Content { Field(BankAccountDef.number) {} }
        }) { scene ->
            field.fieldValue.value = "456"
            scene.render()

            first.dirty shouldBe true
            second.dirty shouldBe false
        }
    }

    /**
     * Consumers can observe changes before the form first registers its fields.
     */
    @Test
    fun `update a composable observer when field input changes`() {
        val form = accountForm(account("123"))
        var observedChanges = false
        lateinit var field: FormFieldScope<String>

        inScene({
            ObserveDirty(form) { observedChanges = it }
            form.Content { Field(BankAccountDef.number) { field = this } }
        }) { scene ->
            scene.render()
            observedChanges shouldBe false
            field.fieldValue.value = "456"
            scene.render()
            observedChanges shouldBe true

            field.fieldValue.value = "123"
            scene.render()
            observedChanges shouldBe false
        }
    }

    /**
     * Both parsed values and partial input flow through the same callback.
     */
    @Test
    fun `report edits through the existing dirty callback`() {
        val form = accountForm(account("123"))
        val dirtyStates = mutableListOf<Boolean>()
        form.onDirtyStateChange = {
            form.dirty shouldBe it
            dirtyStates += it
        }
        lateinit var field: FormFieldScope<String>

        inScene({
            form.Content { Field(BankAccountDef.number) { field = this } }
        }) { scene ->
            dirtyStates shouldBe emptyList()

            field.fieldValue.value = "456"
            scene.render()
            dirtyStates.isNotEmpty() shouldBe true
            dirtyStates.all { it } shouldBe true
            dirtyStates.clear()

            field.fieldValue.value = null
            field.notifyDirtyStateChanged(false)
            scene.render()
            dirtyStates.isNotEmpty() shouldBe true
            dirtyStates.all { it } shouldBe true
            form.dirty shouldBe true
            dirtyStates.clear()

            field.fieldValue.value = "123"
            field.notifyDirtyStateChanged(true)
            scene.render()
            dirtyStates.isNotEmpty() shouldBe true
            dirtyStates.any { it } shouldBe false
            form.dirty shouldBe false
        }
    }

    /**
     * A part opened after validation has cleared the output still uses the initial message.
     */
    @Test
    fun `initialize a later form part from the original message`() {
        val initial = PaymentMethod.newBuilder().setBankAccount(account("123")).build()
        val form = paymentForm(initial)
        val showPart = mutableStateOf(false)
        lateinit var field: FormFieldScope<BankAccount>

        inScene({
            form.MultipartContent {
                if (showPart.value) {
                    FormPart {
                        OneOfFields(PaymentMethodDef.method) {
                            Field(PaymentMethodDef.bankAccount) { field = this }
                        }
                    }
                }
            }
        }) { scene ->
            form.value.value shouldBe null
            showPart.value = true
            scene.render()

            field.fieldValue.value shouldBe account("123")
            form.dirty shouldBe false
            field.fieldValue.value = account("456")
            scene.render()
            showPart.value = false
            scene.render()
            form.dirty shouldBe true
            showPart.value = true
            scene.render()
            field.fieldValue.value shouldBe account("456")
            field.fieldValue.value = account("123")
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Selecting an alternative is a change even before that alternative has a value.
     */
    @Test
    fun `track oneof selection and restoration`() {
        val initial = PaymentMethod.newBuilder().setBankAccount(account("123")).build()
        val form = paymentForm(initial)
        lateinit var oneof: OneOfFieldsScope<PaymentMethod>
        lateinit var bankField: FormFieldScope<BankAccount>
        lateinit var cardField: FormFieldScope<PaymentCardNumber>

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    oneof = this
                    Field(PaymentMethodDef.bankAccount) { bankField = this }
                    Field(PaymentMethodDef.paymentCard) { cardField = this }
                }
            }
        }) { scene ->
            form.dirty shouldBe false
            oneof.selectedField.value = PaymentMethodDef.method.fields
                .first { it.name == "payment_card" }
            scene.render()
            cardField.fieldValue.value shouldBe null
            form.dirty shouldBe true

            oneof.selectedField.value = PaymentMethodDef.method.fields
                .first { it.name == "bank_account" }
            bankField.fieldValue.value = account("123")
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Cleared oneof alternatives must not retain dirty state from their old input.
     */
    @Test
    fun `clear dirty state when an initially unselected oneof is restored`() {
        val form = paymentForm()
        lateinit var oneof: OneOfFieldsScope<PaymentMethod>
        lateinit var field: FormFieldScope<PaymentCardNumber>

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    oneof = this
                    Field(PaymentMethodDef.paymentCard) { field = this }
                }
            }
        }) { scene ->
            form.dirty shouldBe false
            field.fieldValueValid.value = false
            field.notifyDirtyStateChanged(true)
            scene.render()
            form.dirty shouldBe true

            oneof.selectedField.value = null
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * An incomplete nested value can fail validation before any edits are made.
     */
    @Test
    fun `ignore initial nested validation and track subsequent edits`() {
        val initial = PaymentMethod.newBuilder().setBankAccount(account("")).buildPartial()
        val form = paymentForm(initial)
        lateinit var field: FormFieldScope<String>

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    MessageForm(PaymentMethodDef.bankAccount, BankAccount::newBuilder) {
                        Field(BankAccountDef.number) { field = this }
                    }
                }
            }
        }) { scene ->
            scene.render()
            form.value.value shouldBe null
            form.dirty shouldBe false

            field.fieldValueValid.value = false
            field.notifyDirtyStateChanged(true)
            scene.render()
            form.dirty shouldBe true

            field.fieldValueValid.value = true
            field.notifyDirtyStateChanged(false)
            field.fieldValue.value = null
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Formatting differences are clean when the parsed value equals the initial value.
     */
    @Test
    fun `compare parsed values when the text formatting differs`() {
        val form = accountForm(account("123"))
        val input = TrimmingInputField()

        inScene({
            form.Content { input.ContentWithinField(BankAccountDef.number) }
        }) { scene ->
            form.dirty shouldBe false
            input.enterText("123")
            form.dirty shouldBe false

            input.enterText("456")
            scene.render()
            form.dirty shouldBe true

            input.enterText(" 123")
            form.dirty shouldBe false
            input.value.value shouldBe "123"
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Some editors report initial presence from a composition side effect.
     */
    @Test
    fun `ignore initial editor notifications`() {
        val form = accountForm(account("123"))
        var firstComposition = true
        lateinit var field: FormFieldScope<String>

        inScene({
            form.Content {
                Field(BankAccountDef.number) {
                    field = this
                    SideEffect {
                        if (firstComposition) {
                            notifyDirtyStateChanged(true)
                            firstComposition = false
                        }
                    }
                }
            }
        }) { scene ->
            form.dirty shouldBe false
            field.fieldValue.value = null
            scene.render()
            form.dirty shouldBe true
        }
    }

    /**
     * Presence changes must still select an optional message after earlier edits.
     */
    @Test
    fun `track optional message selection and clearing`() {
        val form = accountForm()
        form.required = false

        inScene({ form.Content { Field(BankAccountDef.number) {} } }) { scene ->
            form.dirty shouldBe false
            form.enteringNonNullValue.value = true
            scene.render()
            form.dirty shouldBe true
            form.enteringNonNullValue.value = false
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Clearing a nested editor must not undo the user's oneof selection.
     */
    @Test
    fun `switch away from a prefilled nested form`() {
        val initial = PaymentMethod.newBuilder().setBankAccount(account("123")).build()
        val form = paymentForm(initial)
        lateinit var oneof: OneOfFieldsScope<PaymentMethod>
        val input = TrimmingInputField()

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    oneof = this
                    MessageForm(PaymentMethodDef.bankAccount, BankAccount::newBuilder) {
                        input.ContentWithinField(BankAccountDef.number)
                    }
                    Field(PaymentMethodDef.paymentCard) {}
                }
            }
        }) { scene ->
            form.dirty shouldBe false
            val card = PaymentMethodDef.method.fields.first { it.name == "payment_card" }
            oneof.selectedField.value = card
            scene.render()
            oneof.selectedField.value shouldBe card
            form.dirty shouldBe true
        }
    }

    /**
     * Automatic optional-message selection must not outlive the input that caused it.
     */
    @Test
    fun `clear dirty state after typing and deleting in an optional form`() {
        val form = accountForm()
        form.required = false
        val input = TrimmingInputField()

        inScene({
            form.Content { input.ContentWithinField(BankAccountDef.number) }
        }) { scene ->
            form.dirty shouldBe false
            input.enterText("123")
            scene.render()
            form.enteringNonNullValue.value shouldBe true
            form.dirty shouldBe true

            input.enterText("")
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Restoring nested values must reach the parent's existing dirty callback.
     */
    @Test
    fun `clear parent dirty state when nested input is restored`() {
        val initial = PaymentMethod.newBuilder().setBankAccount(account("123")).build()
        val form = paymentForm(initial)
        val dirtyStates = mutableListOf<Boolean>()
        form.onDirtyStateChange = { dirtyStates += it }
        val input = TrimmingInputField()

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    MessageForm(PaymentMethodDef.bankAccount, BankAccount::newBuilder) {
                        input.ContentWithinField(BankAccountDef.number)
                    }
                }
            }
        }) { scene ->
            form.dirty shouldBe false
            input.enterText("456")
            scene.render()
            form.dirty shouldBe true

            input.enterText("123")
            scene.render()
            form.dirty shouldBe false
            dirtyStates.last() shouldBe false
        }
    }

    /**
     * Empty parsed text is equivalent to a cleared editor, even with visible whitespace.
     */
    @Test
    fun `restore an initially empty value with equivalent text`() {
        val form = accountForm(account(""))
        val input = TrimmingInputField()

        inScene({
            form.Content { input.ContentWithinField(BankAccountDef.number) }
        }) { scene ->
            input.enterText("123")
            scene.render()
            form.dirty shouldBe true

            input.enterText(" ")
            scene.render()
            input.value.value shouldBe ""
            form.dirty shouldBe false
        }
    }

    /**
     * Restoring optional input also restores the original message selection.
     */
    @Test
    fun `restore a prefilled optional message after clearing it`() {
        val form = accountForm(account("123"))
        form.required = false
        val input = TrimmingInputField()

        inScene({
            form.Content { input.ContentWithinField(BankAccountDef.number) }
        }) { scene ->
            form.dirty shouldBe false
            form.enteringNonNullValue.value = false
            scene.render()
            form.dirty shouldBe true

            input.enterText("123")
            scene.render()
            form.enteringNonNullValue.value shouldBe true
            form.dirty shouldBe false
        }
    }

    /**
     * Returning to a wizard page must preserve the original nested values.
     */
    @Test
    fun `restore nested input after leaving and returning to a form part`() {
        val initial = PaymentMethod.newBuilder().setBankAccount(account("123")).build()
        val form = paymentForm(initial)
        val showPart = mutableStateOf(true)
        lateinit var input: TrimmingInputField

        inScene({
            form.MultipartContent {
                if (showPart.value) {
                    FormPart {
                        OneOfFields(PaymentMethodDef.method) {
                            MessageForm(PaymentMethodDef.bankAccount, BankAccount::newBuilder) {
                                input = remember { TrimmingInputField() }
                                input.ContentWithinField(BankAccountDef.number)
                            }
                        }
                    }
                }
            }
        }) { scene ->
            input.enterText("456")
            scene.render()
            form.dirty shouldBe true

            showPart.value = false
            scene.render()
            showPart.value = true
            scene.render()
            input.value.value shouldBe "456"
            form.dirty shouldBe true

            input.enterText("123")
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Disabling an optional message discards its invalid input as well as its value.
     */
    @Test
    fun `clear dirty state when an optional message with invalid input is disabled`() {
        val form = accountForm()
        form.required = false
        val input = TrimmingInputField()
        input.onValidate = { "Invalid account number." }

        inScene({
            form.Content { input.ContentWithinField(BankAccountDef.number) }
        }) { scene ->
            input.enterText("invalid")
            scene.render()
            input.valid.value shouldBe false
            form.enteringNonNullValue.value shouldBe true
            form.dirty shouldBe true

            form.enteringNonNullValue.value = false
            scene.render()
            input.value.value shouldBe null
            input.valid.value shouldBe true
            form.dirty shouldBe false
        }
    }

    /**
     * A parent must observe an empty nested form throughout its clear notifications.
     */
    @Test
    fun `clear oneof selection before reporting cleared input`() {
        val initial = PaymentMethod.newBuilder().setBankAccount(account("123")).build()
        val form = paymentForm(initial)
        val input = TrimmingInputField()
        lateinit var oneof: OneOfFieldsScope<PaymentMethod>

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    oneof = this
                    MessageForm(PaymentMethodDef.bankAccount, BankAccount::newBuilder) {
                        input.ContentWithinField(BankAccountDef.number)
                    }
                }
            }
        }) { scene ->
            var notified = false
            form.onDirtyStateChange = {
                oneof.selectedField.value shouldBe null
                notified = true
            }

            form.clear()
            scene.render()

            notified shouldBe true
            oneof.selectedField.value shouldBe null
            input.value.value shouldBe null
            form.dirty shouldBe true
        }
    }

    /**
     * Unchecking a message restores its initial absence after choosing an alternative.
     */
    @Test
    fun `restore an absent optional message after selecting a oneof`() {
        val form = paymentForm()
        form.required = false
        lateinit var oneof: OneOfFieldsScope<PaymentMethod>

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    oneof = this
                    Field(PaymentMethodDef.bankAccount) {}
                }
            }
        }) { scene ->
            oneof.selectedField.value = PaymentMethodDef.method.fields
                .first { it.name == "bank_account" }
            scene.render()
            form.enteringNonNullValue.value shouldBe true
            form.dirty shouldBe true

            form.enteringNonNullValue.value = false
            scene.render()
            oneof.selectedField.value shouldBe null
            form.value.value shouldBe null
            form.dirty shouldBe false
        }
    }

    /**
     * A reverted checkbox change must not affect later typing and deletion.
     */
    @Test
    fun `restore empty input after toggling optional selection back`() {
        val form = accountForm()
        form.required = false
        val input = TrimmingInputField()

        inScene({
            form.Content { input.ContentWithinField(BankAccountDef.number) }
        }) { scene ->
            form.enteringNonNullValue.value = true
            scene.render()
            form.enteringNonNullValue.value = false
            scene.render()
            form.dirty shouldBe false

            input.enterText("123")
            scene.render()
            input.enterText("")
            scene.render()
            form.dirty shouldBe false
        }
    }

    /**
     * Switching alternatives clears input without replacing its original baseline.
     */
    @Test
    fun `restore a conditional nested alternative after switching away`() {
        val initial = PaymentMethod.newBuilder().setBankAccount(account("123")).build()
        val form = paymentForm(initial)
        lateinit var oneof: OneOfFieldsScope<PaymentMethod>
        lateinit var input: TrimmingInputField
        val bank = PaymentMethodDef.method.fields.first { it.name == "bank_account" }
        val card = PaymentMethodDef.method.fields.first { it.name == "payment_card" }

        inScene({
            form.Content {
                OneOfFields(PaymentMethodDef.method) {
                    oneof = this
                    if (selectedField.value == bank) {
                        MessageForm(PaymentMethodDef.bankAccount, BankAccount::newBuilder) {
                            input = remember { TrimmingInputField() }
                            input.ContentWithinField(BankAccountDef.number)
                        }
                    } else {
                        Field(PaymentMethodDef.paymentCard) {}
                    }
                }
            }
        }) { scene ->
            oneof.selectedField.value = card
            scene.render()
            oneof.selectedField.value shouldBe card
            oneof.selectedField.value = bank
            scene.render()
            input.value.value shouldBe null
            form.dirty shouldBe true

            input.enterText("123")
            scene.render()
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
