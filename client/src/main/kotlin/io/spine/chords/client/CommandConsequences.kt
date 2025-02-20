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

package io.spine.chords.client

import com.google.protobuf.Message
import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.base.EventMessageField
import io.spine.chords.client.appshell.client
import io.spine.chords.core.DefaultPropsOwnerBase
import io.spine.chords.core.appshell.app
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Constitutes consequences, which can be expected after posting a command of
 * type [C], along with respective handlers.
 *
 * Consequences specified within a [CommandConsequences] object are handled by
 * passing this object to the [app.client.postCommand][Client.postCommand]
 * function.
 *
 * The consequences, which are expected along with respective handlers,
 * including callbacks for specific events, rejections, network errors, event
 * timeout conditions, etc. are all declared within the [consequences] lambda,
 * using the API exposed via its [CommandConsequencesScope] receiver.
 * See [CommandConsequencesScope] documentation for the details on API for
 * declaring the expected command's consequences. See also the documentation for
 * the [app.client.postCommand][Client.postCommand] function] for
 * usage examples.
 *
 * @param C A type of commands being posted.
 *
 * @property consequences A lambda, which declares expected consequences along
 *   with their handlers.
 *
 * @see Client.postCommand
 * @see CommandConsequencesScope
 */
public open class CommandConsequences<C: CommandMessage>(
    private val consequences: CommandConsequencesScope<C>.() -> Unit
) : DefaultPropsOwnerBase() {
    protected open fun createConsequencesScope(command: C): CommandConsequencesScopeImpl<C> =
        CommandConsequencesScopeImpl(command, defaultTimeout)

    /**
     * A default value for event timeouts if a respective parameter is omitted
     * in the [withTimeout][CommandConsequencesScope.withTimeout] function.
     */
    public val defaultTimeout: Duration = 30.seconds

    /**
     * An internal method, which posts the given command and handles respective
     * consequences, which are registered with the [consequences] lambda.
     *
     * Applications should use the
     * [app.client.postCommand][Client.postCommand] method instead.
     */
    internal suspend fun postAndProcessConsequences(command: C): EventSubscriptions {
        val consequencesScope: CommandConsequencesScopeImpl<C> = createConsequencesScope(command)

        // NOTE: the `coroutineScope` property is not passed to the constructor,
        //       but assigned separately intentionally in order to simplify the
        //       constructor of CommandConsequencesScopeImpl and leave only the
        //       essential data that cannot be inferred automatically there.
        consequencesScope.coroutineScope = CoroutineScope(coroutineContext)
        return consequencesScope.postAndProcessConsequences {
            registerConsequences(consequencesScope)
        }
    }

    /**
     * Registers consequences implied by this instance in the
     * provided [consequencesScope].
     *
     * By default, this just registers consequences as defined by the
     * [consequences] lambda, but subclasses can extend this behavior with
     * registering some extra consequences.
     */
    protected open fun registerConsequences(consequencesScope: CommandConsequencesScope<C>) {
        consequences(consequencesScope)
    }
}

/**
 * Defines a DSL for registering handlers of command consequences.
 *
 * An instance of `CommandConsequencesScope` is typically expected to serve as
 * a receiver of a function, which configures command consequence handlers. Such
 * a function is typically a part of APIs that post a command, such as
 * [Client.postCommand],
 * [CommandMessageForm][io.spine.chords.client.form.CommandMessageForm],
 * [CommandDialog][io.spine.chords.client.layout.CommandDialog], etc.
 *
 * `CommandConsequencesScope` exposes the following properties and functions:
 *  - The [command] property contains the command message, which is being posted
 *    in this scope.
 *  - The [onBeforePost] function can be used to register a callback, which is
 *    invoked before the command is posted, and the
 *    [onPostServerError]/[onAcknowledge] functions register callbacks invoked
 *    if the command could not be acknowledged due to an error on the server,
 *    and if the command has been acknowledged respectively.
 *  - The [onEvent] function can be used to subscribe to certain events, which
 *    should or can be emitted as a consequence of posting the command [C]. It
 *    can in particular be used for subscribing to rejection events.
 *  - [onNetworkError] registers a callback invoked if a network error occurs
 *    either when posting a command or when observing some of the subscribed
 *    events.
 *
 *    Note that in case of identifying a network error all active event
 *    subscriptions are cancelled and no further events are received.
 *  - [cancelAllSubscriptions] can be used to cancel all event subscriptions that
 *    have been made with the [onEvent] function.
 *
 * See usage examples in the
 * [app.client.postCommand][Client.postCommand] documentation.
 *
 * @param C A type of command message, whose consequences are configured in
 *   this scope.
 *
 * @see Client.postCommand
 */
