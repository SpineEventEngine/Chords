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
import kotlinx.coroutines.flow.StateFlow

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
     * The current status of the connection with the server.
     *
     * This status is independent from [isOpen]. An open client can temporarily
     * have an unavailable connection and reconnect later.
     */
    public val connectionStatus: StateFlow<ConnectionStatus>

    /**
     * The ID of the user on whose behalf this `Client` should send requests to
     * the server.
     */
    public val userId: UserId?

    /**
     * Reads the list of entities with the [entityClass] class and returns an
     * observation that maintains an up-to-date list.
     *
     * This function returns without waiting for the server. The observation is
     * returned with an empty list, and the list read from the server appears in
     * it once the initial read and subscription complete. Until then its status
     * is [DataObservationStatus.Refreshing], or
     * [DataObservationStatus.WaitingForConnection] when the connection is
     * already known to be unavailable.
     *
     * If the connection with the server is lost, the returned observation
     * retains the last received list. It automatically re-reads the complete
     * list and creates a new subscription when the connection is restored.
     * Call [DataObservation.cancel] when the observation is no longer needed.
     *
     * @param E A type of entities being read and observed.
     *
     * @param entityClass A class of entities that should be read and observed.
     * @param extractId A callback that should read the value of
     *   the entity's ID.
     * @return An observation that contains the current list and its
     *   observation status.
     */
    public fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        extractId: (E) -> Any
    ): DataObservation<List<E>>

    /**
     * Reads all entities of type [entityClass] that match the given
     * [queryFilter]. Then sets up observation to receive future updates to the
     * entities, filtering the observed updates using the provided
     * [observeFilter].
     *
     * This function returns without waiting for the server. The observation is
     * returned with an empty list, and the list read from the server appears in
     * it once the initial read and subscription complete. Until then its status
     * is [DataObservationStatus.Refreshing], or
     * [DataObservationStatus.WaitingForConnection] when the connection is
     * already known to be unavailable.
     *
     * If the connection with the server is lost, the returned observation
     * retains the last received list. It automatically re-reads the complete
     * list and creates a new subscription when the connection is restored.
     * Call [DataObservation.cancel] when the observation is no longer needed.
     *
     * @param E A type of entities being read and observed.
     *
     * @param entityClass A class of entities that should be read and observed.
     * @param extractId A callback that should read the value of the entity's ID.
     * @param queryFilter A filter to apply when querying the initial list
     *   of entities.
     * @param observeFilter A filter to apply when observing updates to
     *   the entities, whose criteria should match the ones in [queryFilter].
     * @return An observation that contains the current list and its
     *   observation status.
     */
    public fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        extractId: (E) -> Any,
        queryFilter: CompositeQueryFilter,
        observeFilter: CompositeEntityStateFilter
    ): DataObservation<List<E>>

    /**
     * Returns an observation that maintains an up-to-date nullable entity value
     * according to the given filter parameters.
     *
     * Note the following specifics of how special cases are handled:
     * - If more than one entity matches the criteria specified by [queryFilter]
     *   or [observeFilter], the returned observation gets the first matching
     *   value.
     * - If no entries match the specified criteria, the value is `null`.
     *
     * This function returns without waiting for the server. The observation is
     * returned with a `null` value, and the value read from the server appears
     * in it once the initial read and subscription complete. Until then its
     * status is [DataObservationStatus.Refreshing], or
     * [DataObservationStatus.WaitingForConnection] when the connection is
     * already known to be unavailable.
     *
     * If the connection with the server is lost, the returned observation
     * retains the last received value. It automatically re-reads the value and
     * creates a new subscription when the connection is restored.
     * Call [DataObservation.cancel] when the observation is no longer needed.
     *
     * @param E A type of entity being read and observed.
     *
     * @param entityClass A class of entity value that should be
     *   read and observed.
     * @param queryFilter A filter to use for querying the initial entity value.
     * @param observeFilter A filter to use for observing entity updates, whose
     *   criteria should match the ones in [queryFilter].
     * @return An observation that contains an up-to-date entity value according
     *   to the given criteria, or `null` if no matching entity exists.
     */
    public fun <E : EntityState> readOneAndObserve(
        entityClass: Class<E>,
        queryFilter: CompositeQueryFilter,
        observeFilter: CompositeEntityStateFilter
    ): DataObservation<E?>

    /**
     * Returns an observation that maintains an up-to-date entity value
     * according to the given filter parameters.
     *
     * This overload guarantees a non-null value by using [defaultValue] when no
     * entity matches. If several entities match, the first one is used.
     *
     * This function returns without waiting for the server. The observation is
     * returned with [defaultValue], and the value read from the server appears
     * in it once the initial read and subscription complete. Until then its
     * status is [DataObservationStatus.Refreshing], or
     * [DataObservationStatus.WaitingForConnection] when the connection is
     * already known to be unavailable.
     *
     * If the connection with the server is lost, the returned observation
     * retains the last received value. It automatically re-reads the value and
     * creates a new subscription when the connection is restored.
     * Call [DataObservation.cancel] when the observation is no longer needed.
     *
     * @param E A type of entity being read and observed.
     *
     * @param entityClass A class of entity value that should be
     *   read and observed.
     * @param queryFilter A filter to use for querying the initial entity value.
     * @param observeFilter A filter to use for observing entity updates, whose
     *   criteria should match the ones in [queryFilter].
     * @param defaultValue A value to use when no matching records were found.
     * @return An observation that always contains either a matching entity or
     *   [defaultValue].
     */
    public fun <E : EntityState> readOneAndObserve(
        entityClass: Class<E>,
        queryFilter: CompositeQueryFilter,
        observeFilter: CompositeEntityStateFilter,
        defaultValue: E
    ): DataObservation<E>

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
     * @throws ServerError If the command couldn't be acknowledged due to an
     *   error on the server.
     * @throws ServerCommunicationException In case of a network communication
     *   failure that has occurred during posting of the command. It is unknown
     *   whether the command has been acknowledged in this case.
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
     *   respective handlers.
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
 * The current connection status of a [Client].
 */
