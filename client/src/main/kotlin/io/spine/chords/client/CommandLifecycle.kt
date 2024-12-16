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
 * An object, which can be set up to handle the client-side lifecycle of command
 * [C], starting with posting of a command, including handling different types
 * of outcomes, and error/timeout conditions.
 *
 * This object supports configuring the following aspects of the client side
 * command handling lifecycle:
 *
 * - First, the respective command [C] should be posted using the [post] method,
 *   or using the respective `CommandMessage.post` extension function.
 *
 *   This call will wait until any of the events or rejections specified within
 *   [outcomeSubscriptions] is emitted, or until the [timeout] period elapses.
 *
 *   If either of the specified non-rejection events is emitted during this
 *   period, then the function will run the respective handler (if specified),
 *   and return `true` to signify the "positive" command posting outcome.
 *
 *   Otherwise (if any of the specified rejections is emitted, or if an error
 *   is identified during the command's posting, or when no events or rejections
 *   are emitted during the [timeout] period), the respective condition is
 *   handled accordingly (see below), and `false` is returned to signify the
 *   "negative" command posting outcome.
 *
 * - The [outcomeSubscriptions] lambda can be used to specify an arbitrary set
 *   of event and rejection subscriptions to specify the expected positive and
 *   negative outcomes of posting the command [C].
 *
 *   By default, upon receiving either of the expected rejections, it invokes
 *   the [onRejection] callback, or, if it's not specified displays a message
 *   defined by [rejectionMessage].
 *
 *   It's also possible to specify the per-event/per-rejection handler, which
 *   will override the default behavior above if more granular event/rejection
 *   handling is required.
 *
 * - If an error happens when posting and acknowledging the command, the
 *   [onPostingError] callback is invoked (or the [postingErrorMessage] text is
 *   displayed if the callback is not specified).
 *
 * - Analogously, when the timeout condition is identified (if neither of the
 *   configured events/rejections are received during the [timeout] period), the
 *   [onTimeout] callback is invoked (or the [timeoutMessage] text is displayed
 *   if the callback is not specified).
 *
 * ### Usage example
 *
 * Here's an example of using `CommandLifecycle`:
 *
 * val command: SomeCommand = someCommand()
 * command.post
 *
 * @param C A type of command whose lifecycle is being configured.
 *
 * @param outcomeSubscriptions A lambda, which defines the set of events and
 *   rejections, which can be emitted as an outcome of command [C]. This lambda
 *   can use the [event][OutcomeSubscriptionScope.event] and
 *   [rejection][OutcomeSubscriptionScope.rejection] functions to set up
 *   expected positive/negative command outcomes respectively. Besides, each
 *   event/command subscriptions can optionally be accompanied with the
 *   [handledBy][OutcomeSubscriptionScope.handledAs] infix function to specify
 *   a custom per-event/per-rejection handler(s) where such fine-grained
 *   handlers are required.
 * @param onPostingError An optional callback, which will be invoked when an
 *   error was received during posting and acknowledging the command. If no
 *   callback is provided, the [handlePostingError] method will be invoked,
 *   which displays a respective message dialog by default.
 * @param onTimeout An optional callback, which will be invoked when neither of
 *   the events/rejections configured with [outcomeSubscriptions] were received
 *   during the [timeout] period.
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
 *   either of non-rejection events was emitted before the [timeout] period
 *   elapses), and `false` otherwise.
 */
public open class CommandLifecycle<C : CommandMessage>(
    private val outcomeSubscriptions: OutcomeSubscriptionScope<C>.() -> Unit,
    private var onEvent: (suspend (C, EventMessage) -> Unit)? = null,
    private var onRejection: (suspend (C, RejectionMessage) -> Unit)? = null,
    private var onPostingError: (suspend (C, CommandPostingError) -> Unit)? = null,
    private var onTimeout: (suspend (C) -> Unit)? = null,
    private var eventMessage: (C, EventMessage) -> String? = { command, event -> null },
    private var rejectionMessage: (C, RejectionMessage) -> String? = { command, rejection ->
        "Rejection ${rejection.javaClass.simpleName} was emitted in response to " +
                "the command ${command.javaClass.simpleName}"
    },
    private var timeoutMessage: (C) -> String? = { command ->
        "Timed out waiting for an event in response to " +
                "the command ${command.javaClass.simpleName}"
    },
    private var postingErrorMessage: (C, CommandPostingError) -> String? = { command, error ->
        "An error has occurred when posting or acknowledging " +
                "the command ${command.javaClass.simpleName}: ${error.message}"
    },
    private var timeout: Duration = 20.seconds
) {
    /**
     * Subscriptions, which should be waited for before the [post]
     * method returns.
     */
    private val subscriptions: MutableSet<EventSubscription<out EventMessage>> = HashSet()

    /**
     *
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

    protected fun <E : EventMessage> subscribe(
        eventType: Class<out E>,
        field: EventMessageField,
        fieldValue: Message
    ): EventSubscription<out E> {
        val eventSubscription = app.client.subscribeToEvent(eventType, field, fieldValue)
        subscriptions += eventSubscription
        return eventSubscription
    }

    protected open fun makeSubscriptions() {}

    public suspend fun post(command: C): Boolean {
        val scope = OutcomeSubscriptionScopeImpl(command)
        scope.outcomeSubscriptions()

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

    protected open suspend fun handleEvent(command: C, event: EventMessage) {
        if (onEvent != null) {
            onEvent!!(command, event)
        } else {
            val message = eventMessage(command, event)
            if (message != null) {
                showMessage(message)
            }
        }
    }

    protected open suspend fun handleRejection(command: C, rejection: RejectionMessage) {
        if (onRejection != null) {
            onRejection!!(command, rejection)
        } else {
            val message = rejectionMessage(command, rejection)
            if (message != null) {
                showMessage(message)
            }
        }
    }

    protected open suspend fun handlePostingError(command: C, error: CommandPostingError) {
        if (onPostingError != null) {
            onPostingError!!(command, error)
        } else {
            val message = postingErrorMessage(command, error)
            if (message != null) {
                showMessage(message)
            }
        }
    }

    protected open suspend fun handleTimeout(command: C) {
        if (onTimeout != null) {
            onTimeout!!(command)
        } else {
            val message = timeoutMessage(command)
            if (message != null) {
                showMessage(message)
            }
        }
    }
}

public suspend fun <C: CommandMessage> C.post(lifecycle: CommandLifecycle<C>): Boolean =
    lifecycle.post(this)

/**
 * Defines a DSL available in scope of the [CommandLifecycle]'s
 * [outcomeSubscriptions][CommandLifecycle.outcomeSubscriptions] callback.
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
     * doing so prevents the [CommandLifecycle]'s default
     * [CommandLifecycle.onEvent]/[CommandLifecycle.onRejection] functions to be
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
