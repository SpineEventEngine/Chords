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

package io.spine.chords.client.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.google.protobuf.Message
import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.chords.client.EventSubscription
import io.spine.chords.client.form.CommandMessageForm
import io.spine.chords.core.layout.AbstractWizardPage
import io.spine.chords.core.layout.Wizard
import io.spine.chords.proto.form.FormFieldsScope
import io.spine.chords.proto.form.FormPartScope
import io.spine.chords.proto.form.MessageForm
import io.spine.chords.proto.form.ValidationDisplayMode.MANUAL
import io.spine.chords.runtime.MessageField
import io.spine.protobuf.ValidatingBuilder

/**
 * A kind of [Wizard], which allows creating a command message and posting
 * the respective command.
 *
 * Its usage is similar to [Wizard], but has some specifics:
 * - In order to use [CommandWizard], the respective command message has to be
 *   structured respectively. All of its fields have to be split into meaningful
 *   sub-messages. Each of these sub-messages would have a respective field in
 *   the command message, and would be edited in a corresponding separate
 *   wizard's page (one wizard's page per one command's sub-message).
 * - The [createPages] method of this class has to be implemented to return
 *   subclasses of [CommandWizardPage] instead of
 *   a generic [WizardPage][io.spine.chords.core.layout.WizardPage].
 * - The content of each page implemented in this way is automatically included
 *   into a form that composes the respective message's field. The context of
 *   that form is passed as the [FormPartScope] receiver of the
 *   [page][CommandWizardPage]'s [content][CommandWizardPage.content] method.
 * - This means that you can place respective [Field][FormFieldsScope.Field]
 *   declarations right in the [content][CommandWizardPage.content] method.
 *
 * @param C A type of the command message constructed in the wizard.
 * @param B A type of the command message builder.
 */
@Stable
public abstract class CommandWizard<C : CommandMessage, B : ValidatingBuilder<out C>> : Wizard() {

    internal val commandMessageForm: CommandMessageForm<C> =
        CommandMessageForm.create(
            { createCommandBuilder() },
            onBeforeBuild = { beforeBuild(it) }
        ) {
            validationDisplayMode = MANUAL
            eventSubscription = { subscribeToEvent(it) }
        }

    /**
     * Creates the pages that the wizard consists of.
     *
     * Each page should aim to construct a `Message` which is a field
     * of the `CommandMessage` constructed by this wizard.
     */
    protected abstract override fun createPages():
            List<CommandWizardPage<out Message, out ValidatingBuilder<out Message>>>


    /**
     * A function that should be implemented to create and return a new builder
     * for the command message of type [C].
     *
     * This function should in particular assign command ID field, and any
     * fields that are not edited in the wizard, but need to be specified in
     * a properly built command.
     */
    protected abstract fun createCommandBuilder(): B

    /**
     * A function, which, given a command message that is about to be posted,
     * should subscribe to a respective event that is expected to arrive in
     * response to handling that command.
     *
     * @param command
     *         a command, which is going to be posted.
     * @return a subscription to the event that is expected to arrive in response
     *         to handling [command]
     */
    protected abstract fun subscribeToEvent(command: C): EventSubscription<out EventMessage>

    /**
     * Allows to programmatically amend the command message builder before
     * the command is built.
     *
     * This function is invoked upon every attempt to build the command edited
     * in the wizard, which happens when any command's field is edited by the
     * user. When this function is invoked, the command builder's fields have
     * already been set from all form's field editors, which currently have
     * valid values. Note that there is no guarantee that the command message
     * that is about to be built is going to be valid.
     *
     * For example, if we wanted to set command's `field1` and
     * `field2` explicitly when the form builds a `MyMessage` value, this could
     * be done like this:
     * ```
     *     class WizardImpl: CommandWizard(...) {
     *
     *         override fun beforeBuild(builder: MyMessage.Builder) {
     *             builder.field1 = field1Value
     *             builder.field2 = field2Value
     *         }
     *     }
     * ```
     */
    protected open fun beforeBuild(builder: B) {}

    override suspend fun submit(): Boolean {
        return commandMessageForm.postCommand()
    }
}

/**
 * A base class for a page in `CommandWizard`, which helps fill in a message
 * that constitutes the content of one the command message's fields.
 *
 * @param M A type of the command's field edited in this page.
 * @param B A builder type of the command's field edited in this page.
 *
 * @param wizard The wizard that this page is a part of.
 * @param commandField The command's field whose content is edited in this page.
 * @param builder A function that should create and return a builder for message
 *   a with a type of [M].
 */
public abstract class CommandWizardPage<M : Message, B : ValidatingBuilder<out M>>(
    protected override val wizard: CommandWizard<out CommandMessage,
            out ValidatingBuilder<out CommandMessage>>,
    private val commandField: MessageField<out CommandMessage, M>,
    private val builder: () -> B
): AbstractWizardPage(wizard) {
    private lateinit var pageForm: MessageForm<M>

    @Composable
    override fun content() {
        // `CommandMessageForm`'s type param is in+out, and it's logically just
        // out in `CommandWizard`.
        @Suppress("UNCHECKED_CAST")
        val commandMessageForm = wizard.commandMessageForm as CommandMessageForm<CommandMessage>
        commandMessageForm.MultipartContent {
            FormPart(showPart = { show() }) {
                // The message type param is in+out, and it's just out
                // for commandField.
                @Suppress("UNCHECKED_CAST")
                pageForm = MessageForm(
                    commandField as MessageField<CommandMessage, M>,
                    this@CommandWizardPage.builder,
                    props = { validationDisplayMode = MANUAL }
                ) {
                    content()
                }
            }
        }
    }

    /**
     * The composable content that should include the fields
     * ([Field][FormFieldsScope.Field] declarations) for a part of command's
     * message that corresponds to this page.
     */
    @Composable
    protected abstract fun FormPartScope<M>.content()

    override fun show() {
        super.show()
        pageForm.focus()
    }

    override fun validate(): Boolean {
        pageForm.updateValidationDisplay(true)
        return pageForm.valueValid.value
    }
}
