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

package io.spine.chords.client.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.spine.chords.core.appshell.app
import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.chords.core.ComponentProps
import io.spine.chords.client.EventSubscription
import io.spine.chords.client.appshell.client
import io.spine.chords.proto.form.FormPartScope
import io.spine.chords.proto.form.MessageForm
import io.spine.chords.proto.form.MessageFormSetupBase
import io.spine.chords.proto.form.MultipartFormScope
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
 * Please see the [MessageForm]'s documentation, which applies to
 * [CommandMessageForm] as well for the details on its usage. Besides, here's
 * an example of configuring `CommandMessageForm`, which allows the user
 * to enter username and password, and then post the respective command
 * (called `AuthorizeUser` here).
 *
 * ```
 * // The value returned by such `CommandMessageForm`'s declaration represents
 * // a `CommandMessageForm` instance, which was implicitly created and cached
 * // for this declaration specifically, so you can use its API as needed.
 * val form = CommandMessageForm({ AuthorizeUser.newBuilder() }) {
 *
 *     // It is perfectly valid to organize the actual field editors into any
 *     // layout that might be required.
 *     Column {
 *
 *          // Each field editor would typically be a class-based input
 *          // component, which is a subclass of
 *          // `io.spine.chords.core.InputComponent`, and would be associated
 *          // with some of the command message's fields, by passing the
 *          // respective command's field as
 *          // an `io.spine.chords.runtime.MessageField` instance.
 *          UserNameField(AuthorizeUserDef.userName)
 *          PasswordField(AuthorizeUserDef.password)
 *
 *          // You can implement the UI for posting the form with any component
 *          // that appears appropriate as long as the form's `postCommand`
 *          // method is invoked when the form needs to be posted.
 *          Button(
 *              onClick = { form.postCommand() }
 *          ) {
 *              Text("Login")
 *          }
 * }
 * ```
 *
 * The example above shows a simplest possible configuration of
 * `CommandMessageForm`, and whenever needed, you can also use other
 * customization options. For example, if you need to have the form display
 * pre-populated contents as defined by some non-default `CommandMessage`
 * instance, and also prevent form's validation errors from being displayed
 * live during editing (by triggering validation explicitly), you can do it
 * like this:
 *
 * ```
 * val authorizeUserCommand: MutableState<AuthorizeUser> =
 *     getPrepopulatedAuthorizeUserCommand()
 * val form = CommandMessageForm(
 *     builder = { AuthorizeUser.newBuilder() }
 *
 *     // Providing a `MutableState` based value in this way ensures that
 *     // the form uses current value found in `MutableState` AND that
 *     // the value in this `MutableState` is updated whenever it is edited
 *     // in the form.
 *     value = authorizeUserCommand,
 *
 *     props = {
 *
 *         // In this section you can configure the `CommandMessageForm`
 *         // component by specifying its property values just like you would
 *         // do if you were passing parameter values to a "traditional" Compose
 *         // function-based component.
 *
 *         validationDisplayMode = MANUAL
 *     }
 * ) {
 *
 *     Column {
 *          UserNameField(AuthorizeUserDef.userName)
 *          PasswordField(AuthorizeUserDef.password)
 *
 *          // We're invoking the form's `updateValidationDisplay()` method in
 *          // this way just as an example, and in practice it can be invoked
 *          // when needed to achieve the required validation behavior.
 *          Button(
 *              onClick = {
 *
 *                  // Manually triggers the form's field editors to display the
 *                  // validation messages that correspond to the current
 *                  // form's editing state.
 *                  form.updateValidationDisplay()
 *              }
 *          ) {
 *              Text("Login")
 *          }
 *
 *          Button(
 *              onClick = { form.postCommand() }
 *          ) {
 *              Text("Login")
 *          }
 * }
 * ```
 *
 * @param C A type of the command message being edited with the form.
 */
