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

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.google.protobuf.Message
import io.grpc.ManagedChannelBuilder
import io.spine.base.CommandMessage
import io.spine.base.EntityState
import io.spine.base.Error
import io.spine.base.EventMessage
import io.spine.base.EventMessageField
import io.spine.chords.client.appshell.client
import io.spine.chords.core.appshell.app
import io.spine.client.ClientRequest
import io.spine.client.CompositeEntityStateFilter
import io.spine.client.CompositeQueryFilter
import io.spine.client.EventFilter.eq
import io.spine.core.UserId
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * A period during which a server should provide a reaction in a normally
 * functioning system (e.g., emit an event in response to a command).
 */
private const val ReactionTimeoutMillis = 15_000L

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
     * @param targetList A [MutableState] that contains a list whose content should be
     *   populated and kept up to date by this function.
     * @param extractId  A callback that should read the value of the entity's ID.
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
     * Reads all entities of type [entityClass] that match the given [queryFilters] and invokes the
     * [onNext] callback with the initial list of entities. Then sets up observation to receive
     * future updates to the entities, filtering the observed updates using the provided [observeFilters].
     * Each time any entity that matches the [observeFilters] changes, the [onNext] callback
     * will be invoked again with the updated list of entities.
     *
     * @param entityClass A class of entities that should be read and observed.
     * @param extractId A callback that should read the value of the entity's ID.
     * @param queryFilters Filters to apply when querying the initial list of entities.
     * @param observeFilters Filters to apply when observing updates to the entities.
     * @param onNext A callback function that is called with the list of entities after the initial
     *   query completes, and each time any of the observed entities is updated.
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
     * @throws CommandPostingError If some error has occurred during posting and
     *   acknowledging the command on the server.
     */
    override fun <C: CommandMessage> postCommand(command: C) {
        var error: CommandPostingError? = null
        clientRequest()
            .command(command)
            .onServerError { msg, err: Error ->
                error = ServerError(err)
            }
            .onStreamingError { err: Throwable ->
                error = StreamingError(err)
            }
            .postAndForget()
        if (error != null) {
            throw error!!
        }
    }

    /**
     * Posts the given [command], and runs handlers for any of the consequences
     * registered in [consequenceHandlers].
     *
     * All registered command consequence handlers except event handlers are
     * invoked synchronously before this suspending method returns. Event
     * handlers are invoked in the provided [coroutineScope].
     *
     * @param command The command that should be posted.
     * @param coroutineScope The coroutine scope in which event handlers are to
     *   be invoked.
     */
    public override suspend fun <C : CommandMessage> postCommand(
        command: C,
        coroutineScope: CoroutineScope,
        consequenceHandlers: CommandConsequencesScope<C>.() -> Unit
    ) {
        val scope = CommandConsequencesScopeImpl(command, coroutineScope)
        scope.consequenceHandlers()

        try {
            scope.beforePostHandlers.forEach { it(command) }
            app.client.postCommand(command)
            scope.acknowledgeHandlers.forEach { it(command) }
        } catch (e: ServerError) {
            if (scope.postServerErrorHandlers.isEmpty()) {
                throw IllegalStateException(
                    "No `onPostServerError` handlers are registered for command: " +
                            command.javaClass.simpleName,
                    e
                )
            }
            scope.postServerErrorHandlers.forEach { it(command, e) }
        } catch (e: StreamingError) {
            if (scope.postStreamingErrorHandlers.isEmpty()) {
                throw IllegalStateException(
                    "No `onPostStreamingError` handlers are registered for command: " +
                            command.javaClass.simpleName,
                    e
                )

            }
            scope.postStreamingErrorHandlers.forEach { it(command, e) }
        }
    }

    override fun <E : EventMessage> subscribeToEvent(
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message,
        onEvent: ((E) -> Unit)?,
        onTimeout: (() -> Unit)?,
        timeout: Duration
    ): EventSubscription<E> {
        val eventReceival = CompletableFuture<E>()
        System.currentTimeMillis()
        val futureEventSubscription = FutureEventSubscription(eventReceival)
        GlobalScope.launch {
            delay(timeout)
            if (!eventReceival.isDone) {
                onTimeout?.invoke()
                eventReceival.cancel(false)
            }
        }
        observeEvent(
            event = event,
            field = field,
            fieldValue = fieldValue) { evt ->
                if (!eventReceival.isDone) {
                    eventReceival.complete(evt)
                    futureEventSubscription.onEvent?.invoke(evt)
                }
            }
        return futureEventSubscription
    }

    /**
     * Observes the provided event.
     *
     * @param event A class of event to observe.
     * @param onEmit A callback triggered when the desired event is emitted.
     * @param field A field used for identifying the observed event.
     * @param fieldValue An identifying field value of the observed event.
     */
    override fun <E : EventMessage> observeEvent(
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message,
        onEmit: (E) -> Unit
    ) {
        clientRequest()
            .subscribeToEvent(event)
            .where(eq(field, fieldValue))
            .observe { evt ->
                onEmit(evt)
            }
            .post()
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
     * @param extractId A function that, given a list item, or a value of [entity],
     *   retrieves its ID.
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
 * An [EventSubscription] implementation that is based on
 * a [CompletableFuture] instance.
 *
 * @param future A `CompletableFuture`, which is expected to provide
 *   the respective event.
 */
private class FutureEventSubscription<E: EventMessage>(
    private val future: CompletableFuture<E>,
    /**
     * A callback, which is invoked when the subscribed event is emitted.
     */
    val onEvent: ((E) -> Unit)? = null
) : EventSubscription<E> {

    override suspend fun awaitEvent(): E {
        return withTimeout(ReactionTimeoutMillis) { future.await() }
    }

}
