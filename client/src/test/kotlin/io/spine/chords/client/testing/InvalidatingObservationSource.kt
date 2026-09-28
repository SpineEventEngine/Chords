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

import io.spine.chords.client.ConnectionStatus
import io.spine.chords.client.DataObservation
import io.spine.chords.client.ObservationSubscription
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Keeps invalidation reads queued until a test delivers them after a lifecycle change.
 */
internal class InvalidatingObservationSource {

    /**
     * Supplies the next authoritative query result.
     */
    var readValue: String = "initial"

    /**
     * Counts query reads so stale callbacks cannot pass unnoticed.
     */
    var readCount: Int = 0
        private set

    /**
     * Counts subscription creation separately from query reads.
     */
    var subscribeCount: Int = 0
        private set

    /**
     * Counts cancellations of established subscriptions.
     */
    var cancelCount: Int = 0
        private set

    /**
     * Delivers callbacks after the query snapshot has been captured.
     */
    var onRead: () -> Unit = {}

    /**
     * Makes a query fail after any callbacks configured in [onRead].
     */
    var readFailure: Exception? = null

    /**
     * Receives transformations that can race with an invalidation read.
     */
    private var onUpdate: ((String) -> String) -> Unit = {}

    /**
     * Holds the current subscription's invalidation callback.
     */
    private var onInvalidated: () -> Unit = {}

    /**
     * Holds the current subscription's failure callback.
     */
    private var onError: (Throwable) -> Unit = {}

    /**
     * Retains read requests until the test chooses to run them.
     */
    private val pendingReads = mutableListOf<suspend () -> Unit>()

    /**
     * Uses the real observation lifecycle with synchronously controlled reads and scheduling.
     */
    val observation = DataObservation(
        initialValue = "",
        read = {
            readCount++
            val snapshot = readValue
            onRead()
            readFailure?.let { throw it }
            snapshot
        },
        subscribe = { update, invalidated, error ->
            subscribeCount++
            onUpdate = update
            onInvalidated = invalidated
            onError = error
            ObservationSubscription { cancelCount++ }
        },
        connectionStatus = { ConnectionStatus.CONNECTED },
        requestContext = EmptyCoroutineContext,
        onCancelled = {},
        onRereadNeeded = { observation, generation ->
            pendingReads.add { observation.rereadIfCurrent(generation) }
        }
    )

    /**
     * Requests a reread through the current subscription callback.
     */
    fun invalidate() {
        onInvalidated()
    }

    /**
     * Captures a callback so tests can deliver it after cancellation or recovery.
     */
    fun captureInvalidation(): () -> Unit = onInvalidated

    /**
     * Reports a state that can be applied directly to the observed value.
     */
    fun update(value: String) {
        onUpdate { value }
    }

    /**
     * Reports a terminal stream failure before a queued reread can run.
     */
    fun fail(cause: Throwable) {
        onError(cause)
    }

    /**
     * Runs queued read requests and returns the number of callbacks delivered.
     */
    suspend fun deliverReads(): Int {
        val reads = pendingReads.toList()
        pendingReads.clear()
        reads.forEach { it() }
        return reads.size
    }
}
