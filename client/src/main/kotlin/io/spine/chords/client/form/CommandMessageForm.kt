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

package io.spine.chords.client.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import io.spine.base.CommandMessage
import io.spine.chords.client.CommandConsequences
import io.spine.chords.client.CommandConsequencesScope
import io.spine.chords.client.EventSubscriptions
import io.spine.chords.client.appshell.client
import io.spine.chords.core.appshell.Props
import io.spine.chords.core.appshell.app
import io.spine.chords.proto.form.FormPartScope
import io.spine.chords.proto.form.MessageForm
import io.spine.chords.proto.form.MessageFormSetupBase
import io.spine.chords.proto.form.MultipartFormScope
import io.spine.protobuf.ValidatingBuilder
import kotlinx.coroutines.CoroutineScope

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
 *              onClick = {
 *                  if (form.valueValid.value) {
 *                      form.postCommand()
 *                  }
 *              }
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
 *              onClick = {
 *                  if (form.valueValid.value) {
 *                      form.postCommand()
 *                  }
 *              }
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
            props: Props<CommandMessageForm<C>> = Props {},
            content: @Composable FormPartScope<C>.() -> Unit
        ): CommandMessageForm<C> = declareInstance(
            value as MutableState<CommandMessage?>,
            builder,
            props as Props<CommandMessageForm<CommandMessage>>,
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
            props: Props<CommandMessageForm<C>> = Props {},
            content: @Composable MultipartFormScope<C>.() -> Unit
        ): CommandMessageForm<C> = declareMultipartInstance(
            value as MutableState<CommandMessage?>,
            builder,
            props as Props<CommandMessageForm<CommandMessage>>,
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
            props: Props<CommandMessageForm<C>> = Props {}
        ): CommandMessageForm<C> = createInstance(
            value as MutableState<CommandMessage?>,
            builder,
            onBeforeBuild,
            props as Props<CommandMessageForm<CommandMessage>>
        ) as CommandMessageForm<C>
    }

    /**
     * A function, which, should register handlers for possible consequences of
     * command [C] posted by the form.
     *
     * The command, which is going to be posted and whose consequence handlers
     * should be registered can be obtained from the
     * [command][CommandConsequencesScope.command] property available in the
     * function's scope, and handlers can be registered using the
     * [`onXXX`][CommandConsequencesScope] functions available in the
     * function's scope.
     *
     * See [CommandConsequencesScope] for the description of API available
     * within the scope of [commandConsequences] function.
     *
     * @see CommandConsequencesScope
     * @see postCommand
     * @see cancelActiveSubscriptions
     */
    public lateinit var commandConsequences: CommandConsequencesScope<C>.() -> Unit

    /**
     * A lambda, which can be used to customize the creation of
     * [CommandConsequences] instance.
     *
     * In most cases, specifying the [commandConsequences] property should be
     * enough for defining the set of expected command posting consequences and
     * their handlers. By default, the [commandConsequences] lambda will be used
     * to create a [CommandConsequences] instance, which will be used when
     * posting the command.
     *
     * This property can be used in cases when it might be useful to also
     * customize instantiation of [CommandConsequences], e.g. when a subclass of
     * [CommandConsequences] needs to be used.
     */
    public var createCommandConsequences:
                (CommandConsequencesScope<C>.() -> Unit) -> CommandConsequences<C> =
        { CommandConsequences(it) }

    /**
     * [CoroutineScope] owned by this form's composition used for running
     * form-related suspending calls.
     */
    private lateinit var coroutineScope: CoroutineScope

    /**
     * Event subscriptions, which were made by [commandConsequences] as a result
     * of command posted during dialog form's submission.
     */
    private var activeSubscriptions: MutableList<EventSubscriptions> = ArrayList()

    override fun initialize() {
        super.initialize()
        requireProperty(this::commandConsequences.isInitialized, "commandConsequences")
    }

    @Composable
    override fun content() {
        coroutineScope = rememberCoroutineScope()
        super.content()
    }

    /**
     * Posts the command based on all currently entered data.
     *
     * Note that this method can only be invoked when the data entered within
     * the form is valid (when `valueValid.value == false`).
     *
     * Here's a typical usage example:
     * ```
     *     // Make sure that validation messages are up to date before
     *     // submitting the form.
     *     commandMessageForm.updateValidationDisplay(true)
     *
     *     // Submit the form if it is valid currently.
     *     if (commandMessageForm.valueValid.value) {
     *         commandMessageForm.postCommand()
     *     }
     * ```
     *
     * Note that the [updateValidationDisplay] invocation is technically not
     * required to check if the form is valid because the form is always
     * validated on-the-fly automatically, and its [valueValid] property always
     * contains an up-to-date value. Nevertheless, it would typically be useful
     * to invoke it before [postCommand] to improve user's experience when the
     * form's [validationDisplayMode] property has a value of
     * [MANUAL][io.spine.chords.proto.form.ValidationDisplayMode.MANUAL].
     *
     * @return An object, which allows managing (e.g. cancelling) all
     *   subscriptions made by the [commandConsequences] callback.
     * @throws IllegalStateException If the form is not valid when this method
     *   is invoked (e.g. when `valueValid.value == false`).
     * @see commandConsequences
     * @see cancelActiveSubscriptions
     */
    public suspend fun postCommand(): EventSubscriptions {
        updateValidationDisplay(true)
        check(valueValid.value) {
            "`postCommand` cannot be invoked on an invalid form`"
        }
        val command = value.value
        check(command != null) {
            "CommandMessageForm's value should be not null since it was just " +
            "checked to be valid within postCommand."
        }
        val consequences = createCommandConsequences(commandConsequences)
        val subscriptions = app.client.postCommand(command, consequences)
        activeSubscriptions += subscriptions
        return subscriptions
    }

    /**
     * Cancels all event subscriptions that have been made as a result of
     * invoking this form's [postCommand] method.
     *
     * @see postCommand
     * @see commandConsequences
     */
    public fun cancelActiveSubscriptions() {
        activeSubscriptions.forEach { it.cancelAll() }
        activeSubscriptions.clear()
    }
}
