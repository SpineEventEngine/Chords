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

package io.spine.chords.proto.form.given

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.protobuf.Message
import io.spine.chords.core.ComponentSetup
import io.spine.chords.core.InputComponent
import io.spine.chords.core.InputField
import io.spine.chords.proto.form.FormFieldsScope
import io.spine.chords.proto.form.FormPartScope
import io.spine.chords.proto.form.MessageForm
import io.spine.chords.proto.form.MessageFormSetupBase
import io.spine.chords.proto.form.ValidationDisplayMode.MANUAL
import io.spine.chords.proto.value.money.BankAccount
import io.spine.chords.proto.value.money.PaymentMethod
import io.spine.chords.proto.value.money.PaymentMethodDef
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageFieldValue
import java.awt.EventQueue.invokeAndWait
import org.jetbrains.skia.Surface

/**
 * Supplies real message forms and a composition for change-tracking tests.
 */
internal object MessageFormFixtures {

    /**
     * Observes changes from a composable scope separate from the form's content.
     */
    @Composable
    fun ObserveDirty(form: MessageForm<*>, observe: (Boolean) -> Unit) {
        val dirty = form.dirty
        SideEffect { observe(dirty) }
    }

    /**
     * Creates an account value without requiring a complete form input.
     */
    fun account(number: String): BankAccount = BankAccount.newBuilder()
        .setNumber(number)
        .buildPartial()

    /**
     * Creates a form whose input can be invalid without displaying errors.
     */
    fun accountForm(initialValue: BankAccount? = null): MessageForm<BankAccount> =
        MessageForm.create(mutableStateOf(initialValue), BankAccount::newBuilder) {
            validationDisplayMode = MANUAL
        }

    /**
     * Creates a form with a message-valued oneof for selection and nesting cases.
     */
    fun paymentForm(initialValue: PaymentMethod? = null): MessageForm<PaymentMethod> =
        MessageForm.create(mutableStateOf(initialValue), PaymentMethod::newBuilder) {
            validationDisplayMode = MANUAL
        }

    /**
     * Exercises the real text-input path with equivalent parsed values.
     */
    class TrimmingInputField : InputField<String>() {

        /**
         * Exercises the same field-bound declarations as production input components.
         */
        companion object : ComponentSetup<TrimmingInputField>({ TrimmingInputField() })

        /**
         * Exercises input callbacks without text-field animations.
         */
        @Composable
        override fun content(): Unit = Unit

        /**
         * This fixture has no focusable UI; selection tests only need its input state.
         */
        override fun focus(): Unit = Unit

        /**
         * Applies text through the same validation and callbacks as typing.
         */
        fun enterText(text: String) {
            applyValue(text)
        }

        /**
         * Makes whitespace edits observable without changing the parsed value.
         */
        override fun parseValue(rawText: String): String = rawText.trim()
    }

    /**
     * Binds caller-owned input through the public Field API without automatic editor retention.
     */
    context(FormFieldsScope<M>)
    @Composable
    fun <M : Message, V : MessageFieldValue> InputComponent<V>.RenderWithinField(
        field: MessageField<M, V>
    ) {
        val input = this
        Field(field) {
            input.value = fieldValue
            input.valid = fieldValueValid
            input.externalValidationMessage = externalValidationMessage
            input.onDirtyStateChange = { notifyDirtyStateChanged(it) }
            input.required = fieldRequired
            input.enabled = fieldEnabled.value
            focusRequestDispatcher.handleFocusRequest = { input.focus() }
            registerFieldValueEditor(input)
            input.Content()
        }
    }

    /**
     * Observes form construction through the published subclass setup API.
     */
    class AccountFormSetup(onCreate: () -> Unit) :
        MessageFormSetupBase<BankAccount, MessageForm<BankAccount>>({
            onCreate()
            MessageForm()
        }) {

        /**
         * Declares an account editor inside a payment form.
         */
        context(FormFieldsScope<PaymentMethod>)
        @Composable
        operator fun invoke(
            content: @Composable FormPartScope<BankAccount>.() -> Unit
        ): MessageForm<BankAccount> = declareInstance(
            field = PaymentMethodDef.bankAccount,
            builder = BankAccount::newBuilder,
            content = content
        )
    }

    /**
     * Runs a form scenario without a visible window.
     */
    fun inScene(content: @Composable () -> Unit, test: (FormScene) -> Unit) {
        FormScene(content).use(test)
    }

    /**
     * Lets desktop snapshot notifications run between test actions and frames.
     */
    private fun <T> onUiThread(action: () -> T): T {
        var result: Result<T>? = null
        invokeAndWait { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }

    /**
     * Applies state changes to an off-screen composition of the form.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    class FormScene(content: @Composable () -> Unit) : AutoCloseable {

        /**
         * Provides the desktop composition locals used by message forms.
         */
        private val scene = onUiThread { ComposeScene() }

        /**
         * Supplies a canvas for advancing frames without showing a window.
         */
        private val surface = Surface.makeRasterN32Premul(1, 1)

        /**
         * Keeps frame timestamps increasing as the test applies edits.
         */
        private var frame = 0L

        init {
            try {
                onUiThread {
                    scene.setContent {
                        MaterialTheme { content() }
                    }
                }
                render()
            } catch (e: Exception) {
                close()
                throw e
            }
        }

        /**
         * Advances frames until recomposition and its effects have settled.
         */
        fun render() {
            repeat(30) {
                onUiThread {
                    Snapshot.sendApplyNotifications()
                    scene.render(surface.canvas, ++frame * 16_000_000L)
                }
                if (!scene.hasInvalidations()) {
                    return
                }
            }
            error("The form composition did not settle.")
        }

        /**
         * Returns visible text so tests can observe feedback without depending on its wording.
         */
        fun displayedText(): Set<String> = onUiThread {
            val pending = ArrayDeque(
                scene.roots
                    .map { it.semanticsOwner.rootSemanticsNode }
            )
            val text = mutableSetOf<String>()
            while (pending.isNotEmpty()) {
                val node = pending.removeFirst()
                node.config
                    .getOrNull(SemanticsProperties.Text)
                    .orEmpty()
                    .map { it.text }
                    .filter { it.isNotBlank() }
                    .forEach { text.add(it) }
                pending.addAll(node.children)
            }
            text
        }

        /**
         * Releases the composition and its drawing surface.
         */
        override fun close(): Unit = onUiThread {
            scene.close()
            surface.close()
        }
    }
}
