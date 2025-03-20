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

import androidx.compose.runtime.State
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

/**
 * Provides an API for interacting with the application server.
 */
 public interface Client {

    /**
     * Signifies whether the connection with the server is open.
     *
     * @see close
     */
    public val isOpen: Boolean

    /**
     * The ID of the user on whose behalf this `Client` should send requests to
     * the server.
     */
    public val userId: UserId?


    /**
     * Reads the list of entities with the [entityClass] class and returns the
     * respective [State], which is maintained to contain an up-to-date list.
     *
     * By default, this method just launches an asynchronous reading and returns
     * immediately. Setting [awaitInitialList] to `true` will make the
     * method to wait for reading the list to complete before returning.
     *
     * @param E A type of entities being read and observed.
     *
     * @param entityClass A class of entities that should be read and observed.
     * @param extractId A callback that should read the value of
     *   the entity's ID.
     * @param awaitInitialList Setting to `true` makes the method to wait for
     *   reading the current entity list before returning.
     * @return A [State] that contains a list whose content should be populated
     *   and kept up to date by this function.
     */
    public fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        extractId: (E) -> Any,
        awaitInitialList: Boolean = false
    ): State<List<E>>

    /**
     * Returns a [State], which contains an up-to-date entity value according to
     * the given filter parameters.
     *
     * @param E A type of entity being read and observed.
     *
     * @param entityClass A class of entity value that should be
     *   read and observed.
     * @param queryFilter Filter to use for querying the initial entity value.
     * @param observeFilter Filter to use for observing entity updates.
     * @param awaitInitialValue Setting to `true` makes the method to wait for
     *   reading the current entity value before returning.
     * @return A [State] that contains an up-to-date entity value according to
     *   the given [observeFilter].
     */
    public fun <E : EntityState> readAndObserveFirst(
        entityClass: Class<E>,
        queryFilter: CompositeQueryFilter,
        observeFilter: CompositeEntityStateFilter,
        awaitInitialValue: Boolean = false
    ): State<E?>

    /**
     * Reads all entities of type [entityClass] that match the given
     * [queryFilters] and invokes the [onNext] callback with the initial list of
     * entities. Then sets up observation to receive future updates to the
     * entities, filtering the observed updates using the provided
     * [observeFilters]. Each time any entity that matches the [observeFilters]
     * changes, the [onNext] callback will be invoked again with the updated
     * list of entities.
     *
     * By default, this method just launches an asynchronous reading and returns
     * immediately. Setting [awaitInitialList] to `true` will make the
     * method to wait for reading the list to complete, and invoking the
     * [onNext] callback for the first time before returning.
     *
     * @param entityClass A class of entities that should be read and observed.
     * @param extractId A callback that should read the value of the entity's ID.
     * @param queryFilters Filters to apply when querying the initial list
     *   of entities.
     * @param observeFilters Filters to apply when observing updates to
     *   the entities.
     * @param awaitInitialList Setting to `true` makes the method to wait for
     *   reading the current entity list before returning.
     * @param onNext A callback function that is called with the list of
     *   entities after the initial query completes, and each time any of the
     *   observed entities is updated.
     */
    public fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        extractId: (E) -> Any,
        queryFilters: CompositeQueryFilter,
        observeFilters: CompositeEntityStateFilter,
        awaitInitialList: Boolean,
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
     * specified with [consequences].
     *
     * The command is posted asynchronously and all registered command
     * consequence handlers are invoked asynchronously as well.
     *
     * Here's a simple usage example, which just includes a subscription to an
     * event expected to be emitted as a consequence of posting
     * the given command:
     * ```
     *     val command: ImportItem = createCommand()
     *     val eventSubscriptions = app.client.postCommand(command, consequences {
     *
     *         // Subscribe to an event that is expected to be emitted as
     *         // a consequence of this specific command.
     *         onEvent(
     *             ItemImported::class.java,
     *             ItemImported.Field.itemId(),
     *             command.itemId
     *         ) {
     *             showMessage("Item imported")
     *         }
     *     })
     * ```
     * Note that the `ItemImported` event with the given `itemId` field value is
     * expected indefinitely in this example, and it's also possible to specify
     * an event waiting timeout period as shown in the example below. If the
     * event of the specified type and specified field value is emitted several
     * times, then the respective handler will be invoked several times as well,
     * until the respective subscription is
     * [cancelled][EventSubscription.cancel]. All subscriptions made by the
     * given `CommandConsequences` instance can be cancelled by invoking the
     * [cancelAll][EventSubscriptions.cancelAll] method on the
     * [EventSubscriptions] instance returned by the `postCommand` function.
     *
     * Similarly, you can subscribe to any number of events, including
     * rejection events according to any respective consequences expected.
     *
     * Here's a more complex example, which also demonstrates tracking the
     * command's posting progress as well as handling various errors and
     * timeout conditions:
     * ```
     * val command: ImportItem = createCommand()
     * val inProgress: Boolean by remember { mutableStateOf(false) }
     *
     * app.client.postCommand(command, consequences {
     *     onBeforePost {
     *         inProgress = true
     *     }
     *     onServerError {
     *         showMessage("Unexpected server error has occurred.")
     *         inProgress = false
     *     }
     *     onEvent(
     *         ItemImported::class.java,
     *         ItemImported.Field.itemId(),
     *         command.itemId
     *     ) {
     *         showMessage("Item imported")
     *         inProgress = false
     *     }.withTimeout(30.seconds) {
     *         showMessage("The operation takes unexpectedly long to process. " +
     *                 "Please check the status of its execution later.")
     *         inProgress = false
     *     }
     *     onEvent(
     *         ItemAlreadyExists::class.java,
     *         ItemAlreadyExists.Field.itemId(),
     *         command.itemId
     *     ) {
     *         showMessage("Item already exists: ${command.itemName.value}")
     *         inProgress = false
     *     }
     *     onNetworkError {
     *         showMessage("Server connection failed.")
     *         inProgress = false
     *     }
     * })
     * ```
     *
     * See the [CommandConsequencesScope] documentation for the description of
     * declarations supported when creating a [CommandConsequences] instance.
     *
     * @param command The command that should be posted.
     * @param consequences A configuration of possible consequences and their
     *   respective  handlers.
     * @return An object, which allows managing (e.g. cancelling) all event
     *   subscriptions made by this method according to the
     *   [consequences] parameter.
     * @see CommandConsequencesScope
     */
    public fun <C : CommandMessage> postCommand(
        command: C,
        consequences: CommandConsequences<C>
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
     * @param onEvent A callback, which will be invoked when the specified event
     *   is emitted.
     * @return An [EventSubscription] object, which represents the subscription
     *   that was made.
     */
    public fun <E : EventMessage> onEvent(
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message,
        onNetworkError: ((Throwable) -> Unit)? = null,
        onEvent: (E) -> Unit
    ): EventSubscription

    /**
     * Closes the client and shuts down the connection with the server.
     *
     * This will also cancel any subscriptions made with this client if they
     * haven't been closed explicitly. Once the client is closed, it cannot be
     * used anymore.
     *
     * @see isOpen
     */
    public fun close()
}

/**
 * A subscription for an event.
 */
public interface EventSubscription {

    /**
     * Returns `true`, if the subscription is active (waiting for events).
     */
    public val active: Boolean

    /**
     * Starts the countdown period [timeout] of waiting for the next event, and
     * invokes the provided [onTimeout] handler if the event is not emitted
     * during this period of time.
     *
     * If an event that matches the subscription criteria is not emitted in
     * the [timeout] period since this method is invoked, the [onTimeout]
     * callback is invoked, and the subscription is cancelled.
     *
     * Invoking [withTimeout] repeatedly before the [timeout] of the previous
     * `withTimeout` call has expired cancels the previous timeout period and
     * starts the countdown period specified with the [timeout] parameter anew.
     *
     * @param timeout A maximum period of time that the subscribed event should
     *   be waited for.
     * @param onTimeout A callback, which will be invoked if event is not
     *   emitted within the [timeout] period after this method is called.
     */
    public fun withTimeout(
        timeout: Duration,
        onTimeout: suspend () -> Unit
    )

    /**
     * Cancels the subscription.
     *
     * After a subscription is canceled, it stops receiving notifications about
     * emitted events.
     */
    public fun cancel()
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
