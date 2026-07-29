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
import io.spine.client.Subscription
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Test

internal class EventSubscriptionImplSpec {

    @Test
    fun `not cancel subscription after its stream fails`() {
        val cancelCalls = AtomicInteger()
        val eventSubscription = EventSubscriptionImpl {
            cancelCalls.incrementAndGet()
        }
        eventSubscription.install(Subscription.getDefaultInstance())

        eventSubscription.onStreamingFailure()
        eventSubscription.cancel()

        eventSubscription.active shouldBe false
        eventSubscription.canceled shouldBe true
        cancelCalls.get() shouldBe 0
    }

    @Test
    fun `not install subscription after synchronous streaming failure`() {
        val cancelCalls = AtomicInteger()
        val eventSubscription = EventSubscriptionImpl {
            cancelCalls.incrementAndGet()
        }

        eventSubscription.onStreamingFailure()
        eventSubscription.install(Subscription.getDefaultInstance())
        eventSubscription.cancel()

        eventSubscription.active shouldBe false
        cancelCalls.get() shouldBe 0
    }

    @Test
    fun `not cancel late subscription after failure followed by cancellation`() {
        val cancelCalls = AtomicInteger()
        val eventSubscription = EventSubscriptionImpl {
            cancelCalls.incrementAndGet()
        }

        eventSubscription.onStreamingFailure()
        eventSubscription.cancel()
        eventSubscription.install(Subscription.getDefaultInstance())

        eventSubscription.active shouldBe false
        cancelCalls.get() shouldBe 0
    }

    @Test
    fun `cancel active subscription explicitly`() {
        val cancelCalls = AtomicInteger()
        val eventSubscription = EventSubscriptionImpl {
            cancelCalls.incrementAndGet()
        }
        eventSubscription.install(Subscription.getDefaultInstance())

        eventSubscription.cancel()
        eventSubscription.cancel()

        eventSubscription.active shouldBe false
        eventSubscription.canceled shouldBe true
        cancelCalls.get() shouldBe 1
    }

    @Test
    fun `cancel subscription installed after explicit cancellation`() {
        val cancelCalls = AtomicInteger()
        val eventSubscription = EventSubscriptionImpl {
            cancelCalls.incrementAndGet()
        }

        eventSubscription.cancel()
        eventSubscription.install(Subscription.getDefaultInstance())

        eventSubscription.active shouldBe false
        cancelCalls.get() shouldBe 1
    }

    @Test
    fun `ignore cancellation request failure for active subscription`() {
        val eventSubscription = EventSubscriptionImpl {
            throw IllegalStateException("Subscription is already gone.")
        }
        eventSubscription.install(Subscription.getDefaultInstance())

        eventSubscription.cancel()

        eventSubscription.active shouldBe false
        eventSubscription.canceled shouldBe true
    }

    @Test
    fun `ignore cancellation request failure for late subscription`() {
        val eventSubscription = EventSubscriptionImpl {
            throw IllegalStateException("Subscription is already gone.")
        }
        eventSubscription.cancel()

        eventSubscription.install(Subscription.getDefaultInstance())

        eventSubscription.active shouldBe false
        eventSubscription.canceled shouldBe true
    }
}
