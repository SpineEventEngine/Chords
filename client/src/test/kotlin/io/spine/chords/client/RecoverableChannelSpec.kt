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

import io.grpc.CallOptions
import io.grpc.Metadata
import io.grpc.Status
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.chords.client.testing.CallStatusRecorder
import io.spine.chords.client.testing.ConnectionChannel
import io.spine.client.QueryResponse
import io.spine.client.grpc.QueryServiceGrpc.getReadMethod
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies immediate release of stale RPCs independently of transport timeouts.
 */
@DisplayName("`RecoverableChannel` should")
internal class RecoverableChannelSpec {

    /**
     * The wrapper preserves the managed channel's explicit connection controls.
     */
    @Test
    fun `forward idle and backoff reset requests`() {
        val transport = ConnectionChannel()
        val channel = RecoverableChannel(transport)

        channel.enterIdle()
        channel.resetConnectBackoff()

        transport.effects shouldBe listOf("idle", "reset backoff")
        channel.shutdownNow()
    }

    /**
     * Both pending reads and long-lived streams use the same call lifecycle.
     */
    @Test
    fun `interrupt pending calls and start a fresh connection without replay`() {
        val transport = ConnectionChannel()
        val channel = RecoverableChannel(transport)
        val result = CallStatusRecorder<QueryResponse>()
        val call = channel.newCall(getReadMethod(), CallOptions.DEFAULT)
        call.start(result, Metadata())

        channel.reconnect()

        result.statuses shouldBe listOf(Status.Code.CANCELLED)
        transport.effects shouldBe listOf("idle", "reset backoff", "connect")
        transport.calls.size shouldBe 1
        transport.calls.single().cancelCount shouldBe 1
        channel.shutdownNow()
    }

    /**
     * Reconnection must exclude completed calls and calls already cancelled by their caller.
     */
    @Test
    fun `release completed and cancelled calls before the next recovery`() {
        val transport = ConnectionChannel()
        val channel = RecoverableChannel(transport)
        val completed = channel.newCall(getReadMethod(), CallOptions.DEFAULT)
        completed.start(CallStatusRecorder(), Metadata())
        transport.calls[0].complete(Status.OK)
        val cancelled = channel.newCall(getReadMethod(), CallOptions.DEFAULT)
        cancelled.start(CallStatusRecorder(), Metadata())
        cancelled.cancel("The view closed.", null)

        channel.reconnect()

        transport.calls[0].cancelCount shouldBe 0
        transport.calls[1].cancelCount shouldBe 1
        val replacement = channel.newCall(getReadMethod(), CallOptions.DEFAULT)
        val replacementResult = CallStatusRecorder<QueryResponse>()
        replacement.start(replacementResult, Metadata())
        transport.calls[2].complete(Status.OK)
        replacementResult.statuses shouldBe listOf(Status.Code.OK)
        channel.reconnect()
        transport.calls[2].cancelCount shouldBe 0
        channel.shutdownNow()
    }

    /**
     * An OS wake callback can arrive while the RPC is still installing its response listener.
     */
    @Test
    fun `interrupt a call when recovery arrives during startup`() {
        val transport = ConnectionChannel()
        val channel = RecoverableChannel(transport)
        transport.onStart = { channel.reconnect() }
        val result = CallStatusRecorder<QueryResponse>()

        channel.newCall(getReadMethod(), CallOptions.DEFAULT)
            .start(result, Metadata())

        result.statuses shouldBe listOf(Status.Code.CANCELLED)
        transport.calls.single().cancelCount shouldBe 1
        channel.shutdownNow()
    }

    /**
     * Calls using newly published connection state must survive the current interruption.
     */
    @Test
    fun `publish connection state between old and new calls`() {
        val transport = ConnectionChannel()
        val channel = RecoverableChannel(transport)
        val previousResult = CallStatusRecorder<QueryResponse>()
        val previous = channel.newCall(getReadMethod(), CallOptions.DEFAULT)
        previous.start(previousResult, Metadata())
        val nextResult = CallStatusRecorder<QueryResponse>()

        channel.reconnect {
            previousResult.statuses.shouldBeEmpty()
            channel.newCall(getReadMethod(), CallOptions.DEFAULT)
                .start(nextResult, Metadata())
        }

        previousResult.statuses shouldBe listOf(Status.Code.CANCELLED)
        nextResult.statuses.shouldBeEmpty()
        transport.calls[1].cancelCount shouldBe 0
        channel.reconnect()
        nextResult.statuses shouldBe listOf(Status.Code.CANCELLED)
        transport.calls[1].cancelCount shouldBe 1
        channel.shutdownNow()
    }

    /**
     * A late wake event must not reopen a transport that the application has closed.
     */
    @Test
    fun `ignore recovery after shutdown`() {
        val transport = ConnectionChannel()
        val channel = RecoverableChannel(transport)
        channel.shutdownNow()

        channel.reconnect()

        transport.effects shouldBe listOf("shutdown")
        channel.isShutdown shouldBe true
    }
}
