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

package io.spine.chords.client.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import io.spine.base.CommandMessage
import io.spine.chords.client.CommandConsequencesScope
import io.spine.chords.client.form.CommandMessageForm
import io.spine.chords.client.layout.ModalCommandConsequences.Companion.consequences
import io.spine.chords.core.layout.Dialog
import io.spine.chords.core.layout.SubmitOrCancelDialog
import io.spine.chords.proto.form.FormFieldsScope
import io.spine.chords.proto.form.FormPartScope
import io.spine.chords.proto.form.ValidationDisplayMode.MANUAL
import io.spine.protobuf.ValidatingBuilder

/**
 * A [Dialog] designed to create or modify a command message,
 * and post the respective command upon finish.
 *
 * @param C A type of the command message constructed in the dialog.
 * @param B A type of the command message builder.
 */
public abstract class CommandDialog<C : CommandMessage, B : ValidatingBuilder<C>>
    : SubmitOrCancelDialog() {

    /**
     * A lambda, which can optionally be specified to customize the way how
     * [ModalCommandConsequences] instance is created.
     *
     * In most cases this property would not need to be customized:
     *  - The dialog-specific command consequences should be specified by
     *    implementing the [commandConsequences] method.
     *  - If you need to customize the
     *    [predefined consequences][ModalCommandConsequences.predefinedConsequences],
     *    which are implied by [ModalCommandConsequences] (for common things
     *    like handling errors or displaying command posting progress), you can
     *    customize this on an application level using the
     *    ["shared defaults"][Application.sharedDefaults] feature.
     *
     *  In cases when the above cases are not enough, and you need to customize
     *  how `ModelCommandConsequences` is instantiated or configured on a
     *  per-dialog basis, you can do this by specifying a respective lambda in
     *  this property. The lambda accepts a consequences configuration function
     *  and returns the respective [ModalCommandConsequences] created from that
     *  configuration function.
     */
    public var createCommandConsequences:
                (ModalCommandConsequencesScope<C>.() -> Unit) -> ModalCommandConsequences<C> =
        { consequences(postingState, { close() }, it) }

    private var postingState = mutableStateOf(false)

    /**
     * The [CommandMessageForm] used as a container for the message
     * field editors.
     */
    private lateinit var commandMessageForm: CommandMessageForm<C>

    override fun updateProps() {
        super.updateProps()
        submitting = postingState.value
    }

    /**
     * Creates and renders the [commandMessageForm], and then delegates the
     * rendering of the actual form's content to the [content] method.
     */
    @Composable
    protected final override fun contentSection() {
        commandMessageForm = CommandMessageForm(
            ::createCommandBuilder,
            onBeforeBuild = ::beforeBuild,
            props = {
                validationDisplayMode = MANUAL
                createCommandConsequences = this@CommandDialog.createCommandConsequences
                commandConsequences = {
                    (this as ModalCommandConsequencesScope<C>).run {
                        commandConsequences()
                    }
                }
                enabled = !submitting
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = CenterHorizontally
            ) {
                content()
            }
        }

        LaunchedEffect(Unit) {
            commandMessageForm.focus()
        }
    }

    /**
     * The composable content that should include the fields
     * ([Field][FormFieldsScope.Field] declarations) for editing
     * the command message of type [C].
     */
    @Composable
    protected abstract fun FormPartScope<C>.content()

    /**
     * Creates a builder for the command message of type [C].
     *
     * May pre-fill the builder with the default values,
     * or those required in terms of the domain language,
     * but not available for modification within the dialog UI.
     */
    protected abstract fun createCommandBuilder(): B

    /**
     * A function, which, should register handlers for consequences of
     * command [C] posted by the dialog.
     *
     * The command, which is going to be posted and whose consequence handlers
     * should be registered can be obtained from the
     * [command][CommandConsequencesScope.command] property available in the
     * function's scope, and handlers can be registered using the
     * [`onXXX`][CommandConsequencesScope] functions available in the
     * function's scope.
     *
     * Here's an example of a typical simplest impmelemntation:
     * ```
     *     override fun ModalCommandConsequencesScope<ImportItem>.commandConsequences() {
     *         closeOnEvent(
     *             ItemImported::class.java,
     *             ItemImported.Field.itemId(),
     *             command.itemId
     *         )
     *     }
     * ```
     *
     * Event subscriptions made by this function are automatically cancelled
     * when the dialog is closed. They can also be cancelled explicitly by
     * calling [cancelActiveSubscriptions] if they need to be canceled before
     * the dialog is closed.
     *
     * NOTE: the implementation of this function typically doesn't have to
     * define handlers for consequences, which are needed for displaying of the
     * "posting" state or handling posting errors, since these consequences are
     * handled by predefined consequences specified in the
     * [predefinedConsequences][ModalCommandConsequences.predefinedConsequences]
     * property of [ModalCommandConsequences] instance provided by the
     * [createCommandConsequences] lambda. You can customize this behavior on
     * a per-instance or application-wide level. See [ModalCommandConsequences]
     * and [createCommandConsequences].
     *
     * @receiver [ModalCommandConsequencesScope], which provides an API for
     *   registering command's consequences.
     * @see submitContent
     * @see cancelActiveSubscriptions
     * @see ModalCommandConsequences
     * @see createCommandConsequences
     */
    protected abstract fun ModalCommandConsequencesScope<C>.commandConsequences()

    /**
     * Allows to programmatically amend the command message builder before
     * the command is built.
     *
     * This function is invoked upon every attempt to build the command edited
     * in the dialog. When this function is invoked, the command builder's
     * fields have already been set from all form's field editors,
     * which currently have valid values.
     */
    protected open fun beforeBuild(builder: B) {}

    /**
     * Cancels any active subscriptions made by [commandConsequences] and closes
     * the dialog.
     *
     * @see submitContent
     * @see commandConsequences
     */
    override fun close() {
        cancelActiveSubscriptions()
        super.close()
    }

    /**
     * Posts the command message [C] created in this dialog, and processes
     * the respective command's consequences specified in [commandConsequences].
     *
     * @see commandConsequences
     */
    protected override suspend fun submitContent() {
        commandMessageForm.updateValidationDisplay(true)
        if (!commandMessageForm.valueValid.value) {
            return
        }
        commandMessageForm.postCommand()
    }

    /**
     * Cancels any active event subscriptions that have been made by this
     * dialog's submission(s) up to now.
     *
     * @see submitContent
     * @see commandConsequences
     */
    protected fun cancelActiveSubscriptions() {
        commandMessageForm.cancelActiveSubscriptions()
    }
}
