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

package io.spine.chords.client.testing

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import java.util.concurrent.TimeUnit

/**
 * Controls pending RPC completion and records the transport effects of network recovery.
 */
@Suppress("TooManyFunctions" /* Implements the managed channel test double. */)
internal class ConnectionChannel : ManagedChannel() {

    /**
     * Records connection requests, resets, and shutdown in their observed order.
     */
    val effects = mutableListOf<String>()

    /**
     * Calls created through this transport, including completed calls.
     */
    val calls = mutableListOf<PendingCall<*, *>>()

    /**
     * Runs during call startup to exercise recovery before a listener has been installed.
     */
    var onStart: () -> Unit = {}

    /**
     * Runs after request transmission while the response is still pending.
     */
    var onHalfClose: () -> Unit = {}

    /**
     * Prevents connection recovery after shutdown.
     */
    private var closed = false

    /**
     * Leaves calls pending until the test completes them or the client cancels them.
     */
    override fun <ReqT, RespT> newCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions
    ): ClientCall<ReqT, RespT> = PendingCall<ReqT, RespT>(
        onStart = { onStart() }, onHalfClose = { onHalfClose() }
    )
        .also { calls.add(it) }

    /**
     * Reports the test authority without resolving a host.
     */
    override fun authority(): String = "connection-test"

    /**
     * Records immediate reconnection while preserving a controlled connectivity status.
     */
    override fun getState(requestConnection: Boolean): ConnectivityState {
        if (requestConnection) effects.add("connect")
        return if (closed) ConnectivityState.SHUTDOWN else ConnectivityState.READY
    }

    /**
     * Leaves state transitions under the test's control.
     */
    override fun notifyWhenStateChanged(source: ConnectivityState, callback: Runnable): Unit = Unit

    /**
     * Records abandonment of the current transport.
     */
    override fun enterIdle() {
        effects.add("idle")
    }

    /**
     * Records removal of a pending reconnection backoff.
     */
    override fun resetConnectBackoff() {
        effects.add("reset backoff")
    }

    /**
     * Stops accepting calls for shutdown tests.
     */
    override fun shutdown(): ManagedChannel {
        closed = true
        effects.add("shutdown")
        return this
    }

    /**
     * Ends every pending call synchronously for deterministic teardown.
     */
    override fun shutdownNow(): ManagedChannel {
        shutdown()
        calls.forEach { it.complete(Status.UNAVAILABLE) }
        return this
    }

    /**
     * Reports the controlled shutdown state.
     */
    override fun isShutdown(): Boolean = closed

    /**
     * Reports termination without asynchronous transport resources.
     */
    override fun isTerminated(): Boolean = closed

    /**
     * Requires no waiting because the test transport has no background resources.
     */
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = closed
}

/**
 * A call with explicit completion and the gRPC prohibition on starting a cancelled call.
 */
internal class PendingCall<ReqT, RespT>(
    private val onStart: () -> Unit,
    private val onHalfClose: () -> Unit
) : ClientCall<ReqT, RespT>() {

    /**
     * Receives the call's terminal status after startup.
     */
    private var listener: Listener<RespT>? = null

    /**
     * Retains completion that precedes listener installation.
     */
    private var result: Status? = null

    /**
     * Counts cancellation attempts, even if the call already completed.
     */
    var cancelCount: Int = 0
        private set

    /**
     * Rejects cancellation before startup, as the production gRPC transport does.
     */
    override fun start(responseListener: Listener<RespT>, headers: Metadata) {
        onStart()
        check(cancelCount == 0) { "A cancelled call cannot start." }
        listener = responseListener
        result?.let { responseListener.onClose(it, Metadata()) }
    }

    /**
     * Captures cancellation as a connection interruption without replaying the request.
     */
    override fun cancel(message: String?, cause: Throwable?) {
        cancelCount++
        complete(Status.CANCELLED.withDescription(message).withCause(cause))
    }

    /**
     * Publishes at most one terminal result.
     */
    fun complete(status: Status) {
        if (result != null) return
        result = status
        listener?.onClose(status, Metadata())
    }

    /**
     * Accepts demand without manufacturing a server response.
     */
    override fun request(numMessages: Int): Unit = Unit

    /**
     * Accepts the request while its completion remains controlled by the test.
     */
    override fun sendMessage(message: ReqT): Unit = Unit

    /**
     * Allows the test to interrupt a request before its response arrives.
     */
    override fun halfClose(): Unit = onHalfClose()
}
