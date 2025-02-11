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
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            .forAddress(
                host,
                port
            )
            .usePlaintext()
            .build()
        spineClient = io.spine.client.Client.usingChannel(channel).build()
    }

    public override val userId: UserId?
        get() = user()

    /**
     * Reads the list of entities with the [entityClass] class into [targetList]
     * and ensures that future updates to the list are reflected in [targetList]
     * as well.
     *
     * @param entityClass A class of entities that should be read and observed.
     * @param targetList A [MutableState] that contains a list whose content
     *   should be populated and kept up to date by this function.
     * @param extractId  A callback that should read the value of
     *   the entity's ID.
     */
    public override fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        targetList: MutableState<List<E>>,
        extractId: (E) -> Any
    ) {
        targetList.value = clientRequest().select(entityClass).run()

        clientRequest()
            .subscribeTo(entityClass)
            .observe { entity ->
                updateList(targetList, entity, extractId)
            }
            .post()
    }

    /**
     * Reads all entities of type [entityClass] that match the given
     * [queryFilters] and invokes the [onNext] callback with the initial list of
     * entities. Then sets up observation to receive future updates to the
     * entities, filtering the observed updates using the provided
     * [observeFilters]. Each time any entity that matches the [observeFilters]
     * changes, the [onNext] callback will be invoked again with the updated
     * list of entities.
     *
     * @param entityClass A class of entities that should be read and observed.
     * @param extractId A callback that should read the value of the
     *   entity's ID.
     * @param queryFilters Filters to apply when querying the initial list
     *   of entities.
     * @param observeFilters Filters to apply when observing updates to
     *   the entities.
     * @param onNext A callback function that is called with the list of
     *   entities after the initial query completes, and each time any of the
     *   observed entities is updated.
     */
    public override fun <E : EntityState> readAndObserve(
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
                onNext(observedEntities.value)
            }
            .where(observeFilters)
            .post()
    }

    /**
     * Retrieves an entity of the specified class with the given ID.
     *
     * @param entityClass The class of the entity to retrieve.
     * @param id The ID of the entity to retrieve.
     */
    public override fun <E : EntityState, M : Message> read(
        entityClass: Class<E>,
        id: M
    ): E? {
        val entities = clientRequest()
            .select(entityClass)
            .byId(id)
            .run()
        return entities.firstOrNull()
    }

    /**
     * Posts the given [command] to the server.
     *
     * @param command A command that has to be posted.
     * @throws ServerError If the command could not be acknowledged due to an
     *   error on the server.
     * @throws ServerCommunicationException In case of a network communication
     *   failure that has occurred during posting of the command. It is unknown
     *   whether the command has been acknowledged or no in this case.
     */
    override fun <C: CommandMessage> postCommand(command: C) {
        var error: Throwable? = null
        try {
            clientRequest()
                .command(command)
                .onServerError { msg, err: Error ->
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

    /**
     * Posts the given [command], and runs handlers for any of the consequences
     * registered in [setupConsequences].
     *
     * All registered command consequence handlers except event handlers are
     * invoked synchronously before this suspending method returns. Event
     * handlers are invoked in the provided [coroutineScope].
     *
     * @param command The command that should be posted.
     * @param coroutineScope The coroutine scope in which event handlers are to
     *   be invoked.
     * @param setupConsequences A lambda, which sets up handlers for command's
     *   consequences using the API in [CommandConsequencesScope] on which it
     *   is invoked.
     */
    public override suspend fun <C : CommandMessage> postCommand(
        command: C,
        coroutineScope: CoroutineScope,
        setupConsequences: CommandConsequencesScope<C>.() -> Unit
    ): EventSubscriptions {
        val scope = CommandConsequencesScopeImpl(command, coroutineScope)
        try {
            scope.setupConsequences()
            val allSubscriptionsSuccessful = scope.allSubscriptionsActive
            if (allSubscriptionsSuccessful) {
                scope.triggerBeforePostHandlers()
                postCommand(command)
                scope.triggerAcknowledgeHandlers()
            } else {
                scope.subscriptions.cancelAll()
            }
        } catch (e: ServerError) {
            scope.triggerServerErrorHandlers(e)
        } catch (e: ServerCommunicationException) {
            scope.triggerNetworkErrorHandlers(e)
        }
        return scope.subscriptions
    }

    override fun <E : EventMessage> onEvent(
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message,
        onNetworkError: ((Throwable) -> Unit)?,
        onEvent: (E) -> Unit
    ): EventSubscription<E> {
        val eventSubscription = EventSubscriptionImpl<E>(spineClient)
        try {
            eventSubscription.subscription = clientRequest()
                .subscribeToEvent(event)
                .where(eq(field, fieldValue))
                .observe { evt ->
                    eventSubscription.onEvent()
                    onEvent(evt)
                }
                .onStreamingError({ err ->
                    eventSubscription.cancel()
                    onNetworkError?.invoke(err)
                })
                .post()
        } catch (e: StatusRuntimeException) {
            onNetworkError?.invoke(e)
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
private class EventSubscriptionImpl<E: EventMessage>(
    private val spineClient: io.spine.client.Client
) : EventSubscription<E> {
    override val active: Boolean get() = subscription != null

    /**
     * A Spine [Subscription], which was made to supply events of type [E], or
     * `null` if it either hasn't been made yet, or cancelled already.
     */
    var subscription: Subscription? = null

    private var timeoutJob: Job? = null

    override fun withTimeout(
        timeout: Duration,
        timeoutCoroutineScope: CoroutineScope,
        onTimeout: suspend () -> Unit,
    ) {
        check(timeoutJob == null) {
            "`withTimeout` cannot be used more than once for" +
                    "the same `EventSubscription`"
        }
        timeoutJob = timeoutCoroutineScope.launch {
            delay(timeout)
            if (timeoutJob != null) {
                cancel()
                onTimeout()
            }
        }
    }

    /**
     * Invoked internally for the subsctiption to perform any operations, which
     * have to be performed whenever an expected event [E] is emitted.
     */
    fun onEvent() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    override fun cancel() {
        if (subscription != null) {
            spineClient.subscriptions().cancel(subscription!!)
            subscription = null
        }
        if (timeoutJob != null) {
            timeoutJob?.cancel()
            timeoutJob = null
        }
    }
}
