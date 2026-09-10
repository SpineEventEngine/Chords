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
import io.spine.base.CommandMessage
import io.spine.base.EntityState
import io.spine.base.EventMessage
import io.spine.base.EventMessageField
import io.spine.client.CompositeEntityStateFilter
import io.spine.client.CompositeQueryFilter
import io.spine.core.UserId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides controllable observations without opening a server connection in component tests.
 */
internal object TestClient : Client {

    /**
     * Supplies the observation requested by the current test.
     */
    var observe: ((Class<*>) -> DataObservation<*>)? = null

    /**
     * Indicates that the stub accepts observation requests.
     */
    override val isOpen: Boolean = true
    /**
     * Keeps observation refreshes independent of a real connection.
     */
    override val connectionStatus: StateFlow<ConnectionStatus> =
        MutableStateFlow(ConnectionStatus.CONNECTED)
    /**
     * Tests do not require an authenticated user.
     */
    override val userId: UserId? = null

    /**
     * Provides only the unfiltered observation path used by entity choosers.
     */
    override fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        extractId: (E) -> Any
    ): DataObservation<List<E>> {
        // The test factory validates the entity class before supplying its typed observation.
        @Suppress("UNCHECKED_CAST")
        return checkNotNull(observe).invoke(entityClass) as DataObservation<List<E>>
    }

    /**
     * Provides only the unfiltered observation path used by entity choosers.
     */
    override fun <E : EntityState> readAndObserve(
        entityClass: Class<E>,
        extractId: (E) -> Any,
        queryFilter: CompositeQueryFilter,
        observeFilter: CompositeEntityStateFilter
    ): DataObservation<List<E>> = error("Unexpected filtered observation.")

    /**
     * Rejects observations outside the exercised chooser API.
     */
    override fun <E : EntityState> readOneAndObserve(
        entityClass: Class<E>,
        queryFilter: CompositeQueryFilter,
        observeFilter: CompositeEntityStateFilter
    ): DataObservation<E?> = error("Unexpected single-entity observation.")

    /**
     * Rejects observations outside the exercised chooser API.
     */
    override fun <E : EntityState> readOneAndObserve(
        entityClass: Class<E>,
        queryFilter: CompositeQueryFilter,
        observeFilter: CompositeEntityStateFilter,
        defaultValue: E
    ): DataObservation<E> = error("Unexpected single-entity observation.")

    /**
     * Rejects synchronous reads, which the chooser must not perform.
     */
    override fun <E : EntityState, M : Message> read(entityClass: Class<E>, id: M): E? =
        error("Unexpected entity read.")

    /**
     * Prevents component tests from accidentally posting commands.
     */
    override fun <C : CommandMessage> postCommand(command: C): Unit =
        error("Unexpected command.")

    /**
     * Prevents component tests from accidentally posting commands.
     */
    override fun <C : CommandMessage> postCommand(
        command: C,
        consequences: CommandConsequences<C>
    ): EventSubscriptions = error("Unexpected command.")

    /**
     * Rejects event subscriptions outside the observation lifecycle.
     */
    override fun <E : EventMessage> onEvent(
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message,
        onNetworkError: ((Throwable) -> Unit)?,
        onEvent: (E) -> Unit
    ): EventSubscription = error("Unexpected event subscription.")

    /**
     * There is no connection to close.
     */
    override fun close(): Unit = Unit
}
