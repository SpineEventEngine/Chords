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

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
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
 * Holds a registration open long enough for a concurrently delivered
 * connection transition to be processed, if the two are able to interleave.
 */
private const val RegistrationWindowMillis = 300L

/**
 * Tests how [DataObservationManager] registers observations, starts their
 * initial refresh, and recovers or closes them as the connection status and
 * its own lifecycle change.
 */
@DisplayName("`DataObservationManager` should")
@OptIn(ExperimentalCoroutinesApi::class)
internal class DataObservationManagerSpec {

    @Test
    fun `process connection changes outside notifying thread`() = runTest {
        val manager = createManager()
        val observation = FakeManagedObservation()
        manager.register(observation)

        manager.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)

        observation.waitCalls shouldBe 0
        runCurrent()
        observation.waitCalls shouldBe 1
        manager.close()
    }

    /**
     * The initial refresh is dispatched to the manager's scope rather than
     * run on the registering thread, which is what keeps observation creation
     * from blocking the UI.
     */
    @Test
    fun `initialize a registered observation outside the calling thread`() = runTest {
        val manager = createManager()
        val observation = FakeManagedObservation()

        val job = requireNotNull(
            manager.registerAndInitialize(observation)
        )

        observation.refreshCalls shouldBe 0
        runCurrent()
        observation.refreshCalls shouldBe 1
        job.isCompleted shouldBe true
        manager.close()
    }

    /**
     * Registration parks an observation that arrives while the connection is
     * unavailable, and the manager refreshes it once the connection is
     * restored. Attempting an initial refresh anyway would waste a request on
     * a channel already known to be down, and would move the observation out
     * of [DataObservationStatus.WaitingForConnection] and back again — a
     * status flap visible to the UI that renders connection feedback.
     */
    @Test
    fun `not initialize an observation registered while disconnected`() = runTest {
        val manager = createManager { ConnectionStatus.UNAVAILABLE }
        val observation = FakeManagedObservation()

        val job = manager.registerAndInitialize(observation)
        runCurrent()

        observation.waitCalls shouldBe 1
        observation.refreshCalls shouldBe 0
        job shouldBe null
        manager.close()
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
            val manager = createManager { liveStatus }
            val observation = FakeManagedObservation()

            // The connection is restored, and the transition is fully processed
            // before the observation is registered.
            liveStatus = ConnectionStatus.CONNECTED
            manager.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
            runCurrent()

            val job = manager.registerAndInitialize(observation)
            runCurrent()

            observation.waitCalls shouldBe 0
            observation.refreshCalls shouldBe 1
            job shouldNotBe null
            manager.close()
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
            val manager = DataObservationManager({ ConnectionStatus.UNAVAILABLE })
            manager.start()
            val parkingStarted = CountDownLatch(1)
            val observation = FakeManagedObservation()
            observation.onParkingStarted = {
                parkingStarted.countDown()
                // Hold the observation mid-registration long enough for the
                // reconnection below to be delivered and applied. Serialized,
                // the transition waits here; unserialized, it runs now and
                // passes over an observation that is not parked yet.
                Thread.sleep(RegistrationWindowMillis)
            }

            val registration = Thread {
                manager.register(observation)
            }
            registration.start()
            parkingStarted.await(5, SECONDS) shouldBe true
            manager.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
            registration.join()

            observation.awaitRefresh() shouldBe true
            manager.close()
        }

    /**
     * An observation initialized through this manager stays under its
     * recovery management afterwards, so a later disconnect and reconnect
     * refreshes it again.
     */
    @Test
    fun `recover an observation supplied for initialization`() = runTest {
        val manager = createManager()
        val observation = FakeManagedObservation()

        manager.registerAndInitialize(observation)
        runCurrent()
        manager.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        manager.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.waitCalls shouldBe 1
        observation.refreshCalls shouldBe 2
        manager.close()
    }

    /**
     * Registration arriving after shutdown closes the observation instead of
     * retaining it: a registered observation would wait for a recovery that
     * this manager no longer performs.
     */
    @Test
    fun `close an observation registered after the manager has closed`() = runTest {
        val manager = createManager { ConnectionStatus.UNAVAILABLE }
        manager.close()
        val observation = FakeManagedObservation()

        manager.register(observation)
        runCurrent()

        observation.closeCalls shouldBe 1
        observation.waitCalls shouldBe 0
    }

    /**
     * Initialization performs no refresh once the manager has closed, and
     * reports the absence of an initial refresh by returning no job.
     */
    @Test
    fun `not initialize an observation created after the manager closed`() = runTest {
        val manager = createManager()
        manager.close()
        val observation = FakeManagedObservation()

        val job = manager.registerAndInitialize(observation)
        runCurrent()

        job shouldBe null
        observation.closeCalls shouldBe 1
        observation.refreshCalls shouldBe 0
    }

    /**
     * An observation that loses the race with [DataObservationManager.close]
     * is still closed. The closed status is therefore re-checked after the
     * observation has been added, since `close` closes what it knows about and
     * then clears the set.
     */
    @Test
    fun `unregister an observation closed while it is being registered`() = runTest {
        val manager = createManager { ConnectionStatus.UNAVAILABLE }
        val observation = FakeManagedObservation()
        observation.onParkingStarted = {
            manager.close()
        }

        manager.register(observation)
        manager.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.closeCalls shouldBe 1
        observation.refreshCalls shouldBe 0
    }

    @Test
    fun `refresh observations once after connection is restored`() = runTest {
        val manager = createManager()
        val first = FakeManagedObservation()
        val second = FakeManagedObservation()
        manager.register(first)
        manager.register(second)

        manager.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        manager.onConnectionStatusChanged(ConnectionStatus.CONNECTING)
        runCurrent()
        manager.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        manager.onConnectionStatusChanged(ConnectionStatus.CONNECTING)
        runCurrent()
        manager.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        first.waitCalls shouldBe 1
        second.waitCalls shouldBe 1
        first.refreshCalls shouldBe 1
        second.refreshCalls shouldBe 1
        manager.close()
    }

    @Test
    fun `not refresh on initial connection`() = runTest {
        val manager = createManager { ConnectionStatus.CONNECTING }
        val observation = FakeManagedObservation()
        manager.register(observation)

        manager.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.refreshCalls shouldBe 0
        manager.close()
    }

    @Test
    fun `recover waiting observation on first successful connection`() = runTest {
        val manager = createManager { ConnectionStatus.CONNECTING }
        val observation = FakeManagedObservation()
        observation.waitForConnection()
        manager.register(observation)

        manager.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.refreshCalls shouldBe 1
        manager.close()
    }

    @Test
    fun `recover observation registered while disconnected`() = runTest {
        val manager = createManager { ConnectionStatus.UNAVAILABLE }
        manager.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        val observation = FakeManagedObservation()

        manager.register(observation)
        manager.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.waitCalls shouldBe 1
        observation.refreshCalls shouldBe 1
        manager.close()
    }

    @Test
    fun `retry waiting observation while connection remains available`() = runTest {
        val manager = createManager()
        val observation = FakeManagedObservation()
        manager.register(observation)
        manager.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()
        observation.waitForConnection()

        manager.retryWhileConnected(observation)
        manager.retryWhileConnected(observation)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        observation.refreshCalls shouldBe 1
        observation.needsRecovery shouldBe false
        manager.close()
    }

    @Test
    fun `back off between repeated stream recovery attempts`() = runTest {
        val manager = createManager()
        val observation = FakeManagedObservation()
        observation.remainWaitingAfterRefresh = true
        manager.register(observation)
        manager.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()
        observation.waitForConnection()

        manager.retryWhileConnected(observation)
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        observation.refreshCalls shouldBe 0
        advanceTimeBy(1)
        runCurrent()
        observation.refreshCalls shouldBe 1
        advanceTimeBy(1_000)
        runCurrent()
        observation.refreshCalls shouldBe 2
        manager.close()
    }

    @Test
    fun `exclude unregistered observation from recovery`() = runTest {
        val manager = createManager()
        val observation = FakeManagedObservation()
        manager.register(observation)
        manager.unregister(observation)

        manager.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        manager.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.waitCalls shouldBe 0
        observation.refreshCalls shouldBe 0
        manager.close()
    }

    @Test
    fun `not cancel job supplied in coroutine context`() = runTest {
        val suppliedJob = backgroundScope.coroutineContext[Job]!!
        val manager = DataObservationManager(
            { ConnectionStatus.CONNECTED },
            backgroundScope.coroutineContext
        )
        manager.start()

        manager.close()
        manager.onConnectionStatusChanged(ConnectionStatus.CLOSED)

        suppliedJob.isActive shouldBe true
    }

    /**
     * Exercises a real [DataObservationImpl] against a subscription that blocks:
     * initialization returns while the subscription is still being created, and
     * the observation serves its fallback value until the refresh completes.
     */
    @Test
    fun `return from initialization while the initial subscription is pending`() =
        runBlocking {
            val manager = DataObservationManager({ ConnectionStatus.CONNECTED })
            manager.start()
            val subscribeStarted = CountDownLatch(1)
            val allowSubscribe = CountDownLatch(1)
            val observation = DataObservationImpl<String, String>(
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
                onCancelled = manager::unregister,
                onRecoveryNeeded = manager::retryWhileConnected
            )

            val job = requireNotNull(
                manager.registerAndInitialize(observation)
            )
            subscribeStarted.await(5, SECONDS) shouldBe true

            job.isCompleted shouldBe false
            observation.value shouldBe "fallback"
            observation.status.value shouldBe DataObservationStatus.Refreshing

            allowSubscribe.countDown()
            job.join()

            observation.value shouldBe "server data"
            observation.status.value shouldBe DataObservationStatus.Active
            manager.close()
        }

    @Test
    fun `close observations without cancelling subscriptions individually`() = runTest {
        val manager = createManager()
        val observation = FakeManagedObservation()
        manager.register(observation)

        manager.close()

        observation.closeCalls shouldBe 1
        observation.cancelCalls shouldBe 0
    }
}

