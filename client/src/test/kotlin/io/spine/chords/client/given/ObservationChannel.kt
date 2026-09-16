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

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.spine.base.EntityState
import io.spine.base.Identifier
import io.spine.chords.client.DesktopClient
import io.spine.client.EntityId
import io.spine.client.EntityStateUpdate
import io.spine.client.EntityStateWithVersion
import io.spine.client.EntityUpdates
import io.spine.client.QueryResponse
import io.spine.client.Subscription
import io.spine.client.SubscriptionId
import io.spine.client.SubscriptionUpdate
import io.spine.client.Topic
import io.spine.core.Responses
import io.spine.protobuf.AnyPacker
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Supplies protocol responses to the real Spine client without network or transport threads.
 */
@Suppress("TooManyFunctions" /* Implements the gRPC channel and drives observation responses. */)
internal class ObservationChannel : ManagedChannel(), AutoCloseable {

    /**
     * Supplies the next complete query result.
     */
    var items: List<EntityState> = emptyList()

    /**
     * Delivers subscription changes during a read to exercise refresh ordering.
     */
    var onRead: () -> Unit = {}

    /**
     * Counts reads across the request thread and test thread.
     */
    private val reads = AtomicInteger()

    /**
     * Identifies when an asynchronous reread has begun.
     */
    val readCount: Int get() = reads.get()

    /**
     * Retains active streams until the client cancels them.
     */
    private val streams = ConcurrentHashMap<Subscription, (SubscriptionUpdate) -> Unit>()

    /**
     * Prevents requests after the owning client closes.
     */
    private var closed = false

    /**
     * Exercises the public observation methods with the production subscription adapter.
     */
    val client = DesktopClient(channel = this, user = { null })

    /**
     * Creates a call that delivers the response selected by its gRPC method.
     */
    override fun <ReqT, RespT> newCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions
    ): ClientCall<ReqT, RespT> = object : ClientCall<ReqT, RespT>() {

        /**
         * Receives the response through Spine's normal gRPC callbacks.
         */
        private var responseListener: Listener<RespT>? = null

        /**
         * Holds the request until the client finishes sending it.
         */
        private var request: ReqT? = null

        /**
         * Installs the listener before any response is delivered.
         */
        override fun start(listener: Listener<RespT>, headers: Metadata) {
            responseListener = listener
        }

        /**
         * Stores the unary request for completion.
         */
        override fun sendMessage(message: ReqT) {
            request = message
        }

        /**
         * Accepts demand; these tests deliver bounded responses synchronously.
         */
        override fun request(numMessages: Int): Unit = Unit

        /**
         * Leaves captured callbacks callable after cancellation to simulate late delivery.
         */
        override fun cancel(message: String?, cause: Throwable?): Unit = Unit

        /**
         * Delivers the unary result or retains an active subscription stream.
         */
        override fun halfClose() {
            check(!closed)
            val listener = checkNotNull(responseListener)
            respond(method = method.bareMethodName, request = checkNotNull(request)) { response ->
                // Each method below supplies the response type declared by its gRPC descriptor.
                @Suppress("UNCHECKED_CAST")
                listener.onMessage(response as RespT)
            }
            if (method.bareMethodName != "Activate") {
                listener.onClose(Status.OK, Metadata())
            }
        }
    }

    /**
     * Implements only the read and subscription methods exercised by observation tests.
     */
    private fun respond(method: String?, request: Any, reply: (Any) -> Unit) {
        when (method) {
            "Read" -> {
                reads.incrementAndGet()
                val result = items.map {
                    EntityStateWithVersion.newBuilder()
                        .setState(AnyPacker.pack(it))
                        .build()
                }
                onRead()
                reply(QueryResponse.newBuilder()
                    .setResponse(Responses.ok())
                    .addAllMessage(result)
                    .build())
            }
            "Subscribe" -> reply(Subscription.newBuilder()
                .setId(SubscriptionId.newBuilder()
                    .setValue(Identifier.newUuid()))
                .setTopic(request as Topic)
                .build())
            "Activate" -> streams[request as Subscription] = reply
            "Cancel" -> {
                streams.remove(request as Subscription)
                reply(Responses.ok())
            }
            else -> error("Unexpected gRPC method: $method")
        }
    }

    /**
     * Reports an entity as archived, deleted, or outside the subscription filter.
     */
    fun remove(id: Any) {
        emit(removalUpdate(id))
    }

    /**
     * Captures current streams for late delivery and reports how many callbacks receive it.
     */
    fun captureRemoval(id: Any): () -> Int {
        val recipients = streams.toMap()
        val update = removalUpdate(id)
        return { emit(update, recipients) }
    }

    /**
     * Wraps an entity ID in Spine's removal notification format.
     */
    private fun removalUpdate(id: Any): EntityStateUpdate {
        val entityId = EntityId.newBuilder()
            .setId(Identifier.pack(id))
            .build()
        return EntityStateUpdate.newBuilder()
            .setId(AnyPacker.pack(entityId))
            .setNoLongerMatching(true)
            .build()
    }

    /**
     * Reports a matching entity state, including reappearance after a removal.
     */
    fun update(entity: EntityState) {
        emit(EntityStateUpdate.newBuilder()
            .setState(AnyPacker.pack(entity))
            .build())
    }

    /**
     * Sends a wire-format update to the selected streams and counts delivered callbacks.
     */
    private fun emit(
        update: EntityStateUpdate,
        recipients: Map<Subscription, (SubscriptionUpdate) -> Unit> = streams.toMap()
    ): Int {
        var delivered = 0
        recipients.forEach { (subscription, reply) ->
            reply(SubscriptionUpdate.newBuilder()
                .setSubscription(subscription)
                .setResponse(Responses.ok())
                .setEntityUpdates(EntityUpdates.newBuilder()
                    .addUpdate(update))
                .build())
            delivered++
        }
        return delivered
    }

    /**
     * Identifies the test channel without resolving a network host.
     */
    override fun authority(): String = "observation-test"

    /**
     * Keeps the channel connected until the owning client closes.
     */
    override fun getState(requestConnection: Boolean): ConnectivityState =
        if (closed) ConnectivityState.SHUTDOWN else ConnectivityState.READY

    /**
     * Keeps connection changes outside these subscription tests.
     */
    override fun notifyWhenStateChanged(source: ConnectivityState, callback: Runnable): Unit = Unit

    /**
     * Reports whether the owning client has closed the channel.
     */
    override fun isShutdown(): Boolean = closed

    /**
     * Reports immediate termination because no transport threads are started.
     */
    override fun isTerminated(): Boolean = closed

    /**
     * Returns immediately because this channel has no asynchronous shutdown.
     */
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = closed

    /**
     * Releases retained subscription callbacks.
     */
    override fun shutdown(): ManagedChannel {
        closed = true
        streams.clear()
        return this
    }

    /**
     * Closes the controlled channel without a transport grace period.
     */
    override fun shutdownNow(): ManagedChannel = shutdown()

    /**
     * Closes the client and its observation scope after each test.
     */
    override fun close() {
        client.close()
    }
}
