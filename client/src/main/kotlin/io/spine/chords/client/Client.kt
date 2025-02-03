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
import io.spine.base.CommandMessage
import io.spine.base.EntityState
import io.spine.base.Error
import io.spine.base.EventMessage
import io.spine.base.EventMessageField
import io.spine.client.CompositeEntityStateFilter
import io.spine.client.CompositeQueryFilter
import io.spine.core.UserId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope

/**
 * Provides an API for interacting with the application server.
 */
 public interface Client {

    /**
     * The ID of the user on whose behalf this `Client` should send requests to the server.
     */
    public val userId: UserId?

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
    public fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        targetList: MutableState<List<E>>,
        extractId: (E) -> Any
    )

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
    public fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        extractId: (E) -> Any,
        queryFilters: CompositeQueryFilter,
        observeFilters: CompositeEntityStateFilter,
        onNext: (List<E>) -> Unit
    )

    /**
     * Retrieves an entity of the specified class with the given ID.
     *
     * @param entityClass The class of the entity to retrieve.
     * @param id The ID of the entity to retrieve.
     */
    public fun <E : EntityState, M : Message> read(
        entityClass: Class<E>,
        id: M
    ): E?

    /**
     * Posts a command to the server.
     *
     * @param cmd A command that has to be posted.
     * @throws CommandPostingError If some error has occurred during posting and
     *   acknowledging the command on the server.
     */
    public fun <C: CommandMessage> postCommand(cmd: C)

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
    public suspend fun <C : CommandMessage> postCommand(
        command: C,
        coroutineScope: CoroutineScope,
        consequenceHandlers: CommandConsequencesScope<C>.() -> Unit
    )

    /**
     * Subscribes to an event with a given class and a given field value (which
     * would typically be the event's unique identifier field).
     *
     * The event subscription can be awaited for in two ways:
     * - By specifying the [onEvent] parameter, which can be useful for cases
     *   when receiving an asynchronous event is suitable.
     * - By invoking the [awaitEvent][EventSubscription.awaitEvent] method on
     *   the returned [EventSubscription] object, which can be useful if you
     *   need waiting to be a part of sequential execution in
     *   a suspending function.
     *
     * The maximum time that the event is being waited for after calling this
     * method is defined by the [timeout] parameter. After this period elapses,
     * the optional [onTimeout] callback is invoked.
     *
     *
     * @param event A class of event that has to be subscribed to.
     * @param field A field that should be used for identifying the event to be
     *   subscribed to.
     * @param fieldValue A value of the field that identifies the event to be
     *   subscribed to.
     * @param onEvent An optional callback, which will be invoked when the
     *   specified event is emitted (as long as the event is emitted within
     *   the [timeout] period after this method is called).
     * @param onTimeout An optional callback, which will be invoked if event is
     *   not emitted within the [timeout] period after this method is called.
     * @return An [EventSubscription] object, which represents the subscription
     *   that was made.
     */
    @Suppress(
        // Considering all parameters relevant, esp. considering optional ones.
        "LongParameterList"
    )
    public fun <E : EventMessage> subscribeToEvent(
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message,
        onEvent: ((E) -> Unit)? = null,
        onTimeout: (() -> Unit)? = null,
        timeout: Duration = 60.seconds
    ): EventSubscription<E>

    /**
     * Observes the provided event.
     *
     * @param event A class of event to observe.
     * @param onEmit A callback triggered when the desired event is emitted.
     * @param field A field used for identifying the observed event.
     * @param fieldValue An identifying field value of the observed event.
     */
    public fun <E : EventMessage> observeEvent(
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message,
        onEmit: (E) -> Unit
    )
}

/**
 * A subscription for an event.
 *
 * @param E Type of subscribed event message.
 */
public interface EventSubscription<E: EventMessage> {

    /**
     * Awaits for the event to arrive, and returns the respective event.
     *
     * If invoked after the event has already arrived, this method returns
     * immediately with the respective event.
     *
     * @return An event that is expected to arrive with this subscription.
     * @throws kotlinx.coroutines.TimeoutCancellationException
     *   If the event doesn't arrive within a reasonable timeout defined
     *   by the implementation.
     */
    public suspend fun awaitEvent(): E
}

/**
 * Signifies an error that has occurred during the process of posting the
 * command and acknowledging it on the server.
 */
public open class CommandPostingError(message: String? = null, cause: Throwable? = null) :
    RuntimeException(message, cause)
{
    public companion object {
        private const val serialVersionUID: Long = 3555883899622560720L
    }
}

/**
 * Signifies an error that has occurred when delivering events.
 */
public class StreamingError(error: Throwable) : CommandPostingError(cause = error) {
    public companion object {
        private const val serialVersionUID: Long = -5438430153458733051L
    }
}

/**
 * Signifies an error that has occurred on the server (e.g. a validation error).
 */
public class ServerError(error: Error) : CommandPostingError(error.message) {
    public companion object {
        private const val serialVersionUID: Long = -5438430153458733051L
    }
}
