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

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.google.protobuf.Message
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
import kotlin.time.Duration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Provides API to interact with the application server via gRPC.
 *
 * @param host The host of the application server to which client
 *   should connect.
 * @param port The port on which the application server is listening for
 *   gRPC connections.
 * @param user The callback that should return the user ID on whose behalf
 *   the `DesktopClient` should send requests to the server.
 *   If the callback return `null` the client will send requests
 *   to the server on behalf of the guest user.
 */
public class DesktopClient(
    host: String,
    port: Int,
    private val user: () -> UserId? = { null }
) : Client {
    private val spineClient: io.spine.client.Client

    init {
        val channel = ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .build()
        spineClient = io.spine.client.Client.usingChannel(channel).build()

        getRuntime().addShutdownHook(Thread {
            close()
        })
    }

    /**
     * A flag that signifies whether the connection with the server is open.
     */
    override val isOpen: Boolean get() = spineClient.isOpen

    override val userId: UserId?
        get() = user()

    /**
     * Closes the client and shuts down the connection with the server.
     */
    override fun close() {
        spineClient.close()
    }

    override fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        extractId: (E) -> Any
    ): State<List<E>> {
        val listState = mutableStateOf(listOf<E>())
        listState.value = clientRequest().select(entityClass).run()
        clientRequest()
            .subscribeTo(entityClass)
            .observe { entity ->
                runBlocking(Main) {
                    updateList(listState, entity, extractId)
                }
            }
            .post()
        return listState
    }

    override fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        extractId: (E) -> Any,
        queryFilters: CompositeQueryFilter,
        observeFilters: CompositeEntityStateFilter,
        onNext: (List<E>) -> Unit
    ) {
        val initialResult: List<E> = clientRequest()
            .select(entityClass)
            .where(queryFilters)
            .run()
        onNext(initialResult)
        val observedEntities = mutableStateOf(initialResult)
        clientRequest()
            .subscribeTo(entityClass)
            .observe { updatedEntity ->
                updateList(observedEntities, updatedEntity, extractId)
                runBlocking(Main) {
                    onNext(observedEntities.value)
                }
            }
            .where(observeFilters)
            .post()
    }

    override fun <E : EntityState> readOneAndObserve(
        entityClass: Class<E>,
        queryFilter: CompositeQueryFilter,
        observeFilter: CompositeEntityStateFilter
    ): State<E> {
        val initialResult: List<E> = clientRequest()
            .select(entityClass)
            .where(queryFilter)
            .limit(1)
            .run()
        if (initialResult.size == 0) {
            throw NoMatchingDataException(
                "No entity could be found that matches the specified criteria"
            )
        }

        val state = mutableStateOf(initialResult.get(0))

        clientRequest()
            .subscribeTo(entityClass)
            .observe {
                runBlocking(Main) {
                    state.value = it
                }
            }
            .where(observeFilter)
            .post()
        return state
    }

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
        val eventSubscription = EventSubscriptionImpl(spineClient)
        try {
            eventSubscription.subscription = clientRequest()
                .subscribeToEvent(event)
                .where(eq(field, fieldValue))
                .observe { evt ->
                    eventSubscription.onEvent()
                    onEvent(evt)
                }
                .onStreamingError({ err ->
                    if (!eventSubscription.canceled) {
                        eventSubscription.cancel()
                        onNetworkError?.invoke(err)
                    }
                })
                .post()
        } catch (e: StatusRuntimeException) {
            if (!eventSubscription.canceled) {
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
     * Updates the content of [targetList] by merging in the given [entity]
     * into it.
     *
     * The merging here is either an addition of the new item specified by
     * the [entity] parameter, or, if there's already an item that has the same
     * ID in the field identified by [extractId], a replacement of
     * the corresponding item with the one passed in [entity].
     *
     * @param targetList A [MutableState] that contains a list to be updated.
     * @param entity An item that has to be merged into the list.
     * @param extractId A function that, given a list item, or a value of
     *   [entity], retrieves its ID.
     */
    private fun <E : EntityState> updateList(
        targetList: MutableState<List<E>>,
        entity: E,
        extractId: (E) -> Any
    ) {
        val prevList = targetList.value
        val existingItemIndex = prevList.indexOfFirst { e ->
            extractId(e) == extractId(entity)
        }

        val newList = if (existingItemIndex != -1) {
            prevList.subList(0, existingItemIndex) +
                    entity +
                    prevList.subList(existingItemIndex + 1, prevList.size)
        } else {
            prevList + entity
        }

        targetList.value = newList
    }
}

/**
 * An [EventSubscription] implementation.
 *
 * @param spineClient A Spine Event Engine's [Client][io.spine.client.Client]
 *   instance where the subscription is being registered.
 */
internal open class EventSubscriptionImpl(
    private val spineClient: io.spine.client.Client
) : EventSubscription {
    override val active: Boolean get() = subscription != null

    /**
     * A flag specifying whether the subscription has been canceled.
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
    internal var canceled = false

    /**
     * A Spine [Subscription], which was made, or `null` if it either hasn't
     * been made yet, or cancelled already.
     */
    var subscription: Subscription? = null

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

    override fun cancel() {
        cancelSubscription()
        cancelTimeout()
    }

    private fun cancelSubscription() {
        if (subscription != null) {
            spineClient.subscriptions().cancel(subscription!!)
            subscription = null
        }
        canceled = true
    }

    private fun cancelTimeout() {
        if (timeoutJob != null) {
            timeoutJob?.cancel()
            timeoutJob = null
        }
    }
}
