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
import kotlinx.coroutines.CoroutineScope

/**
 * Provides an API for interacting with the application server.
 */
 public interface Client {

    /**
     * The ID of the user on whose behalf this `Client` should send requests to
     * the server.
     */
    public val userId: UserId?

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
    public fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        targetList: MutableState<List<E>>,
        extractId: (E) -> Any
    )

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
     * @param extractId A callback that should read the value of the entity's ID.
     * @param queryFilters Filters to apply when querying the initial list
     *   of entities.
     * @param observeFilters Filters to apply when observing updates to
     *   the entities.
     * @param onNext A callback function that is called with the list of
     *   entities after the initial query completes, and each time any of the
     *   observed entities is updated.
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
     * @param command A command that has to be posted.
     * @throws ServerCommunicationException If a net work error has occurred
     *   when posting a command.
     * @throws ServerError If the command couldn't be acknowledged due to an
     *   error on the server.
     */
    public fun <C: CommandMessage> postCommand(command: C)

    /**
     * Posts the given [command], and runs handlers for any of the consequences
     * registered in [setupConsequences].
     *
     * All registered command consequence handlers except event handlers are
     * invoked synchronously before this suspending method returns. Event
     * handlers are invoked in the provided [coroutineScope].
     *
     * See the description and an example of specifying command consequence
     * handlers in the [CommandConsequencesScope] documentation.
     *
     * @param command The command that should be posted.
     * @param coroutineScope The coroutine scope in which event handlers are to
     *   be invoked.
     * @param setupConsequences A lambda, which sets up handlers for command's
     *   consequences using the API in [CommandConsequencesScope] on which it
     *   is invoked.
     * @return An object, which allows managing (e.g. cancelling) all event
     *   subscriptions made by this method as specified with the
     *   [setupConsequences] parameter.
     * @see CommandConsequencesScope
     */
    public suspend fun <C : CommandMessage> postCommand(
        command: C,
        coroutineScope: CoroutineScope,
        setupConsequences: CommandConsequencesScope<C>.() -> Unit
    ): EventSubscriptions

    /**
     * Subscribes to events with a given class and a given field value (which
     * would typically be the event's unique identifier field).
     *
     * The subscription remains active by waiting for events that satisfy the
     * specified criteria until the [cancel][EventSubscription.cancel] method
     * is invoked in the returned [EventSubscription] instance.
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
     * @return An [EventSubscription] object, which represents the subscription
     *   that was made.
     */
    public fun <E : EventMessage> onEvent(
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message,
        onNetworkError: ((Throwable) -> Unit)? = null,
        onEvent: (E) -> Unit
    ): EventSubscription<E>
}

/**
 * A subscription for an event.
 *
 * @param E Type of subscribed event message.
 */
public interface EventSubscription<E: EventMessage> {

    /**
     * Returns `true`, if the subscription is active (waiting for event/s).
     */
    public val active: Boolean

    /**
     * Starts the countdown period [timeout] of waiting for the next event, and
     * invokes the provided [onTimeout] handler if the event is not emitted
     * during this period of time.
     *
     * If an event that matches the subscription criteria is not emitted during
     * the [timeout] period since this method is invoked, the [onTimeout]
     * callback is invoked, and the subscription is cancelled.
     *
     * @param timeout A maximum period of time that the subscribed event should
     *   be waited for.
     * @param timeoutCoroutineScope A [CoroutineScope] used to launch
     *   a coroutine for waiting a [timeout] period and invoking
     *   the [onTimeout] callback.
     * @param onTimeout An optional callback, which will be invoked if event is
     *   not emitted within the [timeout] period after this method is called.
     */
    public fun withTimeout(
        timeout: Duration,
        timeoutCoroutineScope: CoroutineScope,
        onTimeout: suspend () -> Unit
    )

    /**
     * Cancels the subscription if it is active.
     *
     * After a subscription is canceled, it stops receiving notifications about
     * emitted events.
     *
     * @return `true` if the subscription was active and was cancelled, and
     *   `false` if the subscription wasn't active.
     */
    public fun cancel(): Boolean
}

/**
 * Represents a set of related subscriptions, e.g. the ones made as a result of
 * [Client.postCommand] method.
 */
public interface EventSubscriptions {

    /**
     * Cancels all subscriptions represented by this object.
     */
    public fun cancelAll()
}

/**
 * Signifies a failure that has occurred while communicating with the server.
 */
public class ServerCommunicationException(cause: Throwable) : RuntimeException(cause) {
    public companion object {
        private const val serialVersionUID: Long = -5438430153458733051L
    }
}

/**
 * Signifies an error that has occurred on the server (e.g. a validation error).
 *
 * @property error Information about the error that has occurred on the server.
 */
public class ServerError(public val error: Error) : RuntimeException(error.message) {
    public companion object {
        private const val serialVersionUID: Long = -5438430153458733051L
    }
}
