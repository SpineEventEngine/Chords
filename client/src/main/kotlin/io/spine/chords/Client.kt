/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords

import androidx.compose.runtime.MutableState
import com.google.protobuf.Message
import io.spine.base.CommandMessage
import io.spine.base.EntityState
import io.spine.base.EventMessage
import io.spine.base.EventMessageField
import io.spine.core.UserId
import java.util.concurrent.CompletableFuture

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
     * @param entityIdField Reads the value of the entity's field that can uniquely identify
     *   an entity.
     */
    public fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        targetList: MutableState<List<E>>,
        entityIdField: (E) -> Any
    )

    /**
     * Posts a command to the server.
     *
     * @param cmd A command that has to be posted.
     */
    public fun command(cmd: CommandMessage)

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
    public suspend fun <E : EventMessage> command(
        cmd: CommandMessage,
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message
    ): E

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
    public fun <E : EventMessage> subscribeToEvent(
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message
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
        onEmit: (E) -> Unit,
        field: EventMessageField,
        fieldValue: Message
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