public class CommandMessageForm<C : CommandMessage> : MessageForm<C>() {
    public companion object :
        MessageFormSetupBase<CommandMessage, CommandMessageForm<CommandMessage>>(
            { CommandMessageForm() }
        ) {

        /**
         * Declares a `CommandMessageForm` instance, which is not bound to
         * a parent form automatically, and can be bound to the respective data
         * field manually as needed.
         *
         * The form's content that is specified with the [content] parameter is
         * expected to include field editors for all command message's fields,
         * which are required to create a valid value of type [C].
         *
         * @param C A type of the command being edited with the form.
         * @param B A type of the command message builder.
         *
         * @param builder A lambda that should create and return a new builder
         *   for a command of type [C].
         * @param value The command message value to be edited within the form.
         * @param onBeforeBuild A lambda that allows to amend the command
         *   message after any valid field is entered to it.
         * @param props A lambda that can set any additional props on the form.
         * @param content A form's content, which can contain an arbitrary
         *   layout along with field editor declarations.
         * @return A form's instance that has been created for this
         *   declaration site.
         */
        @Composable
        @Suppress(
            // Explicit casts are needed since we cannot parameterize
            // `MessageFormCompanionBase` with the `C` type parameter.
            "UNCHECKED_CAST"
        )
        public operator fun <C : CommandMessage, B: ValidatingBuilder<out C>> invoke(
            builder: () -> B,
            value: MutableState<C?> = mutableStateOf(null),
            onBeforeBuild: (B) -> Unit = {},
            props: ComponentProps<CommandMessageForm<C>> = ComponentProps {},
            content: @Composable FormPartScope<C>.() -> Unit
        ): CommandMessageForm<C> = declareInstance(
            value as MutableState<CommandMessage?>,
            builder,
            props as ComponentProps<CommandMessageForm<CommandMessage>>,
            onBeforeBuild
        ) {
            content(this as FormPartScope<C>)
        } as CommandMessageForm<C>

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
         * @param C A type of the command message being edited with the form.
         * @param B A type of the command message builder.
         *
         * @param value The command message value to be edited within the form.
         * @param builder A lambda that should create and return a new builder
         *   for a command message of type [C].
         * @param onBeforeBuild A lambda that allows to amend the command
         *   message after any valid field is entered to it.
         * @param props A lambda that can set any additional props on the form.
         * @param content A form's content, which can contain an arbitrary
         *   layout along with field editor declarations.
         * @return A form's instance that has been created for this
         * declaration site.
         */
        @Composable
        @Suppress(
            // Explicit casts are needed since we cannot parameterize
            // `MessageFormCompanionBase` with the `C` type parameter.
            "UNCHECKED_CAST"
        )
        public fun <C : CommandMessage, B: ValidatingBuilder<out C>> Multipart(
            builder: () -> B,
            value: MutableState<C?> = mutableStateOf(null),
            onBeforeBuild: (B) -> Unit = {},
            props: ComponentProps<CommandMessageForm<C>> = ComponentProps {},
            content: @Composable MultipartFormScope<C>.() -> Unit
        ): CommandMessageForm<C> = declareMultipartInstance(
            value as MutableState<CommandMessage?>,
            builder,
            props as ComponentProps<CommandMessageForm<CommandMessage>>,
            onBeforeBuild
        ) {
            content(this as MultipartFormScope<C>)
        } as CommandMessageForm<C>

        /**
         * Creates a [CommandMessageForm] instance without rendering it in
         * the composable content right away.
         *
         * This method can be used to create a form's instance outside
         * a composable context, and render it separately. A form's instance
         * created in this way, has to be rendered  explicitly by calling either
         * its [Content] method (for single-part forms) or its
         * [MultipartContent] method (for rendering a multipart form) in
         * a composable context where it needs to be displayed.
         *
         * NOTE: `create` returns a new instance each time it is invoked and
         * would typically not need to be invoked from composable
         * functions/methods. If you need to render a component inside
         * a composable function or method, then you probably need one of the
         * [invoke] based instance declarations instead of using the
         * `create` function.
         *
         * @param C A type of the command message being edited with the form.
         * @param B A type of the command message builder.
         *
         * @param builder A lambda that should create and return a new builder
         *   for a command of type [C].
         * @param value The command message value to be edited within the form.
         * @param onBeforeBuild A lambda that allows to amend the command
         *   message after any valid field is entered to it. Note that this
         *   callback is invoked repeatedly as the user edits the form and its
         *   implementation should avoid long or performance-intensive
         *   operations to preserve a smooth user's experience.
         * @param props A lambda that can set any additional props on the form.
         * @return A form's instance that has been created for this
         *   declaration site.
         */
        @Suppress(
            // Explicit casts are needed since we cannot parameterize
            // `MessageFormCompanionBase` with the `C` type parameter.
            "UNCHECKED_CAST"
        )
        public fun <C : CommandMessage, B: ValidatingBuilder<out C>> create(
            builder: () -> B,
            value: MutableState<C?> = mutableStateOf(null),
            onBeforeBuild: (B) -> Unit = {},
            props: ComponentProps<CommandMessageForm<C>> = ComponentProps {}
        ): CommandMessageForm<C> = createInstance(
            value as MutableState<CommandMessage?>,
            builder,
            onBeforeBuild,
            props as ComponentProps<CommandMessageForm<CommandMessage>>
        ) as CommandMessageForm<C>
    }

