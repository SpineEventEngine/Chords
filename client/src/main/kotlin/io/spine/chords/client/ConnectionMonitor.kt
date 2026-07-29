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

import io.grpc.ConnectivityState
import io.grpc.ConnectivityState.CONNECTING
import io.grpc.ConnectivityState.IDLE
import io.grpc.ConnectivityState.READY
import io.grpc.ConnectivityState.SHUTDOWN
import io.grpc.ConnectivityState.TRANSIENT_FAILURE
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observes the connectivity state of a gRPC channel.
 */
internal class ConnectionMonitor(
    private val getState: (requestConnection: Boolean) -> ConnectivityState,
    private val notifyWhenStateChanged: (
        source: ConnectivityState,
        callback: Runnable
    ) -> Unit,
    private val onStatusChanged: (ConnectionStatus) -> Unit
) {

    private val closed = AtomicBoolean(false)
    private val stateLock = Any()
    private val mutableStatus = MutableStateFlow(ConnectionStatus.IDLE)

    /**
     * The currently observed connection status.
     */
    val status: StateFlow<ConnectionStatus> = mutableStatus.asStateFlow()

    /**
     * Starts observing channel connectivity.
     */
    fun start() {
        observe(getState(true))
    }

    /**
     * Stops observing and reports the connection as closed.
     */
    fun close() {
        if (closed.compareAndSet(false, true)) {
            updateStatus(ConnectionStatus.CLOSED)
        }
    }

    private fun observe(channelState: ConnectivityState) {
        if (closed.get()) {
            return
        }
        updateStatus(channelState.toConnectionStatus())
        if (channelState == SHUTDOWN) {
            close()
            return
        }
        notifyWhenStateChanged(channelState, Runnable {
            if (!closed.get()) {
                observe(getState(true))
            }
        })
    }

    private fun updateStatus(newStatus: ConnectionStatus) {
        synchronized(stateLock) {
            if (closed.get() && newStatus != ConnectionStatus.CLOSED) {
                return
            }
            val previousStatus = mutableStatus.value
            if (previousStatus != newStatus) {
                mutableStatus.value = newStatus
                onStatusChanged(newStatus)
            }
        }
    }
}

private fun ConnectivityState.toConnectionStatus(): ConnectionStatus = when (this) {
    IDLE -> ConnectionStatus.IDLE
    CONNECTING -> ConnectionStatus.CONNECTING
    READY -> ConnectionStatus.CONNECTED
    TRANSIENT_FAILURE -> ConnectionStatus.UNAVAILABLE
    SHUTDOWN -> ConnectionStatus.CLOSED
}
