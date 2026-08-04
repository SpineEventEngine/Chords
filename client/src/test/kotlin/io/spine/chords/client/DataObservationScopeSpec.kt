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

import io.grpc.Status
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests how [DataObservationScope] registers observations, starts their
 * initial refresh, and recovers or closes them as the connection status and
 * its own lifecycle change.
 */
@DisplayName("`DataObservationScope` should")
@OptIn(ExperimentalCoroutinesApi::class)
internal class DataObservationScopeSpec {

    @Test
    fun `process connection changes outside notifying thread`() = runTest {
        val scope = createScope()
        val observation = FakeObservation()
        scope.register(observation.observation)

        scope.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)

        observation.status shouldBe DataObservationStatus.Refreshing
        runCurrent()
        observation.status shouldBe DataObservationStatus.WaitingForConnection
        scope.close()
    }

    /**
     * The initial refresh is dispatched to the coroutine scope rather than
     * run on the registering thread, which is what keeps observation creation
     * from blocking the UI.
     */
    @Test
    fun `initialize a registered observation outside the calling thread`() = runTest {
        val scope = createScope()
        val observation = FakeObservation()

        val job = requireNotNull(
            scope.registerAndInitialize(observation.observation)
        )

        observation.refreshCalls shouldBe 0
        runCurrent()
        observation.awaitRefresh() shouldBe true
        runCurrent()
        observation.refreshCalls shouldBe 1
        job.isCompleted shouldBe true
        scope.close()
    }

    /**
     * Registration parks an observation that arrives while the connection is
     * unavailable, and the scope refreshes it once the connection is
     * restored. Attempting an initial refresh anyway would waste a request on
     * a channel already known to be down, and would move the observation out
     * of [DataObservationStatus.WaitingForConnection] and back again — a
     * status flap visible to the UI that renders connection feedback.
     */
    @Test
    fun `not initialize an observation registered while disconnected`() = runTest {
        val scope = createScope { ConnectionStatus.UNAVAILABLE }
        val observation = FakeObservation()

        val job = scope.registerAndInitialize(observation.observation)
        runCurrent()

        observation.status shouldBe DataObservationStatus.WaitingForConnection
        observation.refreshCalls shouldBe 0
        job shouldBe null
        scope.close()
    }

    /**
     * A reconnection processed before registration must be visible to it.
     *
     * The status cannot be sampled by the caller and passed in: between the
     * sample and the moment registration takes its lock, the connection can be
     * restored and the transition processed. That transition finds no such
     * observation registered yet, and registration then acts on the stale
     * `UNAVAILABLE`, parking an observation on a live connection with nothing
     * left to release it.
     *
     * Reading the status inside the lock removes the window: whatever the
     * status was when the caller decided to create an observation, the value
     * that registration acts on is the one current at registration.
     */
    @Test
    fun `register against the status current at registration, not at sampling`() =
        runTest {
            var liveStatus = ConnectionStatus.UNAVAILABLE
            val scope = createScope { liveStatus }
            val observation = FakeObservation()

            // The connection is restored, and the transition is fully processed
            // before the observation is registered.
            liveStatus = ConnectionStatus.CONNECTED
            scope.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
            runCurrent()

            val job = scope.registerAndInitialize(observation.observation)
            runCurrent()
            observation.awaitRefresh() shouldBe true
            runCurrent()

            observation.status shouldBe DataObservationStatus.Active
            observation.refreshCalls shouldBe 1
            job shouldNotBe null
            scope.close()
        }

    /**
     * A reconnection processed while an observation is being registered must
     * not skip that observation.
     *
     * The transition walks the registered observations and refreshes those that
     * need recovery. An observation that has joined the membership but is not
     * parked yet reports no need for recovery, so an interleaved transition
     * passes over it; registration then parks it, and — since the connection is
     * already up — no further transition is due to release it. It would wait
     * forever on a live connection.
     *
     * Registration and status processing are therefore serialized. The
     * observation is parked here while the reconnection is delivered, so the
     * transition can only run before or after the whole registration, and in
     * either order the observation ends up refreshed.
     */
    @Test
    fun `refresh an observation parked while a reconnection is processed`() =
        runBlocking {
            val registrationStarted = CountDownLatch(1)
            val continueRegistration = CountDownLatch(1)
            val scope = DataObservationScope(connectionStatus = {
                registrationStarted.countDown()
                check(continueRegistration.await(5, SECONDS)) {
                    "Timed out waiting to continue observation registration."
                }
                ConnectionStatus.UNAVAILABLE
            })
            scope.start()
            val observation = FakeObservation()

            val registration = Thread {
                scope.register(observation.observation)
            }
            registration.start()
            registrationStarted.await(5, SECONDS) shouldBe true
            scope.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
            continueRegistration.countDown()
            registration.join()

            observation.awaitRefresh() shouldBe true
            scope.close()
        }

    /**
     * An observation initialized through this scope stays under its
     * recovery scope afterwards, so a later disconnect and reconnect
     * refreshes it again.
     */
    @Test
    fun `recover an observation supplied for initialization`() = runTest {
        val scope = createScope()
        val observation = FakeObservation()

        scope.registerAndInitialize(observation.observation)
        runCurrent()
        observation.awaitRefresh() shouldBe true
        runCurrent()
        scope.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        scope.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()
        observation.awaitRefresh() shouldBe true
        runCurrent()

        observation.refreshCalls shouldBe 2
        observation.status shouldBe DataObservationStatus.Active
        scope.close()
    }

    /**
     * Registration arriving after shutdown closes the observation instead of
     * retaining it: a registered observation would wait for a recovery that
     * this scope no longer performs.
     */
    @Test
    fun `close an observation registered after the scope has closed`() = runTest {
        val scope = createScope { ConnectionStatus.UNAVAILABLE }
        scope.close()
        val observation = FakeObservation()

        scope.register(observation.observation)
        runCurrent()

        observation.status shouldBe DataObservationStatus.Cancelled
    }

    /**
     * Initialization performs no refresh once the scope has closed, and
     * reports the absence of an initial refresh by returning no job.
     */
    @Test
    fun `not initialize an observation created after the scope closed`() = runTest {
        val scope = createScope()
        scope.close()
        val observation = FakeObservation()

        val job = scope.registerAndInitialize(observation.observation)
        runCurrent()

        job shouldBe null
        observation.status shouldBe DataObservationStatus.Cancelled
        observation.refreshCalls shouldBe 0
    }

    /**
     * An observation that loses the race with [DataObservationScope.close]
     * is still closed. The closed status is therefore re-checked after the
     * observation has been added, since `close` closes what it knows about and
     * then clears the set.
     */
    @Test
    fun `unregister an observation closed while it is being registered`() = runTest {
        lateinit var scope: DataObservationScope
        scope = createScope {
            scope.close()
            ConnectionStatus.UNAVAILABLE
        }
        val observation = FakeObservation()

        scope.register(observation.observation)
        scope.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.status shouldBe DataObservationStatus.Cancelled
        observation.refreshCalls shouldBe 0
    }

    @Test
    fun `refresh observations once after connection is restored`() = runTest {
        val scope = createScope()
        val first = FakeObservation()
        val second = FakeObservation()
        scope.register(first.observation)
        scope.register(second.observation)

        scope.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        scope.onConnectionStatusChanged(ConnectionStatus.CONNECTING)
        runCurrent()
        scope.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        scope.onConnectionStatusChanged(ConnectionStatus.CONNECTING)
        runCurrent()
        scope.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()
        first.awaitRefresh() shouldBe true
        second.awaitRefresh() shouldBe true
        runCurrent()

        first.refreshCalls shouldBe 1
        second.refreshCalls shouldBe 1
        first.status shouldBe DataObservationStatus.Active
        second.status shouldBe DataObservationStatus.Active
        scope.close()
    }

    @Test
    fun `not refresh on initial connection`() = runTest {
        val scope = createScope { ConnectionStatus.CONNECTING }
        val observation = FakeObservation()
        scope.register(observation.observation)

        scope.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.refreshCalls shouldBe 0
        scope.close()
    }

    @Test
    fun `recover waiting observation on first successful connection`() = runTest {
        val scope = createScope { ConnectionStatus.CONNECTING }
        val observation = FakeObservation()
        observation.waitForConnection()
        scope.register(observation.observation)

        scope.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()
        observation.awaitRefresh() shouldBe true
        runCurrent()

        observation.refreshCalls shouldBe 1
        scope.close()
    }

    @Test
    fun `recover observation registered while disconnected`() = runTest {
        val scope = createScope { ConnectionStatus.UNAVAILABLE }
        scope.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        val observation = FakeObservation()

        scope.register(observation.observation)
        scope.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()
        observation.awaitRefresh() shouldBe true
        runCurrent()

        observation.refreshCalls shouldBe 1
        observation.status shouldBe DataObservationStatus.Active
        scope.close()
    }

    @Test
    fun `retry waiting observation while connection remains available`() = runTest {
        val scope = createScope()
        val observation = FakeObservation()
        scope.register(observation.observation)
        scope.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()
        observation.waitForConnection()

        scope.retryWhileConnected(observation.observation)
        scope.retryWhileConnected(observation.observation)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        observation.awaitRefresh() shouldBe true
        runCurrent()

        observation.refreshCalls shouldBe 1
        observation.needsRecovery shouldBe false
        scope.close()
    }

    @Test
    fun `back off between repeated stream recovery attempts`() = runTest {
        val scope = createScope()
        val observation = FakeObservation()
        observation.remainWaitingAfterRefresh = true
        scope.register(observation.observation)
        scope.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()
        observation.waitForConnection()

        scope.retryWhileConnected(observation.observation)
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        observation.refreshCalls shouldBe 0
        advanceTimeBy(1)
        runCurrent()
        observation.awaitRefresh() shouldBe true
        runCurrent()
        observation.refreshCalls shouldBe 1
        advanceTimeBy(1_000)
        runCurrent()
        observation.awaitRefresh() shouldBe true
        runCurrent()
        observation.refreshCalls shouldBe 2
        scope.close()
    }

    @Test
    fun `exclude unregistered observation from recovery`() = runTest {
        val scope = createScope()
        val observation = FakeObservation()
        scope.register(observation.observation)
        scope.unregister(observation.observation)

        scope.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        scope.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.refreshCalls shouldBe 0
        observation.status shouldBe DataObservationStatus.Refreshing
        scope.close()
    }

    @Test
    fun `not cancel job supplied in coroutine context`() = runTest {
        val suppliedJob = backgroundScope.coroutineContext[Job]!!
        val scope = DataObservationScope(
            { ConnectionStatus.CONNECTED },
            backgroundScope.coroutineContext
        )
        scope.start()

        scope.close()
        scope.onConnectionStatusChanged(ConnectionStatus.CLOSED)

        suppliedJob.isActive shouldBe true
    }

    /**
     * Exercises a real [DataObservation] against a subscription that blocks:
     * initialization returns while the subscription is still being created, and
     * the observation serves its fallback value until the refresh completes.
     */
    @Test
    fun `return from initialization while the initial subscription is pending`() =
        runBlocking {
            val scope = DataObservationScope({ ConnectionStatus.CONNECTED })
            scope.start()
            val subscribeStarted = CountDownLatch(1)
            val allowSubscribe = CountDownLatch(1)
            val observation = createDataObservation<String, String>(
                initialValue = "fallback",
                read = { "server data" },
                subscribe = { _, _ ->
                    subscribeStarted.countDown()
                    check(allowSubscribe.await(5, SECONDS)) {
                        "Timed out waiting to finish subscription setup."
                    }
                    ObservationSubscription { }
                },
                applyUpdate = { _, update -> update },
                connectionStatus = { ConnectionStatus.CONNECTED },
                onCancelled = scope::unregister,
                onRecoveryNeeded = scope::retryWhileConnected
            )

            val job = requireNotNull(
                scope.registerAndInitialize(observation)
            )
            subscribeStarted.await(5, SECONDS) shouldBe true

            job.isCompleted shouldBe false
            observation.value shouldBe "fallback"
            observation.status.value shouldBe DataObservationStatus.Refreshing

            allowSubscribe.countDown()
            job.join()

            observation.value shouldBe "server data"
            observation.status.value shouldBe DataObservationStatus.Active
            scope.close()
        }

    @Test
    fun `close observations without cancelling subscriptions individually`() = runTest {
        val scope = createScope()
        val observation = FakeObservation()
        scope.registerAndInitialize(observation.observation)
        runCurrent()
        observation.awaitRefresh() shouldBe true
        runCurrent()

        scope.close()

        observation.cancelCalls shouldBe 0
        observation.status shouldBe DataObservationStatus.Cancelled
    }
}

