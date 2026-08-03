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

import androidx.compose.runtime.snapshots.Snapshot
import io.grpc.Status
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

internal class DataObservationImplSpec {

    @Test
    fun `read initial data and apply subscription updates`() {
        val source = FakeObservationSource("initial")
        val observation = source.createObservation()

        observation.initialize()
        source.emit("updated")

        observation.value shouldBe "updated"
        observation.status.value shouldBe DataObservationStatus.Active
    }

    @Test
    fun `read data after subscription is established`() {
        val source = FakeObservationSource("before subscription")
        source.onSubscribe = {
            source.readValue = "after subscription"
        }
        val observation = source.createObservation()

        observation.initialize()

        observation.value shouldBe "after subscription"
        observation.status.value shouldBe DataObservationStatus.Active
    }

    @Test
    fun `publish initial data in the snapshot that refreshes the observation`() {
        val source = FakeObservationSource("initial")
        val snapshot = Snapshot.takeMutableSnapshot()
        try {
            snapshot.enter {
                val observation = source.createObservation()

                observation.initialize()

                observation.value shouldBe "initial"
                observation.status.value shouldBe DataObservationStatus.Active
            }
            snapshot.apply().check()
        } finally {
            snapshot.dispose()
        }
    }

    @Test
    fun `retain data and wait when initial read cannot connect`() {
        val source = FakeObservationSource("unused")
        source.readFailure = Status.UNAVAILABLE.asRuntimeException()
        val observation = source.createObservation(
            initialValue = "fallback",
            connectionStatus = { ConnectionStatus.UNAVAILABLE }
        )

        observation.initialize()

        observation.value shouldBe "fallback"
        observation.status.value shouldBe DataObservationStatus.WaitingForConnection
        source.subscribeCalls shouldBe 1
        source.cancelCalls shouldBe 0
    }

    @Test
    fun `cancel live subscription when read times out on connected channel`() {
        val source = FakeObservationSource("unused")
        source.readFailure = Status.DEADLINE_EXCEEDED.asRuntimeException()
        val observation = source.createObservation(initialValue = "fallback")

        observation.initialize()

        observation.value shouldBe "fallback"
        observation.status.value shouldBe DataObservationStatus.WaitingForConnection
        source.cancelCalls shouldBe 1
    }

    @Test
    fun `capture non-connection read failure without throwing`() {
        val failure = IllegalStateException("Invalid query")
        val source = FakeObservationSource("unused")
        source.readFailure = failure
        val observation = source.createObservation(initialValue = "fallback")

        observation.initialize()

        observation.value shouldBe "fallback"
        val status = observation.status.value as DataObservationStatus.Failed
        (status.error === failure) shouldBe true
        source.subscribeCalls shouldBe 1
        source.cancelCalls shouldBe 1
    }

    @Test
    fun `capture non-connection subscription failure without throwing`() {
        val failure = IllegalStateException("Invalid subscription")
        val source = FakeObservationSource("initial")
        source.subscribeFailure = failure
        val observation = source.createObservation()

        observation.initialize()

        observation.value shouldBe "initial"
        val status = observation.status.value as DataObservationStatus.Failed
        (status.error === failure) shouldBe true
    }

    @Test
    fun `propagate cancellation without converting it into failure status`() {
        val cancellation = CancellationException("Caller cancelled refresh.")
        val source = FakeObservationSource("unused")
        source.readFailure = cancellation
        val observation = source.createObservation()

        shouldThrow<CancellationException> {
            observation.initialize()
        }

        observation.status.value shouldBe DataObservationStatus.Refreshing
        observation.needsRecovery shouldBe false
    }

    @Test
    fun `not mistake server rejection for connection failure`() {
        val failure = Status.PERMISSION_DENIED.asRuntimeException()
        val source = FakeObservationSource("unused")
        source.readFailure = failure
        val observation = source.createObservation(
            connectionStatus = { ConnectionStatus.UNAVAILABLE }
        )

        observation.initialize()

        observation.status.value shouldBe DataObservationStatus.Failed(failure)
    }

    @Test
    fun `report programming failure while connection is starting`() {
        val failure = IllegalStateException("Invalid query")
        val source = FakeObservationSource("unused")
        source.readFailure = failure
        val observation = source.createObservation(
            connectionStatus = { ConnectionStatus.CONNECTING }
        )

        observation.initialize()

        observation.status.value shouldBe DataObservationStatus.Failed(failure)
        observation.needsRecovery shouldBe false
    }

    @Test
    fun `not overwrite server failure when connection monitor reports loss`() {
        val failure = Status.PERMISSION_DENIED.asRuntimeException()
        val source = FakeObservationSource("unused")
        source.readFailure = failure
        val observation = source.createObservation()
        observation.initialize()

        observation.waitForConnection()

        observation.status.value shouldBe DataObservationStatus.Failed(failure)
        observation.needsRecovery shouldBe false
    }

