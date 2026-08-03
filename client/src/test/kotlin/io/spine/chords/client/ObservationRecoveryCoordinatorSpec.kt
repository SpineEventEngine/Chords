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
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ObservationRecoveryCoordinatorSpec {

    @Test
    fun `process connection changes outside notifying thread`() = runTest {
        val coordinator = createCoordinator()
        val observation = FakeRecoverableObservation()
        coordinator.register(observation, ConnectionStatus.CONNECTED)

        coordinator.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)

        observation.waitCalls shouldBe 0
        runCurrent()
        observation.waitCalls shouldBe 1
        coordinator.close()
    }

    @Test
    fun `initialize a registered observation outside the calling thread`() = runTest {
        val coordinator = createCoordinator()
        val observation = FakeRecoverableObservation()

        val job = requireNotNull(
            coordinator.registerAndInitialize(observation, ConnectionStatus.CONNECTED)
        )

        observation.refreshCalls shouldBe 0
        runCurrent()
        observation.refreshCalls shouldBe 1
        job.isCompleted shouldBe true
        coordinator.close()
    }

    @Test
    fun `recover an observation supplied for initialization`() = runTest {
        val coordinator = createCoordinator()
        val observation = FakeRecoverableObservation()

        coordinator.registerAndInitialize(observation, ConnectionStatus.CONNECTED)
        runCurrent()
        coordinator.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        coordinator.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.waitCalls shouldBe 1
        observation.refreshCalls shouldBe 2
        coordinator.close()
    }

    @Test
    fun `close an observation registered after the coordinator has closed`() = runTest {
        val coordinator = createCoordinator()
        coordinator.close()
        val observation = FakeRecoverableObservation()

        coordinator.register(observation, ConnectionStatus.UNAVAILABLE)
        runCurrent()

        observation.closeCalls shouldBe 1
        observation.waitCalls shouldBe 0
    }

    @Test
    fun `not initialize an observation created after the coordinator closed`() = runTest {
        val coordinator = createCoordinator()
        coordinator.close()
        val observation = FakeRecoverableObservation()

        val job = coordinator.registerAndInitialize(
            observation,
            ConnectionStatus.CONNECTED
        )
        runCurrent()

        job shouldBe null
        observation.closeCalls shouldBe 1
        observation.refreshCalls shouldBe 0
    }

    @Test
    fun `unregister an observation closed while it is being registered`() = runTest {
        val coordinator = createCoordinator()
        val observation = FakeRecoverableObservation()
        observation.onWaitForConnection = {
            coordinator.close()
        }

        coordinator.register(observation, ConnectionStatus.UNAVAILABLE)
        coordinator.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.closeCalls shouldBe 1
        observation.refreshCalls shouldBe 0
    }

    @Test
    fun `refresh observations once after connection is restored`() = runTest {
        val coordinator = createCoordinator()
        val first = FakeRecoverableObservation()
        val second = FakeRecoverableObservation()
        coordinator.register(first, ConnectionStatus.CONNECTED)
        coordinator.register(second, ConnectionStatus.CONNECTED)

        coordinator.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        coordinator.onConnectionStatusChanged(ConnectionStatus.CONNECTING)
        runCurrent()
        coordinator.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        coordinator.onConnectionStatusChanged(ConnectionStatus.CONNECTING)
        runCurrent()
        coordinator.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        first.waitCalls shouldBe 1
        second.waitCalls shouldBe 1
        first.refreshCalls shouldBe 1
        second.refreshCalls shouldBe 1
        coordinator.close()
    }

    @Test
    fun `not refresh on initial connection`() = runTest {
        val coordinator = createCoordinator()
        val observation = FakeRecoverableObservation()
        coordinator.register(observation, ConnectionStatus.CONNECTING)

        coordinator.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.refreshCalls shouldBe 0
        coordinator.close()
    }

    @Test
    fun `recover waiting observation on first successful connection`() = runTest {
        val coordinator = createCoordinator()
        val observation = FakeRecoverableObservation()
        observation.waitForConnection()
        coordinator.register(observation, ConnectionStatus.CONNECTING)

        coordinator.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.refreshCalls shouldBe 1
        coordinator.close()
    }

    @Test
    fun `recover observation registered while disconnected`() = runTest {
        val coordinator = createCoordinator()
        coordinator.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        val observation = FakeRecoverableObservation()

        coordinator.register(observation, ConnectionStatus.UNAVAILABLE)
        coordinator.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.waitCalls shouldBe 1
        observation.refreshCalls shouldBe 1
        coordinator.close()
    }

    @Test
    fun `retry waiting observation while connection remains available`() = runTest {
        val coordinator = createCoordinator()
        val observation = FakeRecoverableObservation()
        coordinator.register(observation, ConnectionStatus.CONNECTED)
        coordinator.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()
        observation.waitForConnection()

        coordinator.retryWhileConnected(observation)
        coordinator.retryWhileConnected(observation)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        observation.refreshCalls shouldBe 1
        observation.needsRecovery shouldBe false
        coordinator.close()
    }

    @Test
    fun `back off between repeated stream recovery attempts`() = runTest {
        val coordinator = createCoordinator()
        val observation = FakeRecoverableObservation()
        observation.remainWaitingAfterRefresh = true
        coordinator.register(observation, ConnectionStatus.CONNECTED)
        coordinator.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()
        observation.waitForConnection()

        coordinator.retryWhileConnected(observation)
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
        coordinator.close()
    }

    @Test
    fun `exclude unregistered observation from recovery`() = runTest {
        val coordinator = createCoordinator()
        val observation = FakeRecoverableObservation()
        coordinator.register(observation, ConnectionStatus.CONNECTED)
        coordinator.unregister(observation)

        coordinator.onConnectionStatusChanged(ConnectionStatus.UNAVAILABLE)
        runCurrent()
        coordinator.onConnectionStatusChanged(ConnectionStatus.CONNECTED)
        runCurrent()

        observation.waitCalls shouldBe 0
        observation.refreshCalls shouldBe 0
        coordinator.close()
    }

    @Test
    fun `not cancel job supplied in coroutine context`() = runTest {
        val suppliedJob = backgroundScope.coroutineContext[Job]!!
        val coordinator = ObservationRecoveryCoordinator(
            backgroundScope.coroutineContext
        )
        coordinator.start()

        coordinator.close()
        coordinator.onConnectionStatusChanged(ConnectionStatus.CLOSED)

        suppliedJob.isActive shouldBe true
    }

    @Test
    fun `return from initialization while the initial subscription is pending`() =
        runBlocking {
            val coordinator = ObservationRecoveryCoordinator()
            coordinator.start()
            val subscribeStarted = CountDownLatch(1)
            val allowSubscribe = CountDownLatch(1)
            val observation = DataObservationImpl<String, String>(
                "fallback",
                { "server data" },
                { _, _ ->
                    subscribeStarted.countDown()
                    check(allowSubscribe.await(5, SECONDS)) {
                        "Timed out waiting to finish subscription setup."
                    }
                    ObservationSubscription { }
                },
                { _, update -> update },
                { ConnectionStatus.CONNECTED },
                coordinator::unregister,
                coordinator::retryWhileConnected
            )

            val job = requireNotNull(
                coordinator.registerAndInitialize(
                    observation,
                    ConnectionStatus.CONNECTED
                )
            )
            subscribeStarted.await(5, SECONDS) shouldBe true

            job.isCompleted shouldBe false
            observation.value shouldBe "fallback"
            observation.status.value shouldBe DataObservationStatus.Refreshing

            allowSubscribe.countDown()
            job.join()

            observation.value shouldBe "server data"
            observation.status.value shouldBe DataObservationStatus.Active
            coordinator.close()
        }

    @Test
    fun `close observations without cancelling subscriptions individually`() = runTest {
        val coordinator = createCoordinator()
        val observation = FakeRecoverableObservation()
        coordinator.register(observation, ConnectionStatus.CONNECTED)

        coordinator.close()

        observation.closeCalls shouldBe 1
        observation.cancelCalls shouldBe 0
    }
}

private fun TestScope.createCoordinator(): ObservationRecoveryCoordinator =
    ObservationRecoveryCoordinator(
        StandardTestDispatcher(testScheduler)
    ).also {
        it.start()
    }

private class FakeRecoverableObservation : RecoverableDataObservation {

    var waitCalls: Int = 0
        private set
    var refreshCalls: Int = 0
        private set
    var cancelCalls: Int = 0
        private set
    var closeCalls: Int = 0
        private set
    var remainWaitingAfterRefresh: Boolean = false
    var onWaitForConnection: (() -> Unit)? = null

    override var needsRecovery: Boolean = false
        private set

    override fun waitForConnection() {
        waitCalls++
        needsRecovery = true
        onWaitForConnection?.invoke()
    }

    override suspend fun refresh() {
        refreshCalls++
        needsRecovery = remainWaitingAfterRefresh
    }

    override fun cancel() {
        cancelCalls++
    }

    override fun close() {
        closeCalls++
    }
}