    /**
     * A function, which, given a command message that is about to be posted,
     * should subscribe to a respective event that is expected to arrive in
     * response to handling that command.
     */
    public lateinit var eventSubscription: (C) -> EventSubscription<out EventMessage>

    /**
     * A state, which reports whether the form is currently in progress of
     * posting the command.
     *
     * More precisely, it is set to `true`, if the command has been posted using
     * the [postCommand] method, but no response or timeout error has been
     * received yet, and `false` otherwise.
     *
     * This property is backed by a [State] object, so it can be used as part of
     * a composition, which will be updated automatically when this property
     * is changed. E.g. it can be used to disable the respective "Post" button
     * to prevent making it possible to post command duplicates.
     */
    public var posting: Boolean by mutableStateOf(false)

    /**
     * Specifies whether field editors should be disabled when the command is
     * being posted (when [posting] equals `true`).
     */
    public var disableOnPosting: Boolean = true

    /**
     * This overridden implementation ensures that the editors are disabled when
     * the command is being posted (when [posting] is `true`).
     *
     * Note: if `disableOnPosting` is `false`, no automatic disabling is
     * performed during posting the command.
     */
    override val shouldEnableEditors: Boolean
        get() = super.shouldEnableEditors && (!posting || !disableOnPosting)

    @Composable
    @ReadOnlyComposable
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
     *   validation errors, and `false` if the command message could not be
     *   successfully built from the currently entered data (validation errors
     *   are displayed to the user in this case).
     * @throws TimeoutCancellationException If the event doesn't arrive within
     *   a reasonable timeout defined by the implementation.
     * @throws IllegalStateException If the method is invoked while
     *   the [postCommand] invocation is still being handled (when [posting] is
     *   still `true`).
     */
    public suspend fun postCommand(
        responseHandler: CommandResponseHandler<C> = DefaultResponseHandler()
    ): Boolean {
        if (posting) {
            throw IllegalStateException("Cannot invoke `postCommand`, while" +
                    "waiting for handling the previously posted command.")
        }
        updateValidationDisplay(true)
        if (!valueValid.value) {
            return false
        }
        val command = value.value
        check(command != null) {
            "CommandMessageForm's value should be not null since it was just " +
            "checked to be valid within postCommand."
        }
        val subscription = eventSubscription(command)
        return try {
            posting = true
            app.client.command(command)
            subscription.awaitEvent()
            true
        } catch (
            @Suppress(
                // A timeout condition is handled by `responseHandler`.
                "SwallowedException"
            )
            e: TimeoutCancellationException
        ) {
            responseHandler.responseWaitingTimedOut(command)
            false
        } finally {
            posting = false
        }
    }
}

public interface CommandResponseHandler<C : CommandMessage> {
    public fun responseWaitingTimedOut(command: C)
}

public class DefaultResponseHandler<C : CommandMessage> : CommandResponseHandler<C> {
    override fun responseWaitingTimedOut(command: C) {
        println("Timed out waiting for an event that was expected " +
                "to be generated in response to the command ${command.javaClass.simpleName}")
    }
}
