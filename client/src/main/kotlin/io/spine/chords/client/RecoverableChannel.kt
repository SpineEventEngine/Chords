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

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ConnectivityState
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import java.util.concurrent.TimeUnit

/**
 * A channel that can abandon an obsolete connection together with its pending calls.
 *
 * Returning the transport to idle alone leaves ongoing RPCs on the old connection. Cancelling
 * those calls lets their callers recover immediately, including reads blocked before system sleep.
 */
@Suppress("TooManyFunctions" /* Implements the managed channel contract and connection recovery. */)
internal class RecoverableChannel(private val channel: ManagedChannel) : ManagedChannel() {

    /**
     * Orders call registration, reconnection, and shutdown.
     */
    private val lock = Any()

    /**
     * Calls that have started but have not completed or been cancelled.
     */
    private val calls = mutableSetOf<ClientCall<*, *>>()

    /**
     * Starts a fresh connection and interrupts outstanding calls without replaying them.
     */
    fun reconnect() {
        val interrupted = synchronized(lock) {
            if (channel.isShutdown) return
            channel.enterIdle()
            channel.resetConnectBackoff()
            calls.toList()
                .also { calls.clear() }
        }
        interrupted.forEach {
            it.cancel("The network connection was restarted.", ConnectionRestartedException())
        }
        channel.getState(true)
    }

    /**
     * Tracks each started RPC until completion so reconnection also releases blocked reads.
     */
    override fun <ReqT, RespT> newCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions
    ): ClientCall<ReqT, RespT> = TrackedCall(channel.newCall(method, callOptions))

    /**
     * Reports the transport's authority for request routing.
     */
    override fun authority(): String = channel.authority()

    /**
     * Reports transport connectivity and optionally begins connecting.
     */
    override fun getState(requestConnection: Boolean): ConnectivityState =
        channel.getState(requestConnection)

    /**
     * Observes transport changes without introducing a separate connection status.
     */
    override fun notifyWhenStateChanged(source: ConnectivityState, callback: Runnable) {
        channel.notifyWhenStateChanged(source, callback)
    }

    /**
     * Releases the current transport without interrupting its calls.
     */
    override fun enterIdle() {
        channel.enterIdle()
    }

    /**
     * Allows a pending connection attempt to proceed immediately.
     */
    override fun resetConnectBackoff() {
        channel.resetConnectBackoff()
    }

    /**
     * Prevents future calls while allowing outstanding calls to finish.
     */
    override fun shutdown(): ManagedChannel = synchronized(lock) {
        channel.shutdown()
        this
    }

    /**
     * Ends the connection and every outstanding call.
     */
    override fun shutdownNow(): ManagedChannel = synchronized(lock) {
        channel.shutdownNow()
        this
    }

    /**
     * Reports whether the transport has stopped accepting calls.
     */
    override fun isShutdown(): Boolean = channel.isShutdown

    /**
     * Reports whether all transport resources have been released.
     */
    override fun isTerminated(): Boolean = channel.isTerminated

    /**
     * Waits for transport cleanup within the caller's shutdown budget.
     */
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
        channel.awaitTermination(timeout, unit)

    /**
     * Removes a call from recovery tracking when its caller or the transport ends it.
     */
    private inner class TrackedCall<ReqT, RespT>(delegate: ClientCall<ReqT, RespT>) :
        SimpleForwardingClientCall<ReqT, RespT>(delegate) {

        /**
         * Keeps cancellation from reaching a delegate before its startup completes.
         */
        private var starting = false

        /**
         * Preserves the first cancellation requested during startup.
         */
        private var cancelAfterStart: (() -> Unit)? = null

        /**
         * Registers before starting so recovery can cancel even a call still being started.
         */
        override fun start(responseListener: Listener<RespT>, headers: Metadata) {
            synchronized(lock) {
                starting = true
                calls.add(this)
            }
            var started = false
            try {
                super.start(object : SimpleForwardingClientCallListener<RespT>(responseListener) {
                    /**
                     * Releases tracking before forwarding the terminal response to the caller.
                     */
                    override fun onClose(status: Status, trailers: Metadata) {
                        synchronized(lock) { calls.remove(this@TrackedCall) }
                        super.onClose(status, trailers)
                    }
                }, headers)
                started = true
            } finally {
                synchronized(lock) {
                    starting = false
                    if (!started) calls.remove(this)
                    val cancellation = cancelAfterStart
                    cancelAfterStart = null
                    cancellation?.invoke()
                }
            }
        }

        /**
         * Releases tracking immediately even when transport completion is delivered later.
         */
        override fun cancel(message: String?, cause: Throwable?) {
            synchronized(lock) {
                calls.remove(this)
                if (starting) {
                    if (cancelAfterStart == null) {
                        cancelAfterStart = { super.cancel(message, cause) }
                    }
                } else {
                    super.cancel(message, cause)
                }
            }
        }
    }
}

/**
 * Identifies calls interrupted by an explicit reconnect, independently of caller cancellation.
 */
internal class ConnectionRestartedException :
    Exception("The call was interrupted by a connection restart.")
