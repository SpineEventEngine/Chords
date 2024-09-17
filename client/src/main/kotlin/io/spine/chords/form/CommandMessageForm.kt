/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.spine.chords.appshell.app
import io.spine.chords.AbstractComponentCompanion
import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.chords.ComponentProps
import io.spine.chords.EventSubscription
import io.spine.chords.appshell.client
import io.spine.protobuf.ValidatingBuilder
import kotlinx.coroutines.TimeoutCancellationException

/**
 * A form that allows entering a value of a command message and posting
 * the respective command.
 *
 * It can be used in the same way as the [MessageForm] class, and adds
 * the [postCommand] function, which post the command with the entered field
 * values, and awaits for receiving the feedback upon its processing.
 *
 * @param C
 *         a type of the command message being edited with the form.
 */
public class CommandMessageForm<C : CommandMessage> :
    MessageForm<C>() {

    /**
     * Form instance declaration and creation API.
     */
    public companion object : AbstractComponentCompanion({
        CommandMessageForm<CommandMessage>()
    }) {

        /**
         * Declares a `CommandMessageForm` instance, which is not bound to
         * a parent form automatically, and can be bound to the respective data
         * field manually as needed.
         *
         * The form's content that is specified with the [content] parameter is
         * expected to include field editors for all command message's fields,
         * which are required to create a valid value of type [C].
         *
         * @param C
         *         a type of the command being edited with the form.
         * @param B
         *         a type of the command message builder.
         * @param builder
         *         a lambda that should create and return a new builder for
         *         a command of type [C].
         * @param value
         *         the command message value to be edited within the form.
         * @param props
         *         a lambda that can set any additional props on the form.
         * @param content
         *         a form's content, which can contain an arbitrary layout along
         *         with field editor declarations.
         * @return a form's instance that has been created for this
         *         declaration site.
         */
        @Composable
        public operator fun <C : CommandMessage, B: ValidatingBuilder<out C>> invoke(
            builder: () -> B,
            value: MutableState<C?> = mutableStateOf(null),
            props: ComponentProps<CommandMessageForm<C>> = ComponentProps {},
            content: @Composable FormPartScope<C>.() -> Unit
        ): CommandMessageForm<C> = Multipart(builder, value, props) {
            FormPart(content)
        }

        /**
         * Declares a multipart `CommandMessageForm` instance, which is not
         * bound to a parent form automatically, and can be bound to
         * the respective data field manually as needed.
         *
         * The form's content that is specified with the [content] parameter
         * should specify each form's part with using
         * [FormPart][MultipartFormScope.FormPart] declarations, which, in turn,
         * should contain the respective field editors.
         *
         * @param C
         *         a type of the command message being edited with the form.
         * @param B
         *         a type of the command message builder.
         * @param value
         *         the command message value to be edited within the form.
         * @param builder
         *         a lambda that should create and return a new builder for
         *         a message of type [M].
         * @param props
         *         a lambda that can set any additional props on the form.
         * @param content
         *         a form's content, which can contain an arbitrary layout along
         *         with field editor declarations.
         * @return a form's instance that has been created for this
         *         declaration site.
         */
        @Composable
        public fun <C : CommandMessage, B: ValidatingBuilder<out C>> Multipart(
            builder: () -> B,
            value: MutableState<C?> = mutableStateOf(null),
            props: ComponentProps<CommandMessageForm<C>> = ComponentProps {},
            content: @Composable MultipartFormScope<C>.() -> Unit
        ): CommandMessageForm<C> = createAndRender({
            this.value = value
            this.builder = builder as () -> ValidatingBuilder<C>
            multipartContent = content
            props.run { configure() }
        }) {
            Content()
        }

        /**
         * Creates a [CommandMessageForm] instance without rendering it in
         * the composable content with the same call.
         *
         * This method can be used to create a form's instance outside
         * a composable context, and render it separately. A form's instance
         * created in this way, has to be rendered  explicitly by calling either
         * its [Content] method (for single-part forms) or its
         * [MultipartContent] method (for rendering a multipart form) in
         * a composable context where it needs to be displayed.
         *
         * @param C
         *         a type of the command message being edited with the form.
         * @param B
         *         a type of the command message builder.
         * @param builder
         *         a lambda that should create and return a new builder for
         *         a message of type [M].
         * @param value
         *         the command message value to be edited within the form.
         * @param onBeforeBuild
         *         a lambda that allows to amend the command message
         *         after any valid field is entered to it.
         * @param props
         *         a lambda that can set any additional props on the form.
         * @return a form's instance that has been created for this
         *         declaration site.
         */
        public fun <C : CommandMessage, B: ValidatingBuilder<out C>> create(
            builder: () -> B,
            value: MutableState<C?> = mutableStateOf(null),
            onBeforeBuild: B.() -> Unit = {},
            props: ComponentProps<CommandMessageForm<C>> = ComponentProps {}
        ): CommandMessageForm<C> =
            super.create(null) {
                this.value = value
                this.builder = builder as () -> ValidatingBuilder<C>
                this.onBeforeBuild = onBeforeBuild as ValidatingBuilder<out C>.() -> Unit
                props.run { configure() }
            }
    }

    /**
     * A function, which, given a command message that is about to be posted,
     * should subscribe to a respective event that is expected to arrive in
     * response to handling that command.
     */
    public lateinit var eventSubscription: (C) -> EventSubscription<out EventMessage>

    override fun initialize() {
        super.initialize()
        check(this::eventSubscription.isInitialized) {
            "CommandMessageForm's `eventSubscription` property must " +
            "be specified."
        }
    }

    /**
     * Posts the command based on all currently entered data and awaits
     * the feedback upon processing the command.
     *
     * Here's a more detailed description about the sequence of actions
     * performed by this method:
     * - Validates the data entered in the form and builds the respective
     *   command message. In case if any validation errors are encountered, this
     *   method skips further stages, and just makes the respective validation
     *   errors to be displayed.
     * - Posts the command that was validated and built.
     * - Awaits for an event that should arrive upon successful handling of
     *   the command, as defined by the [eventSubscription]
     *   constructor's parameters.
     * - If the event doesn't arrive in a predefined timeout that is considered
     *   an adequate delay from user's perspective, this method
     *   throws [TimeoutCancellationException].
     *
     * @return `true` if the command was successfully built without any
     *         validation errors, and `false` if the command message could not
     *         be successfully built from the currently entered data (validation
     *         errors are displayed to the user in this case).
     * @throws TimeoutCancellationException
     *         if the event doesn't arrive within a reasonable timeout defined
     *         by the implementation.
     */
    public suspend fun postCommand(): Boolean {
        updateValidationDisplay(true)
        if (!valueValid.value) {
            return false
        }
        val command = value.value
        check(command != null) {
            "CommandMessageForm's value should be not null since it was just " +
            "checked to be valid within postCommand."
        }
        return try {
            val subscription = eventSubscription(command)
            app.client.command(command)
            subscription.awaitEvent()
            true
        } catch (
            @Suppress(
                // Using a defensive wide-scope catch to cover any message
                // creation failures.
                "TooGenericExceptionCaught",
                // TODO:2023-09-22:dmitry.pikhulya: handle server communication errors
                //                                  https://github.com/Projects-tm/1DAM/issues/17
                "SwallowedException"
            )
            e: Exception
        ) {
            false
        }
    }
}
