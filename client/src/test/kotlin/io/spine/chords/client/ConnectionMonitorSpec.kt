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

import io.grpc.ConnectivityState
import io.grpc.ConnectivityState.CONNECTING
import io.grpc.ConnectivityState.IDLE
import io.grpc.ConnectivityState.READY
import io.grpc.ConnectivityState.SHUTDOWN
import io.grpc.ConnectivityState.TRANSIENT_FAILURE
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread
import org.junit.jupiter.api.Test

internal class ConnectionMonitorSpec {

    @Test
    fun `report channel connectivity and request reconnection`() {
        val channel = FakeConnectivityChannel()
        val statuses = mutableListOf<ConnectionStatus>()
        val monitor = ConnectionMonitor(
            channel::getState,
            channel::notifyWhenStateChanged
        ) { status ->
            statuses += status
        }

        monitor.start()
        channel.moveTo(CONNECTING)
        channel.moveTo(READY)
        channel.moveTo(TRANSIENT_FAILURE)
        channel.moveTo(CONNECTING)
        channel.moveTo(READY)

        channel.requestConnectionCalls shouldBe 6
        statuses shouldContainExactly listOf(
            ConnectionStatus.CONNECTING,
            ConnectionStatus.CONNECTED,
            ConnectionStatus.UNAVAILABLE,
            ConnectionStatus.CONNECTING,
            ConnectionStatus.CONNECTED
        )
        monitor.status.value shouldBe ConnectionStatus.CONNECTED
    }

    @Test
    fun `stop observing after closing`() {
        val channel = FakeConnectivityChannel()
        val monitor = ConnectionMonitor(
            channel::getState,
            channel::notifyWhenStateChanged
        ) { }

        monitor.start()
        monitor.close()
        channel.moveTo(READY)

        monitor.status.value shouldBe ConnectionStatus.CLOSED
        channel.requestConnectionCalls shouldBe 1
    }

    @Test
    fun `close when channel shuts down`() {
        val channel = FakeConnectivityChannel()
        val monitor = ConnectionMonitor(
            channel::getState,
            channel::notifyWhenStateChanged
        ) { }

        monitor.start()
        channel.moveTo(SHUTDOWN)

        monitor.status.value shouldBe ConnectionStatus.CLOSED
    }

    @Test
    fun `keep closed status when connectivity callback is already running`() {
        val channel = FakeConnectivityChannel()
        val stateReadStarted = CountDownLatch(1)
        val allowStateRead = CountDownLatch(1)
        val monitor = ConnectionMonitor(
            channel::getState,
            channel::notifyWhenStateChanged
        ) { }
        monitor.start()
        channel.beforeGetState = {
            stateReadStarted.countDown()
            allowStateRead.await(5, SECONDS)
        }
        val transition = thread {
            channel.moveTo(READY)
        }
        stateReadStarted.await(5, SECONDS) shouldBe true

        monitor.close()
        allowStateRead.countDown()
        transition.join(5_000)

        transition.isAlive shouldBe false
        monitor.status.value shouldBe ConnectionStatus.CLOSED
    }
}

private class FakeConnectivityChannel {

    var requestConnectionCalls: Int = 0
        private set

    private var state: ConnectivityState = IDLE
    private var callback: Runnable? = null
    var beforeGetState: (() -> Unit)? = null

    fun getState(requestConnection: Boolean): ConnectivityState {
        beforeGetState?.invoke()
        if (requestConnection) {
            requestConnectionCalls++
        }
        return state
    }

    fun notifyWhenStateChanged(source: ConnectivityState, callback: Runnable) {
        check(source == state)
        this.callback = callback
    }

    fun moveTo(newState: ConnectivityState) {
        state = newState
        val stateChanged = callback
        callback = null
        stateChanged?.run()
    }
}
