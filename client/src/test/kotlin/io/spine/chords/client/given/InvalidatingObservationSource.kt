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

package io.spine.chords.client.given

import io.spine.chords.client.ConnectionStatus
import io.spine.chords.client.DataObservation
import io.spine.chords.client.ObservationSubscription
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Keeps invalidation refreshes queued until a test delivers them after a lifecycle change.
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
     * Holds the current subscription's invalidation callback.
     */
    private var onInvalidated: () -> Unit = {}

    /**
     * Holds the current subscription's failure callback.
     */
    private var onError: (Throwable) -> Unit = {}

    /**
     * Retains refresh requests until the test chooses to run them.
     */
    private val pendingRefreshes = mutableListOf<suspend () -> Unit>()

    /**
     * Uses the real observation lifecycle with synchronously controlled reads and scheduling.
     */
    val observation = DataObservation(
        initialValue = "",
        read = {
            readCount++
            readValue
        },
        subscribe = { _, invalidated, error ->
            onInvalidated = invalidated
            onError = error
            ObservationSubscription {}
        },
        connectionStatus = { ConnectionStatus.CONNECTED },
        requestContext = EmptyCoroutineContext,
        onCancelled = {},
        onRefreshNeeded = { observation, generation ->
            pendingRefreshes.add { observation.refreshIfCurrent(generation) }
        }
    )

    /**
     * Requests a reread through the current subscription callback.
     */
    fun invalidate() {
        onInvalidated()
    }

    /**
     * Reports a terminal stream failure before a queued reread can run.
     */
    fun fail(cause: Throwable) {
        onError(cause)
    }

    /**
     * Runs queued refresh requests and returns the number of callbacks delivered.
     */
    suspend fun deliverRefreshes(): Int {
        val refreshes = pendingRefreshes.toList()
        pendingRefreshes.clear()
        refreshes.forEach { it() }
        return refreshes.size
    }
}
