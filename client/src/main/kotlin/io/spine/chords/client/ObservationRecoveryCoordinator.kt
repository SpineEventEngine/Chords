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

    private val scope =
        CoroutineScope(coroutineContext.minusKey(Job) + SupervisorJob())
    private val observations =
        ConcurrentHashMap.newKeySet<RecoverableDataObservation>()
    private val retryingObservations =
        ConcurrentHashMap.newKeySet<RecoverableDataObservation>()
    private val stateLock = Any()
    private val connectionStatusChanges =
        Channel<ConnectionStatus>(Channel.UNLIMITED)
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    private var connectionWasUnavailable = false
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
     * Registers the given observation for automatic recovery.
     */
    fun register(
        observation: RecoverableDataObservation,
        connectionStatus: ConnectionStatus
    ) {
        observations.add(observation)
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

    private fun refresh(observation: RecoverableDataObservation) {
        scope.launch {
            observation.refresh()
        }
    }

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
