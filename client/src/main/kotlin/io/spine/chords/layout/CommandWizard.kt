/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import com.google.protobuf.Message
import io.spine.chords.form.FormFieldsScope
import io.spine.chords.form.MessageForm
import io.spine.chords.form.FormPartScope
import io.spine.chords.form.ValidationDisplayMode.MANUAL
import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.chords.EventSubscription
import io.spine.chords.form.CommandMessageForm
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
 *   a generic [WizardPage][io.spine.chords.layout.WizardPage].
 * - The content of each page implemented in this way is automatically included
 *   into a form that composes the respective message's field. The context of
 *   that form is passed as the [FormPartScope] receiver of the
 *   [page][CommandWizardPage]'s [content][CommandWizardPage.content] method.
 * - This means that you can place respective [Field][FormFieldsScope.Field]
 *   declarations right in the [content][CommandWizardPage.content] method.
 *
 * @param C
 *         a type of the command message constructed in the wizard.
 * @param B
 *         a type of the command message builder.
 */
@Stable
public abstract class CommandWizard<C : CommandMessage, B : ValidatingBuilder<out C>> : Wizard() {

    internal val commandMessageForm: CommandMessageForm<C> =
        CommandMessageForm.create({ createCommandBuilder() },
            onBeforeBuild = { beforeBuild(this) }) {
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
     * The page of this wizard on which the focus was the last time.
     */
    internal var lastFocusedPage:
            CommandWizardPage<out Message, out ValidatingBuilder<out Message>>? = null

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
     * The builder is passed as the receiver of this function,
     * so properties can be set and read without referring to the builder
     * explicitly. For example, if we wanted to set command's `field1` and
     * `field2` explicitly, this could be done like this:
     *
     * ```
     *     class WizardImpl: CommandWizard(...) {
     *
     *         override fun CommandMessage.Builder.onBeforeBuild() = {
     *             field1 = field1Value
     *             field2 = field2Value
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
    private var pageForm: MessageForm<M>? = null
        set(value) {
            if (field == null) {
                require(value != null)
                field = value
            } else {
                require(value == field)
            }
        }

    @Composable
    override fun content() {
        val commandMessageForm = wizard.commandMessageForm as CommandMessageForm<CommandMessage>
        commandMessageForm.MultipartContent {
            FormPart(showPart = { show() }) {
                Field(commandField as MessageField<CommandMessage, M>) {
                    if (pageForm == null) {
                        pageForm = MessageForm.create(fieldValue, this@CommandWizardPage.builder) {
                            validationDisplayMode = MANUAL
                        }
                    }
                    pageForm!!.Content {
                        content()
                    }
                }
            }
        }

        if (wizard.lastFocusedPage != this) {
            wizard.lastFocusedPage = this
            pageForm?.focus()
        }
    }

    /**
     * The composable content that should include the fields
     * ([Field][FormFieldsScope.Field] declarations) for a part of command's
     * message that corresponds to this page.
     */
    @Composable
    protected abstract fun FormPartScope<M>.content()

    override fun validate(): Boolean {
        val form = pageForm!!
        form.updateValidationDisplay(true)
        return form.valueValid.value
    }
}
