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

import io.grpc.Status.Code.UNAVAILABLE
import io.grpc.StatusRuntimeException
import io.spine.client.Subscriptions
import java.util.logging.Filter
import java.util.logging.Logger

/**
 * Captures the expected offline cancellation error on the creating test thread.
 *
 * Other records keep their previous filtering behavior. Closing the capture restores the filter.
 */
internal class SubscriptionCancellationLog : AutoCloseable {

    /**
     * Limits capture to cancellations driven by this test's controlled dispatcher.
     */
    private val owner = Thread.currentThread()

    /**
     * Receives the cancellation errors logged by Spine's subscription registry.
     */
    private val logger = Logger.getLogger(Subscriptions::class.java.name)

    /**
     * Preserves filtering installed before this capture.
     */
    private val previousFilter = logger.filter

    /**
     * Retains expected failures for assertions instead of printing them to stderr.
     */
    private val capturedFailures = mutableListOf<StatusRuntimeException>()

    /**
     * Returns the failures observed so far without exposing the mutable capture.
     */
    val failures: List<StatusRuntimeException>
        get() = capturedFailures.toList()

    init {
        logger.filter = Filter { record ->
            val failure = (record.thrown as? StatusRuntimeException)
                ?.takeIf { it.status.code == UNAVAILABLE }
            val isCancellationHandler =
                record.sourceClassName == "io.spine.client.LoggingErrorHandler" &&
                        record.sourceMethodName == "accept"
            if (Thread.currentThread() === owner && isCancellationHandler && failure != null) {
                capturedFailures.add(failure)
                false
            } else {
                previousFilter?.isLoggable(record) ?: true
            }
        }
    }

    /**
     * Restores the logger even if the test fails inside the capture scope.
     */
    override fun close() {
        logger.filter = previousFilter
    }
}