/**
 * Creates and starts a data observation scope on this test's scheduler.
 */
private fun TestScope.createScope(
    connectionStatus: () -> ConnectionStatus = { ConnectionStatus.CONNECTED }
): DataObservationScope =
    DataObservationScope(
        connectionStatus,
        StandardTestDispatcher(testScheduler)
    ).also {
        it.start()
    }

/**
 * Supplies a concrete observation whose server calls can be counted by scope tests.
 */
private class FakeObservation {

    /**
     * Counts attempts to establish a subscription during refresh.
     */
    var refreshCalls: Int = 0
        private set

    /**
     * Counts individual subscription cancellations.
     */
    var cancelCalls: Int = 0
        private set

    /**
     * Makes every refresh fail as a temporary connection failure when set.
     */
    var remainWaitingAfterRefresh: Boolean = false

    /**
     * Opens once [DataObservation.refresh] starts establishing a subscription.
     */
    private val refreshed = Semaphore(0)

    /**
     * The concrete observation passed to the scope under test.
     */
    val observation: DataObservation<String> = createDataObservation(
        initialValue = "",
        read = { "value" },
        subscribe = { _, _ ->
            refreshCalls++
            refreshed.release()
            if (remainWaitingAfterRefresh) {
                throw Status.UNAVAILABLE.asRuntimeException()
            }
            ObservationSubscription {
                cancelCalls++
            }
        },
        applyUpdate = { _, update: String -> update },
        connectionStatus = { ConnectionStatus.CONNECTED },
        onCancelled = {},
        requestContext = EmptyCoroutineContext
    )

    /**
     * The current lifecycle status of [observation].
     */
    val status: DataObservationStatus
        get() = observation.status.value

    /**
     * Tells whether [observation] is waiting to recover.
     */
    val needsRecovery: Boolean
        get() = observation.needsRecovery

    /**
     * Moves [observation] into its connection-waiting state.
     */
    fun waitForConnection() {
        observation.waitForConnection()
    }

    /**
     * Waits for the first refresh attempt, returning `false` if none arrives.
     */
    fun awaitRefresh(): Boolean = refreshed.tryAcquire(5, SECONDS)
}