public interface CommandConsequencesScope<out C: CommandMessage> {

    /**
     * The command whose consequences are being specified and processed in
     * this scope.
     */
    public val command: C

    /**
     * A timeout period that the [withTimeout] function uses by default.
     */
    public val defaultTimeout: Duration

    /**
     * Registers the callback, which is invoked before the command is posted.
     *
     * @param handler A callback, to be invoked before the command is posted.
     */
    public fun onBeforePost(handler: suspend () -> Unit)

    /**
     * Registers the callback, which is invoked if the server acknowledges
     * the command.
     *
     * @param handler A callback to be invoked, whose [ServerCommunicationException] parameter
     *   holds the exception that has signaled the failure.
     */
    public fun onAcknowledge(handler: suspend () -> Unit)

    /**
     * Registers the callback, which is invoked if an error occurs on the server
     * while acknowledging the command.
     *
     * @param handler A callback to be invoked, whose [ServerError] parameter
     *   receives the exception that has signaled the failure.
     */
    public fun onPostServerError(handler: suspend (ServerError) -> Unit)

    /**
     * Subscribes to events of type [eventType], which have its [field] equal
     * to [fieldValue].
     *
     * The subscription remains active by waiting for events that satisfy the
     * specified criteria until the [cancel][EventSubscription.cancel] method
     * is invoked in the returned [EventSubscription] instance, or until all
     * subscriptions made within this instance are cancelled using the
     * [cancelAllSubscriptions] function.
     *
     * @param eventType A type of event that should be subscribed to.
     * @param field A field whose value should identify an event being
     *   subscribed to.
     * @param fieldValue A value of event's [field] that identifies an event
     *   being subscribed to.
     * @return An [EventSubscription] instance, which can be used to manage this
     *   subscription, e.g. add a timeout to it using the [withTimeout]
     *   function, or cancel the subscription using the
     *   [cancel][EventSubscription.cancel] function.
     */
    public fun <E: EventMessage> onEvent(
        eventType: Class<out E>,
        field: EventMessageField,
        fieldValue: Message,
        eventHandler: suspend (E) -> Unit
    ): EventSubscription

    /**
     * Limits the time of waiting for the event to [timeout].
     *
     * If the event is not emitted during [timeout] since this method is invoked
     * then [timeoutHandler] is invoked, and the event subscription
     * is cancelled.
     *
     * @param timeout A maximum period of time that the event is waited for
     *   since the moment of invoking this function.
     * @param timeoutHandler A callback, which should be invoked if an event
     *   is not emitted within the specified [timeout] period after invoking
     *   this method.
     */
    public fun EventSubscription.withTimeout(
        timeout: Duration = defaultTimeout,
        timeoutHandler: suspend () -> Unit)

    /**
     * Registers the callback, which is invoked if a network communication
     * failure occurs while posting the command or waiting for
     * subscribed events.
     *
     * @param handler A callback to be invoked, whose
     *   [ServerCommunicationException] parameter holds the exception that has
     *   signaled the failure.
     */
    public fun onNetworkError(handler: suspend (ServerCommunicationException) -> Unit)

    /**
     * Cancels all active event subscriptions that have been made in
     * this scope.
     */
    public fun cancelAllSubscriptions()
}

/**
 * An implementation of [CommandConsequencesScope].
 *
 * @property command A command whose consequences are being configured.
 * @property defaultTimeout A default event waiting timeout that will be used if
 *   the respective parameter of [withTimeout] is omitted.
 */
