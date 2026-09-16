/*
 * Copyright 2026, TeamDev. All rights reserved.
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
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status.Code.UNAVAILABLE
import io.grpc.StatusRuntimeException
import io.spine.base.CommandMessage
import io.spine.base.EntityState
import io.spine.base.Error
import io.spine.base.EventMessage
import io.spine.base.EventMessageField
import io.spine.client.ClientRequest
import io.spine.client.CompositeEntityStateFilter
import io.spine.client.CompositeQueryFilter
import io.spine.client.EventFilter.eq
import io.spine.client.Subscription
import io.spine.core.UserId
import java.lang.Runtime.getRuntime
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Allows active gRPC streams time to finish after the client channel is closed.
 */
private const val ChannelShutdownTimeoutSeconds = 5L

/**
 * Provides API to interact with the application server via gRPC.
 *
 * @param channel The owned channel used for server requests.
 * @param user The callback that should return the user ID on whose behalf
 *   the `DesktopClient` should send requests to the server.
 *   If the callback return `null` the client will send requests
 *   to the server on behalf of the guest user.
 */
@Suppress(
    "TooManyFunctions" /* The functions implement the public Client contract. */
)
public class DesktopClient internal constructor(
    private val channel: ManagedChannel,
    private val user: () -> UserId?
) : Client {

    /**
     * Connects to the application server and owns the resulting channel.
     *
     * @param host The host of the application server.
     * @param port The port accepting gRPC connections.
     * @param user Supplies the actor for each request, or `null` for a guest.
     */
    public constructor(
        host: String,
        port: Int,
        user: () -> UserId? = { null }
    ) : this(
        ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build(),
        user
    )

    /**
     * The underlying Spine client that performs server requests.
     */
    private val spineClient: io.spine.client.Client =
        io.spine.client.Client.usingChannel(channel).build()

    /**
     * Owns the lifecycle of the data observations created by this client:
     * their background scope, their reaction to connection changes, and their
     * shutdown.
     */
    private val observationScope =
        DataObservationScope({ connectionStatus.value })

    /**
     * Runs best-effort subscription cancellation requests.
     */
    private val cancellationScope = CoroutineScope(IO + SupervisorJob())

    /**
     * Prevents the client resources from being closed more than once.
     */
    private val closed = AtomicBoolean(false)

    /**
     * Observes channel state changes and reports connection status updates.
     */
    private val connectionMonitor = ConnectionMonitor(
        { requestConnection -> channel.getState(requestConnection) },
        { source, callback -> channel.notifyWhenStateChanged(source, callback) },
        observationScope::onConnectionStatusChanged
    )

    override val isOpen: Boolean get() = spineClient.isOpen
    override val connectionStatus: StateFlow<ConnectionStatus>
        get() = connectionMonitor.status
    override val userId: UserId? get() = user()

    init {
        observationScope.start()
        connectionMonitor.start()

        getRuntime().addShutdownHook(Thread {
            close()
        })
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        observationScope.close()
        connectionMonitor.close()
        cancellationScope.cancel()
        closeChannel()
    }

    override fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        extractId: (E) -> Any
    ): DataObservation<List<E>> = createObservation(
        initialValue = emptyList(),
        read = {
            clientRequest()
                .select(entityClass)
                .run()
        },
        subscribe = { onUpdate, onRemoval, onError ->
            subscribeTo(
                entityClass = entityClass,
                onUpdate = onUpdate,
                onRemoval = onRemoval,
                onError = onError
            )
        },
        applyUpdate = { entities, entity ->
            updateList(entities, entity, extractId)
        },
        applyRemoval = { entities, id ->
            entities.filterNot { extractId(it) == id }
        }
    )

    override fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        extractId: (E) -> Any,
        queryFilter: CompositeQueryFilter,
        observeFilter: CompositeEntityStateFilter
    ): DataObservation<List<E>> = createObservation(
        initialValue = emptyList(),
        read = {
            clientRequest()
                .select(entityClass)
                .where(queryFilter)
                .run()
        },
        subscribe = { onUpdate, onRemoval, onError ->
            subscribeTo(
                entityClass = entityClass,
                onUpdate = onUpdate,
                onRemoval = onRemoval,
                onError = onError,
                filter = observeFilter
            )
        },
        applyUpdate = { entities, entity ->
            updateList(entities, entity, extractId)
        },
        applyRemoval = { entities, id ->
            entities.filterNot { extractId(it) == id }
        }
    )

    override fun <E : EntityState> readOneAndObserve(
        entityClass: Class<E>,
        queryFilter: CompositeQueryFilter,
        observeFilter: CompositeEntityStateFilter
    ): DataObservation<E?> = createObservation(
        initialValue = null,
        read = {
            clientRequest()
                .select(entityClass)
                .where(queryFilter)
                .run()
                .firstOrNull()
        },
        subscribe = { onUpdate, onRemoval, onError ->
            subscribeTo(
                entityClass = entityClass,
                onUpdate = onUpdate,
                onRemoval = onRemoval,
                onError = onError,
                filter = observeFilter
            )
        },
        applyUpdate = { _, entity -> entity }
    )

    override fun <E : EntityState> readOneAndObserve(
        entityClass: Class<E>,
        queryFilter: CompositeQueryFilter,
        observeFilter: CompositeEntityStateFilter,
        defaultValue: E
    ): DataObservation<E> = createObservation(
        initialValue = defaultValue,
        read = {
            clientRequest()
                .select(entityClass)
                .where(queryFilter)
                .run()
                .firstOrNull() ?: defaultValue
        },
        subscribe = { onUpdate, onRemoval, onError ->
            subscribeTo(
                entityClass = entityClass,
                onUpdate = onUpdate,
                onRemoval = onRemoval,
                onError = onError,
                filter = observeFilter
            )
        },
        applyUpdate = { _, entity -> entity }
    )

    override fun <E : EntityState, M : Message> read(
        entityClass: Class<E>,
        id: M
    ): E? {
        val entities = clientRequest()
            .select(entityClass)
            .byId(id)
            .run()
        return entities.firstOrNull()
    }

    override fun <C : CommandMessage> postCommand(command: C) {
        var error: Throwable? = null
        try {
            clientRequest()
                .command(command)
                .onServerError { _, err: Error ->
                    error = ServerError(err)
                }
                .onStreamingError { err: Throwable ->
                    error = ServerCommunicationException(err)
                }
                .postAndForget()
        } catch (e: StatusRuntimeException) {
            if (e.status.code == UNAVAILABLE) {
                throw ServerCommunicationException(e)
            } else {
                throw e
            }
        }
        if (error != null) {
            throw error!!
        }
    }

    override fun <C : CommandMessage> postCommand(
        command: C,
        consequences: CommandConsequences<C>
    ): EventSubscriptions = consequences.postAndProcessConsequences(command)

    override fun <E : EventMessage> onEvent(
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message,
        onNetworkError: ((Throwable) -> Unit)?,
        onEvent: (E) -> Unit
    ): EventSubscription {
        val eventSubscription = EventSubscriptionImpl(::cancelSubscription)
        try {
            val subscription = clientRequest()
                .subscribeToEvent(event)
                .where(eq(field, fieldValue))
                .observe { evt ->
                    eventSubscription.onEvent()
                    onEvent(evt)
                }
                .onStreamingError({ err ->
                    if (eventSubscription.onStreamingFailure()) {
                        onNetworkError?.invoke(err)
                    }
                })
                .post()
            eventSubscription.install(subscription)
        } catch (e: StatusRuntimeException) {
            if (eventSubscription.onStreamingFailure()) {
                onNetworkError?.invoke(e)
            }
        }
        return eventSubscription
    }

    /**
     * A [ClientRequest] instance that can be used for building client's
     * requests to the server.
     */
    private fun clientRequest(): ClientRequest {
        if (userId != null) {
            return spineClient.onBehalfOf(userId!!)
        }
        return spineClient.asGuest()
    }

    /**
     * Closes the channel without asking Spine Client to cancel all subscriptions.
     *
     * Spine Client retains subscriptions whose streams ended because the connection
     * failed. Its standard `close()` method would try to cancel these stale
     * subscriptions, which the server has already removed. Immediately shutting
     * down the channel terminates active streams without sending invalid
     * cancellation requests.
     */
    private fun closeChannel() {
        try {
            channel.shutdownNow()
                .awaitTermination(ChannelShutdownTimeoutSeconds, SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Creates and registers a recoverable data observation.
     *
     * The observation is returned as soon as it is registered, carrying
     * [initialValue]. Its initial read and subscription run in the observation
     * scope, so a slow or unresponsive server does not block the calling thread.
     *
     * When the connection is already known to be unavailable, no initial read
     * is attempted: the observation is returned waiting for a connection, and
     * the scope refreshes it once the connection is restored.
     *
     * Without [applyRemoval], removals trigger a fresh query because the observed
     * value does not have a known ID extractor.
     */
    private fun <T, U> createObservation(
        initialValue: T,
        read: () -> T,
        subscribe: (
            onUpdate: (U) -> Unit,
            onRemoval: (Any) -> Unit,
            onError: (Throwable) -> Unit
        ) -> ObservationSubscription,
        applyUpdate: (T, U) -> T,
        applyRemoval: ((T, Any) -> T)? = null
    ): DataObservation<T> {
        val observation = DataObservation(
            initialValue = initialValue,
            read = read,
            subscribe = { onChange, onInvalidated, onError ->
                subscribe(
                    { update -> onChange { value -> applyUpdate(value, update) } },
                    { id ->
                        if (applyRemoval != null) {
                            onChange { value -> applyRemoval(value, id) }
                        } else {
                            onInvalidated()
                        }
                    },
                    onError
                )
            },
            connectionStatus = { connectionStatus.value },
            requestContext = IO,
            onCancelled = observationScope::unregister,
            onRecoveryNeeded = observationScope::retryWhileConnected,
            onRefreshNeeded = { observation, generation ->
                observationScope.refresh(observation, generation)
            }
        )
        observationScope.registerAndInitialize(observation)
        return observation
    }

    /**
     * Creates an entity subscription that forwards updates, removals, and failures.
     */
    private fun <E : EntityState> subscribeTo(
        entityClass: Class<E>,
        onUpdate: (E) -> Unit,
        onRemoval: (Any) -> Unit,
        onError: (Throwable) -> Unit,
        filter: CompositeEntityStateFilter? = null
    ): ObservationSubscription {
        val request = clientRequest()
            .subscribeTo(entityClass)
            .observe(onUpdate)
            .whenNoLongerMatching(Any::class.java, onRemoval)
            .onStreamingError(onError)
        filter?.let {
            request.where(it)
        }
        val subscription = request.post()
        return ObservationSubscription {
            cancelSubscription(subscription)
        }
    }

    /**
     * Sends a best-effort cancellation request without blocking the caller.
     */
    private fun cancelSubscription(subscription: Subscription) {
        cancellationScope.launch {
            try {
                spineClient.subscriptions().cancel(subscription)
            } catch (_: Exception) {
                // The stream or channel may have failed before cancellation.
            }
        }
    }

    /**
     * Updates the content of [entities] by merging in the given [entity]
     * into it.
     *
     * The merging here is either an addition of the new item specified by
     * the [entity] parameter, or, if there's already an item that has the same
     * ID in the field identified by [extractId], a replacement of
     * the corresponding item with the one passed in [entity].
     *
     * @param entities A list to be updated.
     * @param entity An item that has to be merged into the list.
     * @param extractId A function that, given a list item, or a value of
     *   [entity], retrieves its ID.
     */
    private fun <E : EntityState> updateList(
        entities: List<E>,
        entity: E,
        extractId: (E) -> Any
    ): List<E> {
        val existingItemIndex = entities.indexOfFirst { e ->
            extractId(e) == extractId(entity)
        }

        return if (existingItemIndex != -1) {
            entities.subList(0, existingItemIndex) +
                    entity +
                    entities.subList(existingItemIndex + 1, entities.size)
        } else {
            entities + entity
        }
    }
}

/**
 * An [EventSubscription] implementation.
 *
 * @param cancelSubscriptionRequest Sends an explicit subscription cancellation
 *   request to the server.
 */
internal open class EventSubscriptionImpl(
    private val cancelSubscriptionRequest: (Subscription) -> Unit
) : EventSubscription {

    /**
     * Guards the mutable subscription state.
     */
    private val stateLock = Any()

    override val active: Boolean
        get() = synchronized(stateLock) {
            subscription != null
        }

    /**
     * A flag specifying whether the subscription has been cancelled or failed.
     *
     * It is the same as ![active] with one difference. Since the subscription
     * activation can take a notable period of time (especially with network
     * connectivity issues), it is possible that the subscription can be
     * canceled _before_ its activation completes. This property allows
     * distinguishing this scenario.
     *
     * It is only needed internally because [Client.onEvent] returns only after
     * the subscription has been activated or after its activation has failed.
     */
    internal val canceled: Boolean
        get() = synchronized(stateLock) {
            isCancelled
        }

    /**
     * Indicates whether this subscription handle has reached a terminal state.
     */
    private var isCancelled = false

    /**
     * Requests cancellation when an asynchronously created subscription is installed.
     */
    private var cancelWhenInstalled = false

    /**
     * A Spine [Subscription], which was made, or `null` if it either hasn't
     * been made yet, or cancelled already.
     */
    private var subscription: Subscription? = null

    /**
     * The currently scheduled subscription timeout, if any.
     */
    private var timeoutJob: Job? = null

    @OptIn(
        // Timeout coroutine is canceled manually on demand.
        DelicateCoroutinesApi::class
    )
    override fun withTimeout(
        timeout: Duration,
        onTimeout: suspend () -> Unit,
    ) {
        cancelTimeout()
        timeoutJob = GlobalScope.launch(IO) {
            delay(timeout)
            if (timeoutJob != null) {
                // Event subscription should be cancelled BEFORE calling the
                // timeout callback to prevent a condition when both an event
                // callback and its timeout callback have been invoked.
                cancelSubscription()

                onTimeout()

                // Timeout job should be canceled AFTER invoking a callback
                // to ensure that `onTimeout` callback's coroutine scope still
                // works normally.
                cancelTimeout()
            }
        }
    }

    /**
     * Invoked internally for the subscription to perform any operations, which
     * have to be performed whenever an expected event is emitted.
     */
    fun onEvent() {
        cancelTimeout()
    }

    /**
     * Installs a successfully created subscription unless this handle has
     * already received a failure or cancellation.
     */
    internal fun install(newSubscription: Subscription) {
        val shouldCancel = synchronized(stateLock) {
            if (!isCancelled) {
                subscription = newSubscription
                false
            } else {
                val pendingCancellation = cancelWhenInstalled
                cancelWhenInstalled = false
                pendingCancellation
            }
        }
        if (shouldCancel) {
            sendCancellationSafely(newSubscription)
        }
    }

    /**
     * Marks the subscription as inactive after its stream has failed.
     *
     * No cancellation request is sent because the server has already ended
     * and removed the failed subscription.
     *
     * @return `true` if this is the first terminal signal for the subscription.
     */
    internal fun onStreamingFailure(): Boolean {
        val failureAccepted = synchronized(stateLock) {
            if (isCancelled) {
                false
            } else {
                isCancelled = true
                cancelWhenInstalled = false
                subscription = null
                true
            }
        }
        if (failureAccepted) {
            cancelTimeout()
        }
        return failureAccepted
    }

    override fun cancel() {
        cancelSubscription()
        cancelTimeout()
    }

    /**
     * Marks this handle as cancelled and cancels an installed subscription.
     */
    private fun cancelSubscription() {
        val previousSubscription = synchronized(stateLock) {
            if (isCancelled) {
                return@synchronized null
            }
            val current = subscription
            subscription = null
            isCancelled = true
            cancelWhenInstalled = current == null
            current
        }
        if (previousSubscription != null) {
            sendCancellationSafely(previousSubscription)
        }
    }

    /**
     * Sends a best-effort cancellation request for the given [subscription].
     */
    private fun sendCancellationSafely(subscription: Subscription) {
        try {
            cancelSubscriptionRequest(subscription)
        } catch (_: Exception) {
            // The server may already have removed a failed subscription.
        }
    }

    /**
     * Cancels and clears the currently scheduled timeout.
     */
    private fun cancelTimeout() {
        if (timeoutJob != null) {
            timeoutJob?.cancel()
            timeoutJob = null
        }
    }
}
