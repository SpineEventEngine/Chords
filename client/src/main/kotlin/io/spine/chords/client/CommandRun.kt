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
import io.spine.base.RejectionMessage
import io.spine.chords.client.appshell.client
import io.spine.chords.core.appshell.app
import io.spine.chords.core.layout.MessageDialog.Companion.showMessage
import io.spine.chords.core.writeOnce
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout

/**
 * An extension function, which first posts the command on which it is invoked,
 * then waits until either of the outcomes configured by the passed [lifecycle]
 * is received, or until the timeout period configured in [lifecycle]
 * object elapses.
 *
 * This function makes the [lifecycle] object to handle the outcomes or error
 * conditions that have occurred after posting the command.
 *
 * @param lifecycle A [CommandRun] instance whose configuration defines
 *   how the command's possible outcomes should be handled.
 * @return `true`, if either of the non-rejection events configured in
 *   [lifecycle] was received before the timeout period elapses, and
 *   `false` otherwise.
 *
 */
public suspend fun <C: CommandMessage> C.post(lifecycle: CommandRun<C>): Boolean =
    lifecycle.post(this)

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
 *   within [outcomeSubscriptions] is emitted, or until the [timeout]
 *   period elapses.
 *
 *   If either of the specified non-rejection events is emitted during this
 *   period, then the function will run the respective handler (if specified),
 *   and return `true` to signify the "positive" command posting outcome.
 *
 *   Otherwise (if either of the specified rejections is emitted, or if an error
 *   is identified during the command's posting, or when no events or rejections
 *   are emitted during the [timeout] period), the respective condition is first
 *   handled accordingly (see below), and then `false` is returned to signify
 *   the "negative" command posting outcome.
 *
 * - The [outcomeSubscriptions] lambda can be used to specify an arbitrary set
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
 *   configured events/rejections are received during the [timeout] period), the
 *   [onTimeout] callback is invoked (or the [timeoutMessage] text is displayed
 *   if the callback is not specified).
 *
 * ## Usage examples
 *
 * Note that the [outcomeSubscriptions] lambda is invoked in context of the
 * [OutcomeSubscriptionScope] object, which provides the
 * [command][OutcomeSubscriptionScope.command], which is going to be posted, and
 * some functions that can be used to declare the list of expected command
 * outcomes. See the [event][OutcomeSubscriptionScope.event],
 * [rejection][OutcomeSubscriptionScope.rejection], and
 * [handledAs][OutcomeSubscriptionScope.handledAs] functions.
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
 * [handledAs][OutcomeSubscriptionScope.handledAs] infix function, or using the
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
 * @param outcomeSubscriptions A lambda, which defines the set of events and
 *   rejections, which can be emitted as an outcome of command [C]. This lambda
 *   should use the [event][OutcomeSubscriptionScope.event] and
 *   [rejection][OutcomeSubscriptionScope.rejection] functions to set up
 *   expected positive/negative command outcomes respectively. Besides, each
 *   event/command subscriptions can optionally be accompanied with the
 *   [handledBy][OutcomeSubscriptionScope.handledAs] infix function to specify
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
 *   the events/rejections configured with [outcomeSubscriptions] were received
 *   during the [timeout] period.
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
 * @param timeout A maximum period of time starting from the moment the command
 *   was posted that the configured outcomes are being waited for by
 *   this object.
 * @return `true`, to signify the positive command posting outcome (e.g., when
 *   either of configured non-rejection events was emitted before the [timeout]
 *   period elapses), and `false` otherwise.
 */