@Suppress(
    // Considering all functions to be appropriate.
    "TooManyFunctions"
)
public open class CommandConsequencesScopeImpl<out C: CommandMessage>(
    override val command: C,
    public override val defaultTimeout: Duration = 30.seconds
) : CommandConsequencesScope<C> {

    /**
     * A [CoroutineScope] used to invoke consequence handlers.
     *
     * This property is automatically initialized by the internal
     * [CommandConsequences.postAndProcessConsequences] method.
     */
    internal lateinit var coroutineScope: CoroutineScope

    /**
     * Allows to manage subscriptions made in this scope.
     */
    private val subscriptions: EventSubscriptions = object : EventSubscriptions {
        override fun cancelAll() {
            eventSubscriptions.forEach { it.cancel() }
        }
    }

    /**
     * Returns `true` if all event subscriptions that have been made using the
     * [onEvent] method are active.
     */
    private val allSubscriptionsActive: Boolean get() = eventSubscriptions.all { it.active }

    private val eventSubscriptions: MutableList<EventSubscription> = ArrayList()
    private var beforePostHandlers: List<suspend () -> Unit> = ArrayList()
    private var postNetworkErrorHandlers: List<suspend (ServerCommunicationException) -> Unit> =
        ArrayList()
    private var postServerErrorHandlers: List<suspend (ServerError) -> Unit> = ArrayList()
    private var acknowledgeHandlers: List<suspend () -> Unit> = ArrayList()

    override fun onBeforePost(handler: suspend () -> Unit) {
        beforePostHandlers += handler
    }

    override fun onNetworkError(handler: suspend (ServerCommunicationException) -> Unit) {
        postNetworkErrorHandlers += handler
    }

    override fun onPostServerError(handler: suspend (ServerError) -> Unit) {
        postServerErrorHandlers += handler
    }

    override fun onAcknowledge(handler: suspend () -> Unit) {
        acknowledgeHandlers += handler
    }

    override fun <E: EventMessage> onEvent(
        eventType: Class<out E>,
        field: EventMessageField,
        fieldValue: Message,
        eventHandler: suspend (E) -> Unit
    ): EventSubscription {
        val subscription = app.client.onEvent(
            eventType, field, fieldValue, {
                coroutineScope.launch {
                    triggerNetworkErrorHandlers(ServerCommunicationException(it))
                }
            }, {
                coroutineScope.launch { eventHandler(it) }
            })
        eventSubscriptions += subscription
        return subscription
    }

    override fun EventSubscription.withTimeout(
        timeout: Duration,
        timeoutHandler: suspend () -> Unit
    ): Unit = withTimeout(timeout, coroutineScope, timeoutHandler)

    private suspend fun triggerBeforePostHandlers() {
        beforePostHandlers.forEach { it() }
    }

    internal suspend fun triggerAcknowledgeHandlers() {
        acknowledgeHandlers.forEach { it() }
    }

    private suspend fun triggerNetworkErrorHandlers(e: ServerCommunicationException) {
        cancelAllSubscriptions()
        if (postNetworkErrorHandlers.isEmpty()) {
            throw IllegalStateException(
                "No `onNetworkError` handlers are registered for command: " +
                        command.javaClass.simpleName,
                e
            )
        }
        postNetworkErrorHandlers.forEach { it(e) }
    }

    private suspend fun triggerServerErrorHandlers(e: ServerError) {
        cancelAllSubscriptions()
        if (postServerErrorHandlers.isEmpty()) {
            throw IllegalStateException(
                "No `onPostServerError` handlers are registered for command: " +
                        command.javaClass.simpleName,
                e
            )
        }
        postServerErrorHandlers.forEach { it(e) }
    }

    override fun cancelAllSubscriptions() {
        subscriptions.cancelAll()
    }

    /**
     * An internal method, which posts the given command and handles respective
     * consequences, which are registered with the
     * [registerConsequences] lambda.
     *
     * Applications should use the
     * [app.client.postCommand][Client.postCommand] method instead.
     */
    internal suspend fun postAndProcessConsequences(
        registerConsequences: () -> Unit
    ): EventSubscriptions {
        try {
            registerConsequences()
            val allSubscriptionsSuccessful = allSubscriptionsActive
            if (allSubscriptionsSuccessful) {
                triggerBeforePostHandlers()
                app.client.postCommand(command)
                triggerAcknowledgeHandlers()
            } else {
                subscriptions.cancelAll()
            }
        } catch (e: ServerError) {
            triggerServerErrorHandlers(e)
        } catch (e: ServerCommunicationException) {
            triggerNetworkErrorHandlers(e)
        }
        return subscriptions
    }
}