    @Test
    fun `retry failed observation only when explicitly refreshed`() {
        val source = FakeObservationSource("refreshed")
        source.readFailure = IllegalStateException("Invalid query")
        val observation = source.createObservation(initialValue = "fallback")
        observation.initialize()
        source.readFailure = null

        runBlocking {
            observation.refresh()
        }

        observation.value shouldBe "refreshed"
        observation.status.value shouldBe DataObservationStatus.Active
    }

    @Test
    fun `recover after the initial refresh cannot connect`() {
        val source = FakeObservationSource("server data")
        source.readFailure = Status.UNAVAILABLE.asRuntimeException()
        val observation = source.createObservation(
            initialValue = "fallback",
            connectionStatus = { ConnectionStatus.UNAVAILABLE }
        )

        observation.initialize()

        observation.value shouldBe "fallback"
        observation.status.value shouldBe DataObservationStatus.WaitingForConnection
        observation.needsRecovery shouldBe true

        source.readFailure = null
        runBlocking {
            observation.refresh()
        }

        observation.value shouldBe "server data"
        observation.status.value shouldBe DataObservationStatus.Active
    }

    @Test
    fun `recover after waiting for connection before the first refresh`() {
        val source = FakeObservationSource("server data")
        val observation = source.createObservation(
            initialValue = "fallback",
            connectionStatus = { ConnectionStatus.UNAVAILABLE }
        )

        observation.waitForConnection()

        observation.value shouldBe "fallback"
        observation.status.value shouldBe DataObservationStatus.WaitingForConnection
        observation.needsRecovery shouldBe true

        runBlocking {
            observation.refresh()
        }

        observation.value shouldBe "server data"
        observation.status.value shouldBe DataObservationStatus.Active
        source.subscribeCalls shouldBe 1
        source.cancelCalls shouldBe 0
    }

    @Test
    fun `replace data and subscription when refreshed`() {
        val source = FakeObservationSource("initial")
        val observation = source.createObservation()
        observation.initialize()
        source.readValue = "refreshed"

        runBlocking {
            observation.refresh()
        }

        observation.value shouldBe "refreshed"
        observation.status.value shouldBe DataObservationStatus.Active
        source.subscribeCalls shouldBe 2
        source.cancelCalls shouldBe 1
    }

    @Test
    fun `wait for reconnect after streaming failure`() {
        val source = FakeObservationSource("initial")
        val observation = source.createObservation()
        observation.initialize()

        source.fail(Status.UNAVAILABLE.asRuntimeException())

        observation.value shouldBe "initial"
        observation.status.value shouldBe DataObservationStatus.WaitingForConnection
        observation.needsRecovery shouldBe true
        source.cancelCalls shouldBe 0
    }

    @Test
    fun `request retry when stream fails on connected channel`() {
        val source = FakeObservationSource("initial")
        var recoveryRequests = 0
        val observation = source.createObservation(
            onRecoveryNeeded = {
                recoveryRequests++
            }
        )
        observation.initialize()

        source.fail(Status.UNAVAILABLE.asRuntimeException())

        observation.status.value shouldBe DataObservationStatus.WaitingForConnection
        recoveryRequests shouldBe 1
        source.cancelCalls shouldBe 0
    }

    @Test
    fun `not cancel subscription after connection is lost`() {
        val source = FakeObservationSource("initial")
        val observation = source.createObservation()
        observation.initialize()

        observation.waitForConnection()

        observation.status.value shouldBe DataObservationStatus.WaitingForConnection
        observation.needsRecovery shouldBe true
        source.cancelCalls shouldBe 0
    }

    @Test
    fun `exclude cancelled observation from recovery`() {
        val source = FakeObservationSource("initial")
        var cancelled = false
        val observation = source.createObservation {
            cancelled = true
        }
        observation.initialize()

        observation.cancel()
        runBlocking {
            observation.refresh()
        }

        observation.status.value shouldBe DataObservationStatus.Cancelled
        observation.needsRecovery shouldBe false
        source.readCalls shouldBe 1
        source.cancelCalls shouldBe 1
        cancelled shouldBe true
    }

    @Test
    fun `close without cancelling server subscription`() {
        val source = FakeObservationSource("initial")
        var closed = false
        val observation = source.createObservation {
            closed = true
        }
        observation.initialize()

        observation.close()

        observation.status.value shouldBe DataObservationStatus.Cancelled
        observation.needsRecovery shouldBe false
        source.cancelCalls shouldBe 0
        closed shouldBe true
    }

    @Test
    fun `cancel subscription created after concurrent cancellation`(): Unit = runBlocking {
        val subscribeStarted = CountDownLatch(1)
        val allowSubscribe = CountDownLatch(1)
        val cancelCalls = AtomicInteger()
        val observation = blockingObservation(
            subscribeStarted,
            allowSubscribe,
            cancelCalls
        )

        val refresh = launch(IO) {
            observation.refresh()
        }
        subscribeStarted.await(5, SECONDS) shouldBe true
        observation.cancel()
        allowSubscribe.countDown()
        refresh.join()

        observation.status.value shouldBe DataObservationStatus.Cancelled
        cancelCalls.get() shouldBe 1
    }