private fun TestScope.createManager(
    connectionStatus: () -> ConnectionStatus = { ConnectionStatus.CONNECTED }
): DataObservationManager =
    DataObservationManager(
        connectionStatus,
        StandardTestDispatcher(testScheduler)
    ).also {
        it.start()
    }

private class FakeManagedObservation : ManagedDataObservation {

    var waitCalls: Int = 0
        private set
    var refreshCalls: Int = 0
        private set
    var cancelCalls: Int = 0
        private set
    var closeCalls: Int = 0
        private set
    var remainWaitingAfterRefresh: Boolean = false

    /**
     * Runs at the start of [waitForConnection], *before* this observation
     * records that it is parked.
     *
     * The real implementation performs the whole transition under its own lock,
     * so this models the instant at which an observation is registered but not
     * yet parked — the window a concurrent connection transition must not be
     * able to observe.
     */
    var onParkingStarted: (() -> Unit)? = null

    override var needsRecovery: Boolean = false
        private set

    override fun waitForConnection() {
        onParkingStarted?.invoke()
        waitCalls++
        needsRecovery = true
    }

    override suspend fun refresh() {
        refreshCalls++
        needsRecovery = remainWaitingAfterRefresh
        refreshed.countDown()
    }

    /**
     * Opens once [refresh] has been called at least once, letting a test on
     * another thread wait for a refresh dispatched to a real coroutine scope.
     */
    private val refreshed = CountDownLatch(1)

    /**
     * Waits for the first [refresh] call, returning `false` if none arrives.
     */
    fun awaitRefresh(): Boolean = refreshed.await(5, SECONDS)

    override fun cancel() {
        cancelCalls++
    }

    override fun close() {
        closeCalls++
    }
}