public open class CommandRun<C : CommandMessage>(
    private val outcomeSubscriptions: OutcomeSubscriptionScope<C>.() -> Unit,
    private var onPostingError: (suspend (C, CommandPostingError) -> Unit)? = null,
    private var onTimeout: (suspend (C) -> Unit)? = null,
    private var timeout: Duration = 20.seconds
) {

    /**
     * Subscriptions, which should be waited for before the [post]
     * method returns.
     */
    private val subscriptions: MutableSet<EventSubscription<out EventMessage>> = HashSet()

    /**
     * The handlers that were attached to subscriptions.
     *
     * The keys are a subset of [subscriptions].
     */
    private val subscriptionHandlers: MutableMap<
            EventSubscription<out EventMessage>,
            suspend (EventMessage) -> Unit
            > = HashMap()

    private inner class OutcomeSubscriptionScopeImpl(
        override val command: C
    ) : OutcomeSubscriptionScope<C> {
        override fun event(
            eventType: Class<out EventMessage>,
            field: EventMessageField,
            fieldValue: Message
        ): EventSubscription<out EventMessage> = subscribe(eventType, field, fieldValue)

        override fun rejection(
            rejectionType: Class<out RejectionMessage>,
            field: EventMessageField,
            fieldValue: Message
        ): EventSubscription<out RejectionMessage> = subscribe(rejectionType, field, fieldValue)

        override fun EventSubscription<out EventMessage>.handledAs(
            handler: suspend (EventMessage) -> Unit
        ) {
            subscriptions += this
            subscriptionHandlers[this] = handler
        }
    }

    /**
     * Subscribes to the specified type of event according to the specified
     * field value, and registers this subscription as the one that the [post]
     * method should wait for.
     */
    protected fun <E : EventMessage> subscribe(
        eventType: Class<out E>,
        field: EventMessageField,
        fieldValue: Message
    ): EventSubscription<out E> {
        val eventSubscription = app.client.subscribeToEvent(eventType, field, fieldValue)
        subscriptions += eventSubscription
        return eventSubscription
    }

    /**
     * Configures the object with any subscriptions that are needed.
     *
     * The event subscriptions are expected to be made with the
     * [subscribe] method.
     */
    protected open fun makeSubscriptions(command: C) {
        val scope = OutcomeSubscriptionScopeImpl(command)
        scope.outcomeSubscriptions()
    }

    /**
     * Posts the given [command], and makes sure that the expected outcomes
     * (events/rejections) or other conditions (posting errors or outcome
     * waiting timeout condition) are handled according to this
     * object's configuration.
     *
     * @param command The command that should be posted.
     * @return `true` if any of the expected non-rejection events were received
     *   before the [timeout] period elapses, and `false` otherwise.
     */
    public suspend fun post(command: C): Boolean {
        makeSubscriptions(command)

        val futureCommandOutcome = CompletableFuture<EventMessage>()
        var subscriptionTriggered: EventSubscription<*> by writeOnce()
        for (subscription in subscriptions) {
            subscription.onEvent = { event ->
                subscriptionTriggered = subscription
                futureCommandOutcome.complete(event)
            }
        }

        return try {
            app.client.command(command)

            val event = withTimeout(timeout) { futureCommandOutcome.await() }

            val eventHandler = subscriptionHandlers[subscriptionTriggered]
            if (eventHandler != null) {
                eventHandler(event)
            } else {
                if (event is RejectionMessage) {
                    handleRejection(command, event)
                } else {
                    handleEvent(command, event)
                }
            }

            event !is RejectionMessage
        } catch (e: CommandPostingError) {
            handlePostingError(command, e)
            false
        } catch (
            @Suppress(
                // A timeout condition is handled by `outcomeHandler`.
                "SwallowedException"
            )
            e: TimeoutCancellationException
        ) {
            handleTimeout(command)
            false
        }
    }

    /**
     * Invoked when any of the expected non-rejection events was received.
     *
     * @param command The command that was posted.
     * @param event The non-rejection event that was received after posting
     *   the [command].
     */
    protected open suspend fun handleEvent(command: C, event: EventMessage) {
        val message = eventMessage(command, event)
        if (message != null) {
            showMessage(message)
        }
    }

    /**
     * Invoked when any of the expected rejections was received.
     *
     * @param command The command that was posted.
     * @param rejection The rejection that was received after posting
     *   the [command].
     */
    protected open suspend fun handleRejection(command: C, rejection: RejectionMessage) {
        val message = rejectionMessage(command, rejection)
        if (message != null) {
            showMessage(message)
        }
    }

    /**
     * Invoked when an error has occurred during posting or acknowledging
     * the command.
     *
     * @param command The command that was posted.
     * @param error The error that has occurred during posting or acknowledging
     *   the command.
     */
    protected open suspend fun handlePostingError(command: C, error: CommandPostingError) {
        if (onPostingError != null) {
            onPostingError!!(command, error)
        }
    }

    /**
     * Invoked when neither of the expected events/rejections were received
     * during the [timeout] period after the [command] was posted.
     *
     * @param command The command that was posted.
     */
    protected open suspend fun handleTimeout(command: C) {
        if (onTimeout != null) {
            onTimeout!!(command)
        }
    }
}

/**
 * Defines a DSL available in scope of the [CommandRun]'s
 * [outcomeSubscriptions][CommandRun.outcomeSubscriptions] callback.
 */
public interface OutcomeSubscriptionScope<C: CommandMessage> {

    /**
     * The command whose outcomes are being specified in this scope.
     */
    public val command: C

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
    public fun event(
        eventType: Class<out EventMessage>,
        field: EventMessageField,
        fieldValue: Message
    ): EventSubscription<out EventMessage>

    /**
     * Subscribes to a rejection of type [rejectionType], which has its [field]
     * equal to [fieldValue], and registers it as one of the possible outcomes.
     *
     * @param rejectionType A type of rejection that should be subscribed to.
     * @param field A field whose value should identify a rejection being
     *   subscribed to.
     * @param fieldValue A value of event's [field] that identifies a rejection
     *   being subscribed to.
     * @return A subscription that was created.
     * @see event
     */
    public fun rejection(
        rejectionType: Class<out RejectionMessage>,
        field: EventMessageField,
        fieldValue: Message
    ): EventSubscription<out RejectionMessage>

    /**
     * An infix function, which can be used alongside with [event] or
     * [rejection] function call to specify how the respective outcome should
     * be handled.
     *
     * Specifying such an individual event/rejection handler associates the
     * provided [handler] with this particular event/rejection subscription, and
     * doing so prevents the [CommandRun]'s default
     * [CommandRun.onEvent]/[CommandRun.onRejection] functions to be
     * called for this specific event/rejection subscription in favor of
     * this [handler].
     *
     * @receiver An event/rejection subscription, for which an individual
     *   handler has to be registered, which will replace the default handler.
     * @param handler The callback, which will replace the default handler for
     *   this specific event/rejection subscription.
     */
    public infix fun EventSubscription<out EventMessage>.handledAs(
        handler: suspend (EventMessage) -> Unit
    )
}
