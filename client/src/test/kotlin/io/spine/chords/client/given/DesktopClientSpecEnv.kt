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

import io.spine.chords.client.DataObservation
import io.spine.chords.client.DataObservationStatus
import io.spine.core.UserId
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Supplies entities and waits for asynchronous observation initialization.
 */
internal object DesktopClientSpecEnv {

    /**
     * Creates distinct states with stable message IDs.
     */
    fun item(id: String, label: String = id): ObservedItem = ObservedItem.newBuilder()
        .setId(UserId.newBuilder()
            .setValue(id))
        .setLabel(label)
        .build()

    /**
     * Waits for the read to complete, surfacing failures instead of timing them out.
     */
    fun DataObservation<*>.awaitActive() = runBlocking {
        withTimeout(5_000) {
            while (status.value != DataObservationStatus.Active) {
                val currentStatus = status.value
                check(currentStatus !is DataObservationStatus.Failed) { currentStatus }
                delay(10)
            }
        }
    }

    /**
     * Waits for a queued reread to start before checking its observation's completion.
     */
    fun ObservationChannel.awaitReads(count: Int) = runBlocking {
        withTimeout(5_000) {
            while (readCount < count) {
                delay(10)
            }
        }
    }
}
