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

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withTimeout

/**
 * Checks that a publication callback permits another thread to read the navigator before returning.
 * Its bounded wait exposes lock inversion without permanently blocking the test process.
 */
internal class NavigatorCallbackProbe(
    /**
     * Reads public navigator state on the callback's receiver thread.
     */
    private val readState: () -> Unit
) : CoroutineDispatcher(), AutoCloseable {

    /**
     * The receiver that must remain able to inspect state while a callback is in progress.
     */
    private val receiver = Executors.newSingleThreadExecutor()

    /**
     * Restricts the measurement to the first callback while allowing subsequent dispatches.
     */
    private val inspected = AtomicBoolean(false)

    /**
     * Whether the receiver read finished before the measured callback returned.
     */
    private val completed = CompletableDeferred<Boolean>()

    /**
     * Probes waiter resumption before running its continuation on the receiver thread.
     */
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        inspect()
        receiver.execute(block)
    }

    /**
     * Probes a callback without requiring it to resume a coroutine.
     */
    @Suppress("SwallowedException" /* A timeout is the measured lock-blocking result. */)
    fun inspect() {
        if (!inspected.compareAndSet(false, true)) return
        val reading = receiver.submit { readState() }
        val available = try {
            reading.get(1, SECONDS)
            true
        } catch (blocked: TimeoutException) {
            false
        }
        completed.complete(available)
    }

    /**
     * Waits for evidence that the callback ran and could reach the published state.
     */
    suspend fun awaitCallback(): Boolean = withTimeout(5_000) { completed.await() }

    /**
     * Releases the receiver after its callback and resumed continuation have finished.
     */
    override fun close() {
        receiver.shutdown()
        check(receiver.awaitTermination(5, SECONDS)) { "The callback receiver did not stop." }
    }
}
