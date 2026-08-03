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
 * Owns the lifecycle of every [DataObservation] created by one [DesktopClient].
 *
 * A [DataObservationImpl] knows how to read, subscribe, and recover *itself*,
 * but it has no coroutine scope of its own and no knowledge of the connection.
 * This class supplies both, for all observations of a client at once:
 *
 * - **A scope.** Every background refresh runs in this manager's scope, whose
 *   [SupervisorJob] keeps one failing observation from cancelling the others.
 *   [close] cancels the scope, which is how a closed client stops all pending
 *   work. Without a shared owner, each observation would have to create and
 *   destroy a scope of its own, and closing the client could not reach them.
 * - **Connection awareness.** The client reports connection changes to
 *   [onConnectionStatusChanged]. Losing the connection parks every observation;
 *   regaining it refreshes those that need recovery. An observation cannot do
 *   this for itself, because the connection is a property of the client, and
 *   the transition has to be applied to all observations in a defined order.
 * - **Membership.** An observation participates from
 *   [registerAndInitialize] until [unregister] or [close]. Membership is what
 *   makes recovery and shutdown reach exactly the live observations.
 *
 * The observations are held as [ManagedDataObservation], the narrow lifecycle
 * contract this manager needs. That keeps the manager independent of the
 * observed value types, which differ per observation.
 *
 * @param connectionStatus Returns the connection status as it is *right now*.
 *   Registration reads it while holding [stateLock] rather than accepting a
 *   value sampled by the caller: a sampled value can go stale before
 *   registration takes the lock, and acting on a stale
 *   [ConnectionStatus.UNAVAILABLE] parks an observation on a connection that
 *   is already up, where nothing is due to release it.
 * @param coroutineContext The context for the background work, overridable in
 *   tests to make the scheduling deterministic.
 */
internal class DataObservationManager(
    private val connectionStatus: () -> ConnectionStatus,
    coroutineContext: CoroutineContext = IO
) {

    /**
     * Runs every background task of the managed observations: status
     * processing, initial refreshes, recovery, and connected retries.
     *
     * The [SupervisorJob] isolates the observations from each other, so one
     * failing refresh does not cancel the rest. [close] cancels this scope,
     * which stops all pending work at once.
     */
    private val scope =
        CoroutineScope(coroutineContext.minusKey(Job) + SupervisorJob())

    /**
     * The observations currently managed by this manager.
     */
    private val observations =
        ConcurrentHashMap.newKeySet<ManagedDataObservation>()

    /**
     * The observations that currently have a connection-aware retry loop.
     */
    private val retryingObservations =
        ConcurrentHashMap.newKeySet<ManagedDataObservation>()

    /**
     * Guards connection status transitions *and* registration, so that the two
     * cannot interleave.
     *
     * Both decide what an observation should be doing based on the connection,
     * and both walk the same membership. Running them concurrently loses an
     * observation that is registered but not yet parked: the transition sees it
     * as not needing recovery and skips it, registration then parks it, and it
     * waits for a reconnection that has already happened.
     *
     * Observation calls made while this lock is held — `waitForConnection`,
     * `needsRecovery`, `close` — take the observation's own lock, never the
     * other way round: an observation invokes this manager only from outside
     * its own synchronized blocks. That ordering is what keeps this safe.
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
     * Indicates whether this manager has stopped accepting work.
     */
    private val closed = AtomicBoolean(false)

    /**
     * Indicates that an unavailable status has not yet been followed by reconnection.
     */
    private var connectionWasUnavailable = false

    /**
     * The latest connection status this manager has *processed*.
     *
     * Status changes are queued and applied by a coroutine, so this lags the
     * client's own live status. Callers that know the current status pass it
     * explicitly rather than relying on this field.
     */
    private var lastProcessedStatus = ConnectionStatus.IDLE

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
     * Registers the given observation and brings it in line with the
     * connection status current at that moment.
     *
     * Joining the membership, reading [connectionStatus], and acting on it
     * happen together under [stateLock], so a connection transition can neither
     * observe a half-registered observation nor slip in between the status
     * being read and being acted upon. See that field for what goes wrong if
     * the two interleave.
     *
     * An observation that arrives once this manager has closed, or that loses
     * the race with [close], is closed instead of being registered: leaving it
     * registered would keep it waiting for a recovery that no longer happens.
     * The closed status is therefore checked after the addition, because
     * [close] closes the observations it knows about and then clears them.
     *
     * @return `true` if the observation is now managed by this manager.
     */
    fun register(observation: ManagedDataObservation): Boolean {
        synchronized(stateLock) {
            observations.add(observation)
            if (closed.get()) {
                observations.remove(observation)
                observation.close()
                return false
            }
            val currentStatus = connectionStatus()
            val unavailable = connectionWasUnavailable ||
                    currentStatus == ConnectionStatus.UNAVAILABLE
            if (unavailable) {
                observation.waitForConnection()
            } else if (currentStatus == ConnectionStatus.CONNECTED &&
                observation.needsRecovery
            ) {
                refresh(observation)
            }
        }
        return true
    }

    /**
     * Registers the given observation and performs its initial read and
     * subscription in this manager's scope.
     *
     * The observation is registered before the initial refresh starts, so that
     * a refresh that immediately fails on a connected channel can be retried.
     *
     * No initial refresh is started when registration has parked the
     * observation to wait for a connection. Reaching the server is known to be
     * impossible at that point, and the observation is already scheduled for
     * recovery, so refreshing would only spend a request on an unavailable
     * channel and move the observation out of
     * [waiting for connection][DataObservationStatus.WaitingForConnection] and
     * back again.
     *
     * @return The job that performs the initial refresh, or `null` if no
     *   initial refresh was started — either because this manager has closed,
     *   or because the observation is waiting for a connection.
     */
    fun registerAndInitialize(observation: ManagedDataObservation): Job? =
        if (register(observation) && !observation.needsRecovery) {
            refresh(observation)
        } else {
            null
        }

    /**
     * Unregisters the given observation.
     */
    fun unregister(observation: ManagedDataObservation) {
        observations.remove(observation)
    }

    /**
     * Retries an observation whose stream failed while the channel remained connected.
     */
    fun retryWhileConnected(observation: ManagedDataObservation) {
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
        synchronized(stateLock) {
            lastProcessedStatus = status
            val connectionLost =
                status == ConnectionStatus.UNAVAILABLE && !connectionWasUnavailable
            if (connectionLost) {
                connectionWasUnavailable = true
            }
            if (status == ConnectionStatus.CONNECTED) {
                connectionWasUnavailable = false
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
    }

    /**
     * Refreshes the given [observation] in this manager's scope.
     *
     * @return The job that performs the refresh. It completes without running
     *   the refresh if this manager has been closed meanwhile, because [close]
     *   cancels the scope.
     */
    private fun refresh(observation: ManagedDataObservation): Job =
        scope.launch {
            observation.refresh()
        }

    /**
     * Checks whether the given [observation] still needs a connected retry.
     */
    private fun shouldRetry(observation: ManagedDataObservation): Boolean {
        val connected = synchronized(stateLock) {
            lastProcessedStatus == ConnectionStatus.CONNECTED
        }
        return !closed.get() &&
                connected &&
                observation in observations &&
                observation.needsRecovery
    }
}
