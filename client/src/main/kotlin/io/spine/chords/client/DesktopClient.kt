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
import com.google.protobuf.Message
import io.grpc.ManagedChannelBuilder
import io.spine.base.CommandMessage
import io.spine.base.EntityState
import io.spine.base.Error
import io.spine.base.EventMessage
import io.spine.base.EventMessageField
import io.spine.client.ClientRequest
import io.spine.client.EventFilter.eq
import io.spine.core.UserId
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
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
     * @param targetList A [MutableState] that contains a list whose content
     *   should be populated and kept up to date by this function.
     * @param entityIdField Reads the value of the entity's field that can
     *   uniquely identify an entity.
     */
    public override fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        targetList: MutableState<List<E>>,
        entityIdField: (E) -> Any
    ) {
        targetList.value = clientRequest().select(entityClass).run()

        clientRequest()
            .subscribeTo(entityClass)
            .observe { entity ->
                updateList(targetList, entity, entityIdField)
            }
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
        if (entities.isEmpty()) {
            return null
        }
        return entities[0]
    }

    /**
     * Posts a command to the server.
     *
     * @param cmd A command that has to be posted.
     * @throws CommandPostingError If some error has occurred during posting and
     *   acknowledging the command on the server.
     */
    override fun command(cmd: CommandMessage) {
        var error: CommandPostingError? = null
        clientRequest()
            .command(cmd)
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
     * Posts a command to the server and awaits for the specified event
     * to arrive.
     *
     * @param cmd A command that has to be posted.
     * @param event A class of event that has to be waited for after posting
     *   the command.
     * @param field A field that should be used for identifying the event to be
     *   awaited for.
     * @param fieldValue A value of the field that identifies the event to be
     *   awaited for.
     * @return An event specified in the parameters, which was emitted in
     *   response to the command.
     * @throws kotlinx.coroutines.TimeoutCancellationException
     *   If the event doesn't arrive within a reasonable timeout defined
     *   by the implementation.
     */
    override suspend fun <E : EventMessage> command(
        cmd: CommandMessage,
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message
    ): E = coroutineScope {
        val eventSubscription = subscribeToEvent(event, field, fieldValue)
        command(cmd)
        eventSubscription.awaitEvent()
    }

    /**
     * Subscribes to an event with a given class and a given field value (which
     * would typically be the event's unique identifier field).
     *
     * @param event A class of event that has to be subscribed to.
     * @param field A field that should be used for identifying the event to be
     *   subscribed to.
     * @param fieldValue A value of the field that identifies the event to be
     *   subscribed to.
     * @return A [CompletableFuture] instance that is completed when the event
     *   specified by the parameters arrives.
     */
    override fun <E : EventMessage> subscribeToEvent(
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message
    ): EventSubscription<E> {
        val eventReceival = CompletableFuture<E>()
        observeEvent(
            event = event,
            field = field,
            fieldValue = fieldValue) { evt ->
                eventReceival.complete(evt)
            }
        return FutureEventSubscription(eventReceival)
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
     * ID in the field identified by [entityIdField], a replacement of
     * the corresponding item with the one passed in [entity].
     *
     * @param targetList A [MutableState] that contains a list to be updated.
     * @param entity An item that has to be merged into the list.
     * @param entityIdField A function that, given a list item, or a value of
     *   [entity], returns the value of its field, which uniquely identifies
     *   the respective instance.
     */
    private fun <E : EntityState> updateList(
        targetList: MutableState<List<E>>,
        entity: E,
        entityIdField: (E) -> Any
    ) {
        val prevList = targetList.value
        val existingItemIndex = prevList.indexOfFirst { e ->
            entityIdField(e) == entityIdField(entity)
        }

        val newList = if (existingItemIndex != -1) {
            prevList.subList(0, existingItemIndex - 1) +
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
    private val future: CompletableFuture<E>
) : EventSubscription<E> {

    override suspend fun awaitEvent(): E {
        return withTimeout(ReactionTimeoutMillis) { future.await() }
    }
}