public enum class ConnectionStatus {

    /**
     * The connection is not currently used.
     */
    IDLE,

    /**
     * The client is establishing a connection.
     */
    CONNECTING,

    /**
     * The connection is ready to carry requests.
     */
    CONNECTED,

    /**
     * The connection is temporarily unavailable.
     */
    UNAVAILABLE,

    /**
     * The client is permanently closed and will not try to reconnect.
     *
     * This status lets applications distinguish a deliberate client shutdown
     * from a temporary connection failure and stop showing reconnection UI.
     */
    CLOSED
}

/**
 * The status of a [DataObservation].
 */
public sealed class DataObservationStatus {

    /**
     * The observation is receiving updates.
     */
    public object Active : DataObservationStatus()

    /**
     * The observation is retaining its last value until the connection is
     * restored.
     */
    public object WaitingForConnection : DataObservationStatus()

    /**
     * The observation is re-reading its data and creating a new subscription.
     */
    public object Refreshing : DataObservationStatus()

    /**
     * The observation could not read data or create a subscription.
     *
     * This status is terminal for automatic connection recovery because the
     * failure was not classified as a temporary connection loss. After
     * resolving the cause, call [DataObservation.refresh] explicitly to retry.
     *
     * @property error The failure that has occurred.
     */
    public data class Failed(
        public val error: Throwable
    ) : DataObservationStatus()

    /**
     * The observation has been cancelled permanently.
     */
    public object Cancelled : DataObservationStatus()
}

/**
 * Maintains live server data and recovers it after a temporary connection
 * failure.
 *
 * A `DataObservation` is also a Compose [State], so its current [value] can be
 * read directly or with Kotlin property delegation. A newly created observation
 * is readable right away: it carries its initial or default value until the
 * first read from the server completes. An observation in
 * [DataObservationStatus.WaitingForConnection] automatically re-reads the
 * complete value and creates a new subscription when the server connection is
 * restored. An observation in [DataObservationStatus.Failed] is not retried
 * automatically and requires an explicit [refresh] call.
 *
 * The caller owns the observation lifecycle and should call [cancel] when the
 * observation is no longer needed.
 *
 * ## Why this is an interface
 *
 * The only production implementation is the internal `DataObservationImpl`,
 * which cannot appear in this public API at all. The separation is not an
 * extension point for third parties; it exists because the implementation
 * carries detail that consumers must not depend on:
 *
 * - `DataObservationImpl<T, U>` has a second type parameter, `U`, for the type
 *   of an individual server update, which differs from the observed value
 *   (a list observation applies single-entity updates to a `List`). This
 *   interface is what erases `U` from the public signature.
 * - The implementation exposes lifecycle operations that only the client's
 *   observation manager may call. Publishing them would let a consumer corrupt
 *   the recovery bookkeeping.
 *
 * Do not implement this interface outside the library: new members may be added
 * to it in future versions.
 *
 * @param T The type of the observed value.
 */
public interface DataObservation<out T> : State<T> {

    /**
     * The current observation status.
     */
    public val status: State<DataObservationStatus>

    /**
     * Re-reads the complete value and replaces the current subscription.
     *
     * Request failures are exposed through [status] instead of being thrown
     * from this function. Coroutine cancellation is propagated to the caller
     * without being converted into an observation failure.
     */
    public suspend fun refresh()

    /**
     * Permanently cancels this observation.
     *
     * A cancelled observation is excluded from automatic recovery and cannot
     * be refreshed again.
     */
    public fun cancel()
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
        /**
         * Identifies the serialized form of this exception.
         */
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
        /**
         * Identifies the serialized form of this exception.
         */
        private const val serialVersionUID: Long = -5438430153458733051L
    }
}