    @Test
    fun `not cancel subscription created after connection is lost`(): Unit = runBlocking {
        val subscribeStarted = CountDownLatch(1)
        val allowSubscribe = CountDownLatch(1)
        val cancelCalls = AtomicInteger()
        val observation = blockingObservation(
            subscribeStarted,
            allowSubscribe,
            cancelCalls
        )

        val refresh = launch(IO) {
            observation.refresh()
        }
        subscribeStarted.await(5, SECONDS) shouldBe true
        observation.waitForConnection()
        allowSubscribe.countDown()
        refresh.join()

        observation.status.value shouldBe DataObservationStatus.WaitingForConnection
        observation.needsRecovery shouldBe true
        cancelCalls.get() shouldBe 0
    }

    @Test
    fun `expose the initial value while the first refresh is pending`(): Unit = runBlocking {
        val subscribeStarted = CountDownLatch(1)
        val allowSubscribe = CountDownLatch(1)
        val cancelCalls = AtomicInteger()
        val observation = blockingObservation(
            subscribeStarted,
            allowSubscribe,
            cancelCalls,
            initialValue = "fallback"
        )

        val refresh = launch(IO) {
            observation.refresh()
        }
        subscribeStarted.await(5, SECONDS) shouldBe true

        observation.value shouldBe "fallback"
        observation.status.value shouldBe DataObservationStatus.Refreshing

        allowSubscribe.countDown()
        refresh.join()

        observation.value shouldBe "value"
        observation.status.value shouldBe DataObservationStatus.Active
    }

    @Test
    fun `not cancel subscription created after the client is closed`(): Unit = runBlocking {
        val subscribeStarted = CountDownLatch(1)
        val allowSubscribe = CountDownLatch(1)
        val cancelCalls = AtomicInteger()
        val observation = blockingObservation(
            subscribeStarted,
            allowSubscribe,
            cancelCalls
        )

        val refresh = launch(IO) {
            observation.refresh()
        }
        subscribeStarted.await(5, SECONDS) shouldBe true
        observation.close()
        allowSubscribe.countDown()
        refresh.join()

        observation.status.value shouldBe DataObservationStatus.Cancelled
        cancelCalls.get() shouldBe 0
    }

    @Test
    fun `not cancel subscription that reports failure before installation`() {
        val cancelCalls = AtomicInteger()
        val observation = DataObservationImpl(
            "",
            { "value" },
            { _, onError ->
                onError(Status.UNAVAILABLE.asRuntimeException())
                ObservationSubscription {
                    cancelCalls.incrementAndGet()
                }
            },
            { _, update: String -> update },
            { ConnectionStatus.UNAVAILABLE },
            {}
        )

        observation.initialize()

        observation.status.value shouldBe DataObservationStatus.WaitingForConnection
        cancelCalls.get() shouldBe 0
    }
}

/**
 * Runs the initial refresh of this observation to completion.
 *
 * Production code initializes an observation asynchronously in the recovery
 * coordinator's scope; these tests need the initialized state to be ready
 * before they assert on it.
 */
private fun DataObservationImpl<*, *>.initialize() = runBlocking {
    refresh()
}

private fun blockingObservation(
    subscribeStarted: CountDownLatch,
    allowSubscribe: CountDownLatch,
    cancelCalls: AtomicInteger,
    initialValue: String = ""
): DataObservationImpl<String, String> = DataObservationImpl(
    initialValue,
    { "value" },
    { _, _ ->
        subscribeStarted.countDown()
        check(allowSubscribe.await(5, SECONDS)) {
            "Timed out waiting to finish subscription setup."
        }
        ObservationSubscription {
            cancelCalls.incrementAndGet()
        }
    },
    { _, update -> update },
    { ConnectionStatus.CONNECTED },
    {}
)

private class FakeObservationSource(
    var readValue: String
) {

    var readCalls: Int = 0
        private set
    var subscribeCalls: Int = 0
        private set
    var cancelCalls: Int = 0
        private set
    var readFailure: Exception? = null
    var subscribeFailure: Exception? = null
    var cancelFailure: io.grpc.StatusRuntimeException? = null
    var onSubscribe: (() -> Unit)? = null

    private var onUpdate: ((String) -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null

    fun createObservation(
        initialValue: String = "",
        connectionStatus: () -> ConnectionStatus = { ConnectionStatus.CONNECTED },
        onRecoveryNeeded: (RecoverableDataObservation) -> Unit = {},
        onCancelled: (RecoverableDataObservation) -> Unit = {}
    ): DataObservationImpl<String, String> = DataObservationImpl(
        initialValue,
        {
            readCalls++
            readFailure?.let { throw it }
            readValue
        },
        { update, error ->
            subscribeCalls++
            subscribeFailure?.let { throw it }
            onSubscribe?.invoke()
            onUpdate = update
            onError = error
            ObservationSubscription {
                cancelCalls++
                cancelFailure?.let { throw it }
            }
        },
        { _, update -> update },
        connectionStatus,
        onCancelled,
        onRecoveryNeeded
    )

    fun emit(value: String) {
        onUpdate?.invoke(value)
    }

    fun fail(error: Throwable) {
        onError?.invoke(error)
    }
}
