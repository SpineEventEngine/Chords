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

package io.spine.chords.client

import com.google.protobuf.Message
import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.base.EventMessageField
import io.spine.chords.client.appshell.client
import io.spine.chords.core.appshell.app
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * An object, which can be set up to handle the client-side lifecycle of command
 * [C], starting with posting of a command, and then handling different types
 * of outcomes, and error/timeout conditions.
 *
 * This object supports configuring the following aspects of the client side
 * command handling lifecycle:
 *
 * - First, the respective command [C] should be posted using the [post] method,
 *   or using the respective `CommandMessage.post` extension function.
 *
 *   This call will wait until either of the events or rejections specified
 *   within [consequenceSubscriptions] is emitted, or until the [defaultEventTimeout]
 *   period elapses.
 *
 *   If either of the specified non-rejection events is emitted during this
 *   period, then the function will run the respective handler (if specified),
 *   and return `true` to signify the "positive" command posting outcome.
 *
 *   Otherwise (if either of the specified rejections is emitted, or if an error
 *   is identified during the command's posting, or when no events or rejections
 *   are emitted during the [defaultEventTimeout] period), the respective condition is first
 *   handled accordingly (see below), and then `false` is returned to signify
 *   the "negative" command posting outcome.
 *
 * - The [consequenceSubscriptions] lambda can be used to specify an arbitrary set
 *   of event and rejection subscriptions to specify the expected positive and
 *   negative outcomes of posting the command [C] respectively.
 *
 *   By default, upon receiving either of the expected rejections, it invokes
 *   the [onRejection] callback, or, when it's not specified, displays a message
 *   defined by [rejectionMessage].
 *
 *   It's also possible to specify the per-event/per-rejection handlers, which
 *   will override the default behavior above if more granular event/rejection
 *   handling is required.
 *
 * - If an error happens when posting or acknowledging the command, the
 *   [onPostingError] callback is invoked (or the [postingErrorMessage] text is
 *   displayed if the callback is not specified).
 *
 * - Analogously, when the timeout condition is identified (if neither of the
 *   configured events/rejections are received during the [defaultEventTimeout] period), the
 *   [onTimeout] callback is invoked (or the [timeoutMessage] text is displayed
 *   if the callback is not specified).
 *
 * ## Usage examples
 *
 * Note that the [consequenceSubscriptions] lambda is invoked in context of the
 * [CommandConsequencesScope] object, which provides the
 * [command][CommandConsequencesScope.command], which is going to be posted, and
 * some functions that can be used to declare the list of expected command
 * outcomes. See the [event][CommandConsequencesScope.onEvent],
 * [rejection][CommandConsequencesScope.rejection], and
 * [handledAs][CommandConsequencesScope.handledAs] functions.
 *
 * Here's a simple example of configuring `CommandLifecycle` to wait for
 * `ExpectedEvent` as an expected "positive" outcome of `SomeCommand`:
 * ```
 *     val command: SomeCommand = someCommand()
 *     val succeeded: Boolean = command.post(
 *         CommandLifecycle({
 *             event(
 *                 ExpectedEvent::class.java,
 *                 ExpectedEvent.Field.id(),
 *                 command.id
 *             )
 *         })
 *     )
 *     // `succeeded` will contain `true` if `ExpectedEvent.id == command.id` was
 *     // emitted as an outcome of handling command `SomeCommand` during the
 *     // default timeout period, and `false` otherwise.
 * ```
 *
 * Below is an example, which demonstrates handling both a regular
 * (non-rejection) event, and a rejection event. Receiving the specified
 * rejection will display the text message defined by [rejectionMessage], and
 * will make the `post` function to return `false`.
 * ```
 *     val command: SomeCommand = someCommand()
 *     val succeeded: Boolean = command.post(
 *         CommandLifecycle({
 *             event(
 *                 ExpectedEvent::class.java,
 *                 ExpectedEvent.Field.id(),
 *                 command.id
 *             )
 *             rejection(
 *                 ExpectedRejection::class.java,
 *                 ExpectedRejection.Field.id(),
 *                 command.id
 *             )
 *         })
 *     )
 *     // If `ExpectedRejection` is emitted during the [timeout] period,
 *     // `succeeded` will be `false.
 * ```
 *
 * It is also optionally possible to customize event/rejection handlers either
 * on a per-event/per-rejection basis using the
 * [handledAs][CommandConsequencesScope.handledAs] infix function, or using the
 * [onEvent]/[onRejection] callbacks, which will be invoked upon receiving
 * either of the configured events/rejections:
 * ```
 *      val command: SomeCommand = someCommand()
 *     val succeeded: Boolean = command.post(
 *         CommandLifecycle(
 *             {
 *                 event(
 *                     ExpectedEvent::class.java,
 *                     ExpectedEvent.Field.id(),
 *                     command.id
 *                 )
 *                 rejection(
 *                     ExpectedRejection1::class.java,
 *                     ExpectedRejection1.Field.id(),
 *                     command.id
 *                 )
 *                 rejection(
 *                     ExpectedRejection2::class.java,
 *                     ExpectedRejection2.Field.id(),
 *                     command.id
 *                 ) handledAs {
 *                     showMessage("Custom rejection message for ExpectedRejection2")
 *                     anyOtherCodeForThisRejection()
 *                 }
 *             },
 *             onRejection = { command, rejection ->
 *                 showMessage("Rejection ${rejection.javaClass.simpleName} was " +
 *                         "received as an outcome for command ${command.javaClass.simpleName}")
 *             }
 *         )
 *     )
 * ```
 *
 * Please see the property descriptions below for the full list of
 * customizations available.
 *
 * @param C A type of command whose lifecycle is being configured.
 *
 * @param consequenceSubscriptions A lambda, which defines the set of events and
 *   rejections, which can be emitted as an outcome of command [C]. This lambda
 *   should use the [event][CommandConsequencesScope.onEvent] and
 *   [rejection][CommandConsequencesScope.rejection] functions to set up
 *   expected positive/negative command outcomes respectively. Besides, each
 *   event/command subscriptions can optionally be accompanied with the
 *   [handledBy][CommandConsequencesScope.handledAs] infix function to specify
 *   a custom per-event/per-rejection handler(s) where such fine-grained
 *   handlers are required.
 * @param onEvent An optional callback, which will be invoked upon receiving any
 *   of the configured non-rejection events.
 * @param onRejection An optional callback, which will be invoked upon receiving any
 *   of the configured rejections.
 * @param onPostingError An optional callback, which will be invoked when an
 *   error was received during posting or acknowledging the command. If no
 *   callback is provided, the [handlePostingError] method will be invoked,
 *   which displays a respective message dialog by default.
 * @param onTimeout An optional callback, which will be invoked when neither of
 *   the events/rejections configured with [consequenceSubscriptions] were received
 *   during the [defaultEventTimeout] period.
 * @param eventMessage A callback, which can return a non-null value to specify
 *   the text message that needs to be displayed when either of the expected
 *   non-rejection events is received.
 * @param rejectionMessage A callback, which can return a non-null value to
 *   specify the text message that needs to be displayed when either of the
 *   expected rejections is received.
 * @param timeoutMessage A callback, which provides a message that should be
 *   displayed upon the timeout condition by default (if no [onTimeout]
 *   parameter is specified).
 * @param postingErrorMessage A callback, which provides a message that should
 *   be displayed upon a posting error by default (if no [onPostingError]
 *   parameter is specified).
 * @param defaultEventTimeout A maximum period of time starting from the moment the command
 *   was posted that the configured outcomes are being waited for by
 *   this object.
 * @return `true`, to signify the positive command posting outcome (e.g., when
 *   either of configured non-rejection events was emitted before the [defaultEventTimeout]
 *   period elapses), and `false` otherwise.
 */
public open class CommandConsequences<C : CommandMessage>(
    private val coroutineScope: CoroutineScope,
    private val consequenceSubscriptions: CommandConsequencesScope<C>.() -> Unit
) {

    private inner class CommandConsequencesScopeImpl(
        override val command: C
    ) : CommandConsequencesScope<C> {

        var beforePostHandlers: List<suspend (C) -> Unit> = ArrayList()
        var postStreamingErrorHandlers: List<suspend (C, StreamingError) -> Unit> =
            ArrayList()
        var postServerErrorHandlers: List<suspend (C, ServerError) -> Unit> = ArrayList()
        var acknowledgeHandlers: List<suspend (C) -> Unit> = ArrayList()

        override fun onBeforePost(handler: suspend (C) -> Unit) {
            beforePostHandlers += handler
        }

        override fun onPostStreamingError(handler: suspend (C, StreamingError) -> Unit) {
            postStreamingErrorHandlers += handler
        }

        override fun onPostServerError(handler: suspend (C, ServerError) -> Unit) {
            postServerErrorHandlers += handler
        }

        override fun onAcknowledge(handler: suspend (C) -> Unit) {
            acknowledgeHandlers += handler
        }

        override fun onEvent(
            eventType: Class<out EventMessage>,
            field: EventMessageField,
            fieldValue: Message,
            timeout: Duration,
            timeoutHandler: (suspend (C) -> Unit)?,
            eventHandler: suspend (EventMessage) -> Unit
        ) {
            app.client.subscribeToEvent(eventType, field, fieldValue, { eventMessage ->
                coroutineScope.launch { eventHandler(eventMessage) }
            }, {
                coroutineScope.launch { timeoutHandler?.invoke(command) }
            }, timeout)
        }
    }

    /**
     * Posts the given [command], and makes sure that the expected outcomes
     * (events/rejections) or other conditions (posting errors or outcome
     * waiting timeout condition) are handled according to this
     * object's configuration.
     *
     * @param command The command that should be posted.
     * @return `true` if any of the expected non-rejection events were received
     *   before the [defaultEventTimeout] period elapses, and `false` otherwise.
     */
    public suspend fun post(command: C) {
        val scope = CommandConsequencesScopeImpl(command)
        scope.consequenceSubscriptions()

        try {
            scope.beforePostHandlers.forEach { it(command) }
            app.client.command(command)
            scope.acknowledgeHandlers.forEach { it(command) }
        } catch (e: ServerError) {
            check(scope.postServerErrorHandlers.isNotEmpty()) {
                "No `onPostServerError` handlers are registered for command: " +
                        command.javaClass.simpleName
            }
            scope.postServerErrorHandlers.forEach { it(command, e) }
        } catch (e: StreamingError) {
            check(scope.postStreamingErrorHandlers.isNotEmpty()) {
                "No `onPostStreamingError` handlers are registered for command: " +
                        command.javaClass.simpleName
            }
            scope.postStreamingErrorHandlers.forEach { it(command, e) }
        }
    }
}

/**
 * Defines a DSL available in scope of the [CommandConsequences]'s
 * [outcomeSubscriptions][CommandConsequences.outcomeSubscriptions] callback.
 */
public interface CommandConsequencesScope<C: CommandMessage> {

    /**
     * The command whose outcomes are being specified in this scope.
     */
    public val command: C

    public fun onBeforePost(handler: suspend (C) -> Unit)
    public fun onPostStreamingError(handler: suspend (C, StreamingError) -> Unit)
    public fun onPostServerError(handler: suspend (C, ServerError) -> Unit)
    public fun onAcknowledge(handler: suspend (C) -> Unit)


    /**
     * Subscribes to an event of type [eventType], which has its [field] equal
     * to [fieldValue], and registers it as one of the possible outcomes.
     *
     * This method is typically expected to be used only for non-rejection types
     * of events. For specifying rejection subscriptions, use the
     * [rejection] function.
     *
     * @param eventType A type of event that should be subscribed to.
     * @param field A field whose value should identify an event being
     *   subscribed to.
     * @param fieldValue A value of event's [field] that identifies an event
     *   being subscribed to.
     * @return A subscription that was created.
     * @see rejection
     */
    public fun onEvent(
        eventType: Class<out EventMessage>,
        field: EventMessageField,
        fieldValue: Message,
        timeout: Duration = 20.seconds,
        timeoutHandler: (suspend (C) -> Unit)? = null,
        eventHandler: suspend (EventMessage) -> Unit
    )
}
