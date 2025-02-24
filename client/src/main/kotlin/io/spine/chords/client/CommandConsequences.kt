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
import io.spine.chords.client.layout.ModalCommandConsequences
import io.spine.chords.core.DefaultPropsOwnerBase
import io.spine.chords.core.appshell.app
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

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
 * the [app.client.postCommand][Client.postCommand] function for
 * usage examples.
 *
 * Note: the same [CommandConsequences] instance can be reused for posting
 * commands of its respective type [C] as many times as needed. At the same
 * time, each posting of a command results in creating a new
 * [CommandConsequencesScope] instance, which is not reused across postings.
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
    public var defaultTimeout: Duration = 30.seconds

    /**
     * An internal method, which posts the given command and handles respective
     * consequences, which are registered with the [consequences] lambda.
     *
     * Applications should use the
     * [app.client.postCommand][Client.postCommand] method instead.
     *
     * @param command The command that should be posted.
     * @return An object, which allows managing (e.g. cancelling) all event
     *   subscriptions made by this method according to [consequences].
     */
    internal suspend fun postAndProcessConsequences(
        command: C
    ): EventSubscriptions = coroutineScope {
        val consequencesScope: CommandConsequencesScopeImpl<C> = createConsequencesScope(command)

        // NOTE: The `coroutineScope` property is not passed to the constructor,
        //       but assigned separately intentionally in order to simplify the
        //       constructor of CommandConsequencesScopeImpl and leave only the
        //       essential data that cannot be inferred automatically there.
        consequencesScope.callbacksCoroutineScope = this
        consequencesScope.postAndProcessConsequences {
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
     *
     * @param consequencesScope The scope to be used for registering
     *   command's consequences.
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
 *    [onServerError]/[onAcknowledge] functions register callbacks invoked
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
 *  - [cancelAllSubscriptions] can be used to cancel all event subscriptions
 *    that have been made with the [onEvent] function.
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
     * Registers the callback, which is invoked before the command is posted
     * (and before any time-consuming preparation for posting a command
     * is made).
     *
     * More precisely, if there are any [onEvent] handlers defined, "before
     * post" callbacks will be run before the first [onEvent] call, since it is
     * considered to be an operation that needs to be done in preparation for
     * posting a command and can be time-consuming due to network communication.
     * If no [onEvent] handlers are defined, "before post" callbacks will be
     * run before the command is posted.
     *
     * @param handler A callback, to be invoked before the command is posted.
     * @throws IllegalStateException If this function is called after [onEvent]
     *   (see the description above).
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
    public fun onServerError(handler: suspend (ServerError) -> Unit)

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
    public override val defaultTimeout: Duration
) : CommandConsequencesScope<C> {

    /**
     * A [CoroutineScope] used to invoke consequence handlers.
     *
     * This property is automatically initialized by the internal
     * [CommandConsequences.postAndProcessConsequences] method.
     */
    internal lateinit var callbacksCoroutineScope: CoroutineScope

    /**
     * Allows to manage subscriptions made in this scope.
     */
    private val subscriptions: EventSubscriptions = object : EventSubscriptions {
        override fun cancelAll() {
            eventSubscriptions.forEach { it.cancel() }
        }
    }

    /**
     * [onEvent] subscriptions are deferred until after all consequences are
     * registered, and this flag kept `false` until such deferred subscriptions
     * are actually fulfilled, after which this flag is set to `true`.
     */
    private var initialSubscriptionsMade = false

    /**
     * Returns `true` if all event subscriptions that have been made using the
     * [onEvent] method are active.
     */
    private val allSubscriptionsActive: Boolean get() = eventSubscriptions.all { it.active }

    /**
     * Specifies whether this instance has already been used by
     * invoking [postAndProcessConsequences].
     */
    private var used = false
    private val eventSubscriptions: MutableList<EventSubscription> = ArrayList()
    private var beforePostHandlers: List<suspend () -> Unit> = ArrayList()
    private var networkErrorHandlers: List<suspend (ServerCommunicationException) -> Unit> =
        ArrayList()
    private var serverErrorHandlers: List<suspend (ServerError) -> Unit> = ArrayList()
    private var acknowledgeHandlers: List<suspend () -> Unit> = ArrayList()

    override fun onBeforePost(handler: suspend () -> Unit) {
        beforePostHandlers += handler
    }

    override fun onNetworkError(handler: suspend (ServerCommunicationException) -> Unit) {
        networkErrorHandlers += handler
    }

    override fun onServerError(handler: suspend (ServerError) -> Unit) {
        serverErrorHandlers += handler
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
        val onNetworkError: (Throwable) -> Unit = {
            triggerNetworkErrorHandlers(ServerCommunicationException(it))
        }
        val onEvent: (E) -> Unit = {
            callbacks { eventHandler(it) }
        }
        val subscription = if (!initialSubscriptionsMade) {
            DeferredEventSubscription(eventType, field, fieldValue, onNetworkError, onEvent)
        } else {
            app.client.onEvent(eventType, field, fieldValue, onNetworkError, onEvent)
        }
        eventSubscriptions += subscription
        return subscription
    }

    override fun EventSubscription.withTimeout(
        timeout: Duration,
        timeoutHandler: suspend () -> Unit
    ): Unit = withTimeout(timeout, callbacksCoroutineScope, timeoutHandler)

    private fun triggerBeforePostHandlers() = callbacks {
        beforePostHandlers.forEach { it() }
    }

    internal fun triggerAcknowledgeHandlers() = callbacks {
        acknowledgeHandlers.forEach { it() }
    }

    private fun triggerNetworkErrorHandlers(e: ServerCommunicationException) = callbacks {
        cancelAllSubscriptions()
        if (networkErrorHandlers.isEmpty()) {
            throw IllegalStateException(
                "No `onNetworkError` handlers are registered for command: " +
                        command.javaClass.simpleName,
                e
            )
        }
        networkErrorHandlers.forEach { it(e) }
        yield()
    }

    private fun triggerServerErrorHandlers(e: ServerError) = callbacks {
        cancelAllSubscriptions()
        if (serverErrorHandlers.isEmpty()) {
            throw IllegalStateException(
                "No `onServerError` handlers are registered for command: " +
                        command.javaClass.simpleName,
                e
            )
        }
        serverErrorHandlers.forEach { it(e) }
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
     *
     * @param registerConsequences A function, which should register
     *   consequences, which are relevant within this scope.
     * @return An object, which allows managing (e.g. cancelling) all event
     *   subscriptions made by [registerConsequences].
     */
    internal suspend fun postAndProcessConsequences(
        registerConsequences: () -> Unit
    ): EventSubscriptions = withContext(IO) {
        check(!used) {
            "`postAndProcessConsequences` cannot be invoked more than once " +
                    "on the same `CommandConsequencesScopeImpl` instance."
        }
        used = true
        registerConsequences()

        // "Before post" handlers should be triggered before making
        // subscriptions, since subscriptions require a  continuous network
        // operation, and are considered a part of preparation for posting
        // of a command.
        triggerBeforePostHandlers()

        launch {
            try {
                eventSubscriptions.forEach {
                    if (it is DeferredEventSubscription<*>) {
                        it.subscribe()
                    }
                    initialSubscriptionsMade = true
                }

                val allSubscriptionsSuccessful = allSubscriptionsActive
                if (allSubscriptionsSuccessful) {
                    app.client.postCommand(command)
                    triggerAcknowledgeHandlers()
                } else {
                    cancelAllSubscriptions()
                }
            } catch (e: ServerError) {
                triggerServerErrorHandlers(e)
            } catch (e: ServerCommunicationException) {
                triggerNetworkErrorHandlers(e)
            }
        }
        subscriptions
    }

    /**
     * Triggers [callbacks] inside of a [callbacksCoroutineScope], which is designed to
     * be used for triggering consequences callbacks.
     */
    protected fun callbacks(callbacks: suspend () -> Unit): Job = callbacksCoroutineScope.launch {
        callbacks()
        yield()
    }

    public companion object {

        /**
         * A shortcut for the [CommandConsequences] constructor, which can be
         * used to make [app.client.postCommand][Client.postCommand] calls
         * more concise.
         *
         * As a result `postCommand` usage can look like this:
         * ```
         *     app.client.postCommand(command, consequences {
         *         onEvent(
         *             ItemImported::class.java,
         *             ItemImported.Field.itemId(),
         *             command.itemId
         *         ) {
         *             showMessage("Item imported")
         *         }
         *     })
         * ```
         *
         * @param C A type of commands whose consequences are specified.
         *
         * @property consequences A lambda, which declares expected consequences
         *   along with their handlers.
         * @return The [ModalCommandConsequences] instance that was created.
         */
        public fun <C: CommandMessage> consequences(
            consequences: CommandConsequencesScope<C>.() -> Unit
        ): CommandConsequences<C> {
            return CommandConsequences(consequences)
        }
    }
}

/**
 * An implementation of `EventSubscription`, which makes the respective
 * subscription after the instance was created (see the [subscribe] method).
 *
 * It has the same parameters as the [Client.onEvent] function.
 *
 * @param event A class of events that have to be subscribed to.
 * @param field A field that should be used for identifying the events to be
 *   subscribed to.
 * @param fieldValue A value of the field that identifies the events to be
 *   subscribed to.
 * @param onNetworkError A callback triggered if network communication error
 *   occurs during subscribing or waiting for events. This callback can
 *   either be invoked synchronously communication fails while subscribing
 *   to events, or asynchronously, if the communication error happens after
 *   the subscription has been made. In either of these cases, the returned
 *   `EventSubscription` is transitioned into an inactive state and stops
 *   receiving events.
 * @param onEvent An optional callback, which will be invoked when the
 *   specified event is emitted.
 */
private class DeferredEventSubscription<E : EventMessage>(
    event: Class<E>,
    field: EventMessageField,
    fieldValue: Message,
    onNetworkError: ((Throwable) -> Unit)? = null,
    onEvent: (E) -> Unit
) : EventSubscription {
    private class  SubscriptionParams<E : EventMessage>(
        val event: Class<E>,
        val field: EventMessageField,
        val fieldValue: Message,
        val onNetworkError: ((Throwable) -> Unit)? = null,
        val onEvent: (E) -> Unit
    )

    private class TimeoutParams(
        val timeout: Duration,
        val timeoutCoroutineScope: CoroutineScope,
        val onTimeout: suspend () -> Unit
    )

    private var subscriptionParams: SubscriptionParams<E>? =
        SubscriptionParams(event, field, fieldValue, onNetworkError, onEvent)

    private var timeoutParams: TimeoutParams? = null
    private var actualSubscription: EventSubscription? = null

    override val active: Boolean get() = actualSubscription?.active ?: true

    /**
     * Makes the subscription according to the parameters passed to the
     * class's constructor.
     *
     * This method can be invoked only once for the same
     * `DeferredEventSubscription` instance.
     */
    fun subscribe() {
        check(actualSubscription == null) {
            "Subscription has already been made: `subscribe()` should be invoked only once."
        }
        if (subscriptionParams == null) {
            // Subscription has been cancelled.
            return
        }
        actualSubscription = with(subscriptionParams!!) {
            app.client.onEvent(event, field, fieldValue, onNetworkError, onEvent)
        }
        if (timeoutParams != null) {
            with(timeoutParams!!) {
                actualSubscription!!.withTimeout(timeout, timeoutCoroutineScope, onTimeout)
            }
        }
    }

    override fun withTimeout(
        timeout: Duration,
        timeoutCoroutineScope: CoroutineScope,
        onTimeout: suspend () -> Unit
    ) {
        if (actualSubscription == null) {
            timeoutParams = TimeoutParams(timeout, timeoutCoroutineScope, onTimeout)
        } else {
            actualSubscription!!.withTimeout(timeout, timeoutCoroutineScope, onTimeout)
        }
    }

    override fun cancel() {
        if (actualSubscription == null) {
            subscriptionParams = null
        } else {
            actualSubscription!!.cancel()
        }
    }
}
