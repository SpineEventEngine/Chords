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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Prevents a repeatedly failing subscription stream from causing a tight retry loop.
 */
private const val ObservationRetryDelayMillis = 1_000L

/**
 * Suspends and refreshes data observations as connectivity changes.
 */
internal class ObservationRecoveryCoordinator(
    coroutineContext: CoroutineContext = IO
) {

    /**
     * Runs connection processing and observation recovery tasks.
     */
    private val scope =
        CoroutineScope(coroutineContext.minusKey(Job) + SupervisorJob())

    /**
     * The observations managed by this coordinator.
     */
    private val observations =
        ConcurrentHashMap.newKeySet<RecoverableDataObservation>()

    /**
     * The observations that currently have a connection-aware retry loop.
     */
    private val retryingObservations =
        ConcurrentHashMap.newKeySet<RecoverableDataObservation>()

    /**
     * Guards connection status transition state.
     */
    private val stateLock = Any()

    /**
     * Serializes connection status changes for ordered processing.
     */
    private val connectionStatusChanges =
        Channel<ConnectionStatus>(Channel.UNLIMITED)

    /**
     * Prevents the status-processing coroutine from being started more than once.
     */
    private val started = AtomicBoolean(false)

    /**
     * Indicates whether this coordinator has stopped accepting work.
     */
    private val closed = AtomicBoolean(false)

    /**
     * Indicates that an unavailable status has not yet been followed by reconnection.
     */
    private var connectionWasUnavailable = false

    /**
     * The latest connection status processed by this coordinator.
     */
    private var connectionStatus = ConnectionStatus.IDLE

    /**
     * Starts processing connection status changes.
     */
    fun start() {
        if (!closed.get() && started.compareAndSet(false, true)) {
            scope.launch {
                for (status in connectionStatusChanges) {
                    processConnectionStatus(status)
                }
            }
        }
    }

    /**
     * Registers the given observation for automatic recovery and brings it in
     * line with the [connectionStatus] known at the moment of registration.
     *
     * An observation that arrives once this coordinator has closed, or that
     * loses the race with [close], is closed instead of being registered:
     * leaving it registered would keep it waiting for a recovery that no longer
     * happens. The closed status is therefore checked after the addition,
     * because [close] closes the observations it knows about and then clears
     * them.
     *
     * @return `true` if the observation is now managed by this coordinator.
     */
    fun register(
        observation: RecoverableDataObservation,
        connectionStatus: ConnectionStatus
    ): Boolean {
        observations.add(observation)
        if (closed.get()) {
            observations.remove(observation)
            observation.close()
            return false
        }
        val unavailable = synchronized(stateLock) {
            connectionWasUnavailable || connectionStatus == ConnectionStatus.UNAVAILABLE
        }
        if (unavailable) {
            observation.waitForConnection()
        } else if (connectionStatus == ConnectionStatus.CONNECTED &&
            observation.needsRecovery
        ) {
            refresh(observation)
        }
        return true
    }

    /**
     * Registers the given observation and performs its initial read and
     * subscription in this coordinator's scope.
     *
     * The observation is registered before the initial refresh starts, so that
     * a refresh which immediately fails on a connected channel can be retried.
     *
     * @return The job that performs the initial refresh, or `null` if the
     *   observation was not registered because this coordinator has closed.
     */
    fun registerAndInitialize(
        observation: RecoverableDataObservation,
        connectionStatus: ConnectionStatus
    ): Job? =
        if (register(observation, connectionStatus)) {
            refresh(observation)
        } else {
            null
        }

    /**
     * Unregisters the given observation.
     */
    fun unregister(observation: RecoverableDataObservation) {
        observations.remove(observation)
    }

    /**
     * Retries an observation whose stream failed while the channel remained connected.
     */
    fun retryWhileConnected(observation: RecoverableDataObservation) {
        if (closed.get() ||
            observation !in observations ||
            !retryingObservations.add(observation)
        ) {
            return
        }
        scope.launch {
            try {
                while (shouldRetry(observation)) {
                    delay(ObservationRetryDelayMillis)
                    if (shouldRetry(observation)) {
                        observation.refresh()
                    }
                }
            } finally {
                retryingObservations.remove(observation)
            }
        }
    }

    /**
     * Schedules an update of all registered observations for the new connection
     * status.
     */
    fun onConnectionStatusChanged(status: ConnectionStatus) {
        val sent = connectionStatusChanges.trySend(status)
        check(sent.isSuccess || closed.get()) {
            "Unable to schedule connection status change: $status."
        }
    }

    /**
     * Cancels all observations and stops automatic recovery.
     */
    fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        connectionStatusChanges.close()
        observations.toList().forEach {
            it.close()
        }
        observations.clear()
        retryingObservations.clear()
        scope.cancel()
    }

    /**
     * Applies a connection [status] transition to all registered observations.
     */
    private fun processConnectionStatus(status: ConnectionStatus) {
        val connectionLost: Boolean
        synchronized(stateLock) {
            connectionStatus = status
            connectionLost =
                status == ConnectionStatus.UNAVAILABLE && !connectionWasUnavailable
            if (connectionLost) {
                connectionWasUnavailable = true
            }
            if (status == ConnectionStatus.CONNECTED) {
                connectionWasUnavailable = false
            }
        }

        if (connectionLost) {
            observations.toList().forEach {
                it.waitForConnection()
            }
        } else if (status == ConnectionStatus.CONNECTED) {
            observations.toList().forEach {
                if (it.needsRecovery) {
                    refresh(it)
                }
            }
        }
    }

    /**
     * Refreshes the given [observation] in the coordinator scope.
     *
     * @return The job that performs the refresh. It completes without running
     *   the refresh if this coordinator has been closed meanwhile, because
     *   [close] cancels the scope.
     */
    private fun refresh(observation: RecoverableDataObservation): Job =
        scope.launch {
            observation.refresh()
        }

    /**
     * Checks whether the given [observation] still needs a connected retry.
     */
    private fun shouldRetry(observation: RecoverableDataObservation): Boolean {
        val connected = synchronized(stateLock) {
            connectionStatus == ConnectionStatus.CONNECTED
        }
        return !closed.get() &&
                connected &&
                observation in observations &&
                observation.needsRecovery
    }
}
