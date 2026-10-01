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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.spine.chords.client.ConnectionStatus.CONNECTED
import io.spine.chords.client.ConnectionStatus.UNAVAILABLE
import io.spine.chords.client.DataObservationStatus.Active
import io.spine.chords.client.DataObservationStatus.Cancelled
import io.spine.chords.client.DataObservationStatus.Failed
import io.spine.chords.client.DataObservationStatus.Refreshing
import io.spine.chords.client.DataObservationStatus.WaitingForConnection
import io.spine.chords.client.DataPageCursor.End
import io.spine.chords.client.DataPageCursor.Start
import io.spine.chords.client.given.ObservedItemPages.items
import io.spine.chords.client.given.ObservedItemPages.query
import io.spine.chords.client.given.PagedDataNavigatorSpecEnv.navigator
import io.spine.chords.client.testing.ConfigurablePagedDataSource
import io.spine.chords.client.testing.NavigatorCallbackProbe
import io.spine.chords.client.testing.ObservationChannel
import io.spine.chords.client.testing.PagedDataNavigatorScene
import io.spine.chords.client.testing.awaitCondition
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Verifies navigation, coroutine ownership, request replacement, and Compose lifetime.
 */
@DisplayName("`PagedDataNavigator` should")
@Suppress("LargeClass" /* One suite covers navigation, publication, and cancellation. */)
internal class PagedDataNavigatorSpec {

    /**
     * Groups cursor, page-boundary, and live-update navigation checks.
     */
    @Nested
    @DisplayName("navigate")
    inner class Navigation {

        /**
         * A direct final page permits adjacent navigation and stays fixed on source changes.
         */
        @ParameterizedTest(name = "last page with ascending={0}")
        @ValueSource(booleans = [true, false])
        fun `to the final page without loading intervening pages`(ascending: Boolean): Unit =
            runBlocking {
                val source = ConfigurablePagedDataSource(pageSize = 3, ascending = ascending)
                source.items = (1..8).toList()
                val ordered = if (ascending) source.items else source.items.reversed()
                navigator(source, CoroutineScope(coroutineContext + Unconfined)).use { pages ->
                    pages.last()

                    pages.items shouldBe ordered.takeLast(3)
                    pages.canGoNext shouldBe false
                    pages.canGoPrevious shouldBe true
                    source.cursors shouldBe listOf(Start, End)
                    source.subscriptions shouldBe 0
                    source.items = (0..9).toList()
                    source.updates.tryEmit(Unit)
                    pages.items shouldBe ordered.takeLast(3)

                    pages.previous()
                    pages.items shouldBe ordered.drop(2)
                        .take(3)
                    pages.next()
                    pages.items shouldBe if (ascending) listOf(6, 7, 8) else listOf(3, 2, 1)
                    pages.first()
                    pages.isFirstPage shouldBe true
                    source.subscriptions shouldBe 1
                }
                source.subscriptions shouldBe 0
            }

        /**
         * A final-page request observes live data when no earlier page exists.
         */
        @ParameterizedTest
        @ValueSource(ints = [0, 1, 3])
        fun `to live data when the final page is also the first`(count: Int): Unit = runBlocking {
            val source = ConfigurablePagedDataSource(pageSize = 3)
            source.items = (1..count).toList()
            navigator(source, CoroutineScope(coroutineContext + Unconfined)).use { pages ->
                pages.last()

                pages.items shouldBe source.items.reversed()
                pages.isFirstPage shouldBe true
                pages.canGoPrevious shouldBe false
                pages.canGoNext shouldBe false
                source.subscriptions shouldBe 1
                source.items = (1..4).toList()
                source.updates.tryEmit(Unit)
                pages.items shouldBe listOf(4, 3, 2)
                pages.canGoNext shouldBe true
            }
        }

        /**
         * Direct navigation includes its target in both directions and can resume live data.
         */
        @ParameterizedTest(name = "seek with ascending={0}")
        @ValueSource(booleans = [true, false])
        fun `directly without loading intervening pages`(ascending: Boolean): Unit = runBlocking {
            val source = ConfigurablePagedDataSource(pageSize = 3, ascending = ascending)
            source.items = (1..9).toList()
            navigator(source, CoroutineScope(coroutineContext + Unconfined)).use { pages ->
                pages.seek(5)

                pages.items shouldBe if (ascending) listOf(5, 6, 7) else listOf(5, 4, 3)
                source.cursors.size shouldBe 2
                source.subscriptions shouldBe 0
                pages.next()
                pages.items shouldBe if (ascending) listOf(8, 9) else listOf(2, 1)
                pages.previous()
                pages.items shouldBe if (ascending) listOf(5, 6, 7) else listOf(5, 4, 3)

                source.items = (0..10).toList()
                source.updates.tryEmit(Unit)
                pages.items shouldBe if (ascending) listOf(5, 6, 7) else listOf(5, 4, 3)
                pages.seek(if (ascending) 0 else 10)
                pages.items shouldBe if (ascending) listOf(0, 1, 2) else listOf(10, 9, 8)
                pages.isFirstPage shouldBe true
                source.subscriptions shouldBe 1

                pages.seek(if (ascending) 100 else -1)
                pages.items.shouldBeEmpty()
                pages.canGoPrevious shouldBe true
                pages.previous()
                pages.isFirstPage shouldBe true
            }
            source.subscriptions shouldBe 0
        }

        /**
         * Forward and backward navigation preserve contiguous values in both display orders.
         */
        @ParameterizedTest(name = "navigate with ascending={0}")
        @ValueSource(booleans = [true, false])
        fun `in either sort direction and resume live updates`(ascending: Boolean): Unit =
            runBlocking {
                val source = ConfigurablePagedDataSource(pageSize = 3, ascending = ascending)
                source.items = (1..8).toList()
                val expected = if (ascending) source.items else source.items.reversed()
                navigator(source, CoroutineScope(coroutineContext + Unconfined)).use { pages ->
                    val firstKey = pages.pageKey
                    pages.items shouldBe expected.take(3)
                    source.subscriptions shouldBe 1
                    pages.next()
                    pages.items shouldBe expected.drop(3)
                        .take(3)
                    source.subscriptions shouldBe 0
                    pages.next()
                    pages.items shouldBe expected.drop(6)
                    pages.canGoNext shouldBe false
                    pages.previous()
                    pages.items shouldBe expected.drop(3)
                        .take(3)
                    pages.previous()
                    pages.items shouldBe expected.take(3)
                    pages.isFirstPage shouldBe true
                    source.subscriptions shouldBe 1

                    source.items = (0..9).toList()
                    source.updates.tryEmit(Unit)

                    pages.items shouldBe if (ascending) listOf(0, 1, 2) else listOf(9, 8, 7)
                    pages.pageKey shouldBe firstKey
                }
                source.subscriptions shouldBe 0
            }

        /**
         * Deleting the remaining fixed-page items still leaves a route back to live data.
         */
        @Test
        fun `back to the first page from an emptied page`(): Unit = runBlocking {
            val source = ConfigurablePagedDataSource(pageSize = 3)
            source.items = (1..5).toList()
            navigator(source, CoroutineScope(coroutineContext + Unconfined)).use { pages ->
                source.items = (3..5).toList()
                pages.next()
                pages.items.shouldBeEmpty()

                pages.previous()

                pages.items shouldBe listOf(5, 4, 3)
                pages.isFirstPage shouldBe true
                source.subscriptions shouldBe 1
            }
        }
    }

    /**
     * An exact last page stays fixed when newer data appears.
     */
    @Test
    fun `retain an exact final page until navigation`(): Unit = runBlocking {
        val source = ConfigurablePagedDataSource(pageSize = 3)
        source.items = (1..6).toList()
        navigator(source, CoroutineScope(coroutineContext + Unconfined)).use { pages ->
            pages.next()
            pages.items shouldBe listOf(3, 2, 1)
            pages.canGoNext shouldBe false

            source.items = (1..7).toList()
            source.updates.tryEmit(Unit)

            pages.items shouldBe listOf(3, 2, 1)
            source.subscriptions shouldBe 0
            pages.first()
            pages.items shouldBe listOf(7, 6, 5)
        }
    }

    /**
     * Loading keeps the displayed page and scroll key while disabling adjacent navigation.
     */
    @Test
    fun `retain displayed items while another page is pending`(): Unit = runBlocking {
        val source = ConfigurablePagedDataSource(pageSize = 3)
        source.items = (1..8).toList()
        navigator(source, CoroutineScope(coroutineContext + Unconfined)).use { pages ->
            val firstKey = pages.pageKey
            val response = CompletableDeferred<Unit>()
            source.pending = response
            try {
                pages.next()

                pages.status shouldBe Refreshing
                pages.canGoNext shouldBe false
                pages.canGoPrevious shouldBe false
                pages.items shouldBe listOf(8, 7, 6)
                pages.pageKey shouldBe firstKey
                pages.next()
                pages.previous()
                source.cursors.size shouldBe 2

                response.complete(Unit)
                pages.awaitItems() shouldBe listOf(5, 4, 3)
                pages.canGoNext shouldBe true
                pages.canGoPrevious shouldBe true
                (pages.pageKey != firstKey) shouldBe true
            } finally {
                response.complete(Unit)
            }
        }
    }

    /**
     * A terminal failure exposes its cause and retries the failed cursor without losing items.
     */
    @Test
    fun `expose failures and retry the requested page`(): Unit = runBlocking {
        val source = ConfigurablePagedDataSource(pageSize = 3)
        source.items = (1..5).toList()
        navigator(source, CoroutineScope(coroutineContext + Unconfined)).use { pages ->
            val failure = IllegalStateException("Rejected query")
            source.failure = failure

            pages.next()

            pages.status shouldBe Failed(failure)
            pages.items shouldBe listOf(5, 4, 3)
            pages.canGoNext shouldBe false
            pages.canGoPrevious shouldBe false
            shouldThrow<IllegalStateException> { pages.awaitItems() } shouldBeSameInstanceAs failure

            pages.retry()

            pages.awaitItems() shouldBe listOf(2, 1)
            source.cursors.takeLast(2)
                .distinct()
                .size shouldBe 1
        }
    }

    /**
     * Replacing a pending request keeps its waiter attached to the newest requested page.
     */
    @ParameterizedTest(name = "replace pending read using retry={0}")
    @ValueSource(booleans = [true, false])
    fun `follow a replacement request without cancelling its waiter`(retry: Boolean): Unit =
        runBlocking {
            val source = ConfigurablePagedDataSource(pageSize = 3)
            source.items = (1..5).toList()
            navigator(source, CoroutineScope(coroutineContext + Unconfined)).use { pages ->
                val response = CompletableDeferred<Unit>()
                source.pending = response
                try {
                    pages.next()
                    val waiting = async(start = UNDISPATCHED) { pages.awaitItems() }

                    if (retry) pages.retry() else pages.first()
                    source.items = (1..6).toList()
                    response.complete(Unit)

                    withTimeout(5_000) {
                        waiting.await() shouldBe if (retry) listOf(2, 1) else listOf(6, 5, 4)
                    }
                    waiting.isCancelled shouldBe false
                    pages.status shouldBe Active
                } finally {
                    response.complete(Unit)
                }
            }
        }

    /**
     * A stopped or empty observation ends loading even if the client still reports connected.
     */
    @ParameterizedTest(name = "complete observation before initial value={0}")
    @ValueSource(booleans = [true, false])
    fun `cancel when the live stream completes normally`(initially: Boolean): Unit = runBlocking {
        val source = ConfigurablePagedDataSource()
        source.complete = initially
        navigator(source, CoroutineScope(coroutineContext + Unconfined)).use { pages ->
            if (!initially) {
                pages.status shouldBe Active
                source.complete = true
                source.updates.tryEmit(Unit)
            }

            pages.status shouldBe Cancelled
            shouldThrow<CancellationException> { pages.awaitItems() }
            source.subscriptions shouldBe 0
        }
    }

    /**
     * The first page exposes automatic recovery and retains its last successful values.
     */
    @Test
    fun `publish first-page connection status consistently`(): Unit = runBlocking {
        val source = ConfigurablePagedDataSource(pageSize = 3)
        source.items = (1..5).toList()
        source.status = WaitingForConnection
        navigator(source, CoroutineScope(coroutineContext + Unconfined)).use { pages ->
            pages.status shouldBe WaitingForConnection
            pages.items.shouldBeEmpty()
            source.status = Active
            source.updates.tryEmit(Unit)
            pages.items shouldBe listOf(5, 4, 3)
            source.status = WaitingForConnection
            source.updates.tryEmit(Unit)

            pages.status shouldBe WaitingForConnection
            pages.canGoNext shouldBe false
            pages.items shouldBe listOf(5, 4, 3)
            source.status = Active
            source.updates.tryEmit(Unit)
            pages.canGoNext shouldBe true
        }
    }

    /**
     * Transport cancellation is transient; a disconnected fixed page waits without polling reads.
     */
    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `wait for a connection before retrying a fixed page`() = runTest {
        val connection = MutableStateFlow(CONNECTED)
        val source = ConfigurablePagedDataSource(pageSize = 3)
        source.items = (1..5).toList()
        navigator(source = source, scope = backgroundScope, connection = connection).use { pages ->
            runCurrent()
            source.failure = Status.CANCELLED.asRuntimeException()
            pages.next()
            runCurrent()
            pages.status shouldBe WaitingForConnection
            connection.value = UNAVAILABLE

            advanceTimeBy(10_000)
            runCurrent()

            source.cursors.size shouldBe 2
            pages.status shouldBe WaitingForConnection
            connection.value = CONNECTED
            runCurrent()
            pages.awaitItems() shouldBe listOf(2, 1)
            source.cursors.size shouldBe 3
        }
    }

    /**
     * Cancelling one caller scope closes only its navigators and leaves the shared client open.
     */
    @Test
    fun `cancel only navigators in the cancelled caller scope`(): Unit = runBlocking {
        ObservationChannel().use { source ->
            source.items = items(4)
            val firstScope = CoroutineScope(coroutineContext + Job())
            val secondScope = CoroutineScope(coroutineContext + Job())
            try {
                PagedDataNavigator(query = query(), client = source.client, scope = firstScope)
                    .use { first ->
                        PagedDataNavigator(
                            query = query(), client = source.client, scope = secondScope
                        ).use { second ->
                            withTimeout(5_000) {
                                first.awaitItems()
                                second.awaitItems()
                            }
                            firstScope.cancel()

                            awaitCondition { source.cancelCount == 1 }
                            first.status shouldBe Cancelled
                            second.status shouldBe Active
                            source.client.isOpen shouldBe true
                        }
                    }
            } finally {
                firstScope.cancel()
                secondScope.cancel()
            }
            awaitCondition { source.cancelCount == 2 }
        }
    }

    /**
     * Cancellation releases waiters before even a noncooperative response finishes.
     */
    @ParameterizedTest(name = "cancel through caller scope={0}")
    @ValueSource(booleans = [true, false])
    fun `release a pending waiter and reject late data on cancellation`(parent: Boolean): Unit =
        runBlocking {
            val scope = CoroutineScope(coroutineContext + Job() + Unconfined)
            val source = ConfigurablePagedDataSource()
            val response = CompletableDeferred<Unit>()
            source.pending = response
            source.items = listOf(1)
            try {
                navigator(source, scope).use { pages ->
                    val waiting = async(start = UNDISPATCHED) { pages.awaitItems() }

                    if (parent) scope.cancel() else pages.close()

                    withTimeout(5_000) {
                        shouldThrow<CancellationException> { waiting.await() }
                    }
                    response.complete(Unit)
                    pages.status shouldBe Cancelled
                    pages.items.shouldBeEmpty()
                    pages.next()
                    pages.previous()
                    pages.first()
                    pages.retry()
                    source.cursors.size shouldBe 1
                }
            } finally {
                response.complete(Unit)
                scope.cancel()
            }
        }

    /**
     * Concurrent callers cannot let an old response overwrite the newest selection.
     */
    @Test
    fun `serialize navigation and publication on a multithreaded dispatcher`(): Unit = runBlocking {
        val scope = CoroutineScope(coroutineContext + Default)
        val source = ConfigurablePagedDataSource(pageSize = 3)
        source.items = (1..5).toList()
        navigator(source, scope).use { pages ->
            withTimeout(5_000) { pages.awaitItems() }
            val response = CompletableDeferred<Unit>()
            source.pending = response
            try {
                pages.next()
                awaitCondition { source.pending == null }
                val waiting = async { pages.awaitItems() }
                val replace = launch(Default) { pages.first() }
                replace.join()
                source.items = (10..15).toList()
                response.complete(Unit)

                withTimeout(5_000) { waiting.await() shouldBe listOf(15, 14, 13) }
                pages.status shouldBe Active
                pages.isFirstPage shouldBe true
            } finally {
                response.complete(Unit)
            }
        }
    }

    /**
     * A resumed UI waiter may need another thread to inspect the page before dispatch returns.
     */
    @Test
    fun `release its monitor before resuming a page waiter`(): Unit = runBlocking {
        val scope = CoroutineScope(coroutineContext + Default)
        val response = CompletableDeferred<Unit>()
        val source = ConfigurablePagedDataSource(pageSize = 3)
        source.items = (1..3).toList()
        source.pending = response
        try {
            navigator(source, scope).use { pages ->
                awaitCondition { source.pending == null }
                NavigatorCallbackProbe {
                    pages.status shouldBe Active
                    pages.items shouldBe listOf(3, 2, 1)
                }.use { callback ->
                    val waiting = async(callback, start = UNDISPATCHED) { pages.awaitItems() }

                    response.complete(Unit)

                    withTimeout(5_000) { waiting.await() } shouldBe listOf(3, 2, 1)
                    callback.awaitCallback() shouldBe true
                }
            }
        } finally {
            response.complete(Unit)
        }
    }

    /**
     * Snapshot observers must be able to dispatch a reader without a publication lock inversion.
     */
    @Test
    fun `release its monitor before notifying snapshot observers`(): Unit = runBlocking {
        val scope = CoroutineScope(coroutineContext + Default)
        val response = CompletableDeferred<Unit>()
        val source = ConfigurablePagedDataSource(pageSize = 3)
        source.items = (1..3).toList()
        source.pending = response
        try {
            navigator(source, scope).use { pages ->
                awaitCondition { source.pending == null }
                NavigatorCallbackProbe {
                    pages.status shouldBe Active
                    pages.items shouldBe listOf(3, 2, 1)
                }.use { callback ->
                    val observer = Snapshot.registerApplyObserver { _, _ ->
                        if (pages.status == Active) callback.inspect()
                    }
                    try {
                        response.complete(Unit)

                        callback.awaitCallback() shouldBe true
                        withTimeout(5_000) { pages.awaitItems() } shouldBe listOf(3, 2, 1)
                    } finally {
                        observer.dispose()
                    }
                }
            }
        } finally {
            response.complete(Unit)
        }
    }

    /**
     * Non-UI reading and live changes need no Compose frames or snapshot notification pump.
     */
    @Test
    fun `load and observe pages without a composition`(): Unit = runBlocking {
        withTimeout(5_000) {
            ObservationChannel().use { source ->
                val sample = items(4)
                    .reversed()
                source.items = sample
                val readStarted = CompletableDeferred<Unit>()
                val releaseRead = CountDownLatch(1)
                source.onRead = {
                    readStarted.complete(Unit)
                    check(releaseRead.await(5, SECONDS))
                }
                try {
                    PagedDataNavigator(query = query(), client = source.client, scope = this)
                        .use { pages ->
                            yield()
                            readStarted.await()
                            source.onRead = {}
                            releaseRead.countDown()
                            pages.awaitItems() shouldBe sample.take(3)
                            val updated = items(5)
                                .reversed()
                            source.items = updated.take(4)
                            source.update(updated.first())
                            awaitCondition { pages.items == updated.take(3) }
                            source.subscribeCount shouldBe 1

                            source.items = updated.drop(3)
                            pages.next()
                            pages.awaitItems() shouldBe updated.drop(3)
                            awaitCondition { source.cancelCount == 1 }
                            source.items = updated.take(4)
                            pages.first()
                            pages.awaitItems() shouldBe updated.take(3)
                            source.subscribeCount shouldBe 2
                        }
                    awaitCondition { source.cancelCount == 2 }
                } finally {
                    releaseRead.countDown()
                }
            }
        }
    }

    /**
     * Equivalent input retains navigation, while query and client changes reset it.
     */
    @Test
    fun `remember inputs and recreate after leaving composition`(): Unit = runBlocking {
        ObservationChannel().use { source ->
            ObservationChannel().use { replacement ->
                source.items = items(4)
                    .reversed()
                replacement.items = source.items
                PagedDataNavigatorScene(source.client).use { scene ->
                    awaitCondition { scene.navigator.status == Active }
                    val original = scene.navigator
                    source.items = items(1)
                    scene.act { next() }
                    awaitCondition { scene.navigator.status == Active }
                    scene.recompose()
                    scene.navigator shouldBeSameInstanceAs original
                    scene.navigator.isFirstPage shouldBe false
                    source.readCount shouldBe 2

                    scene.lowerBound = "0000"
                    awaitCondition { scene.navigator.status == Active }
                    val selected = scene.navigator
                    selected shouldNotBeSameInstanceAs original
                    selected.isFirstPage shouldBe true
                    scene.client = replacement.client
                    awaitCondition { scene.navigator.status == Active }
                    val connected = scene.navigator
                    connected shouldNotBeSameInstanceAs selected
                    scene.visible = false
                    scene.render()
                    connected.status shouldBe Cancelled
                    awaitCondition { replacement.cancelCount == 1 }

                    scene.visible = true
                    awaitCondition { scene.navigator.status == Active }
                    scene.navigator shouldNotBeSameInstanceAs connected
                    scene.navigator.isFirstPage shouldBe true
                    replacement.subscribeCount shouldBe 2
                }
                awaitCondition { replacement.cancelCount == 2 }
            }
        }
    }

    /**
     * Client shutdown during composition and a new selection produce cancellation, not a crash.
     */
    @Test
    fun `tolerate a closed client in composition`(): Unit = runBlocking {
        ObservationChannel().use { source ->
            PagedDataNavigatorScene(source.client).use { scene ->
                awaitCondition { scene.navigator.status == Active }
                source.client.close()
                scene.lowerBound = "0000"

                scene.navigator.status shouldBe Cancelled
                scene.navigator.canGoNext shouldBe false
                source.readCount shouldBe 1
            }
        }
    }

    /**
     * An abandoned composition discards its snapshot before disposing the remembered navigator.
     */
    @Test
    fun `close an idle navigator after its creation snapshot is abandoned`(): Unit = runBlocking {
        ObservationChannel().use { source ->
            val scope = CoroutineScope(Job())
            val snapshot = Snapshot.takeMutableSnapshot()
            val pages = try {
                snapshot.enter {
                    createPagedDataNavigator(query = query(), client = source.client, scope = scope)
                }
            } finally {
                snapshot.dispose()
            }
            try {
                pages.close()

                pages.status shouldBe Cancelled
                pages.canGoNext shouldBe false
                shouldThrow<CancellationException> { pages.awaitItems() }
                source.readCount shouldBe 0
                source.subscribeCount shouldBe 0
            } finally {
                scope.cancel()
            }
        }
    }

    /**
     * An already cancelled caller cannot trigger an initial server request.
     */
    @Test
    fun `return a cancelled navigator for a cancelled caller`(): Unit = runBlocking {
        ObservationChannel().use { source ->
            val scope = CoroutineScope(Job())
            scope.cancel()
            PagedDataNavigator(query = query(), client = source.client, scope = scope)
                .use { pages ->
                    pages.status shouldBe Cancelled
                    shouldThrow<CancellationException> { pages.awaitItems() }
                    source.readCount shouldBe 0
                }
        }
    }

    /**
     * Client shutdown cancels a pending initial observation before its deferred read runs.
     */
    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `cancel an initial waiter when its client closes`() = runTest {
        ObservationChannel(StandardTestDispatcher(testScheduler)).use { source ->
            PagedDataNavigator(query = query(), client = source.client, scope = backgroundScope)
                .use { pages ->
                    val waiting = async(start = UNDISPATCHED) { pages.awaitItems() }
                    source.client.close()
                    runCurrent()

                    shouldThrow<CancellationException> { waiting.await() }
                    pages.status shouldBe Cancelled
                    source.readCount shouldBe 0
                    source.subscribeCount shouldBe 0
                }
        }
    }

    /**
     * A client's closure interrupts the fixed-page read and releases its waiter.
     */
    @Test
    fun `cancel a pending fixed page when its client closes`(): Unit = runBlocking {
        withTimeout(5_000) {
            ObservationChannel().use { source ->
                source.items = items(4)
                    .reversed()
                PagedDataNavigator(query = query(), client = source.client, scope = this)
                    .use { pages ->
                        pages.awaitItems()
                        val readStarted = CompletableDeferred<Unit>()
                        val readStopped = CompletableDeferred<Unit>()
                        val releaseRead = CountDownLatch(1)
                        source.onRead = {
                            readStarted.complete(Unit)
                            try {
                                check(releaseRead.await(5, SECONDS))
                            } finally {
                                readStopped.complete(Unit)
                            }
                        }
                        try {
                            pages.next()
                            val waiting = async { pages.awaitItems() }
                            readStarted.await()
                            source.client.close()

                            shouldThrow<CancellationException> { waiting.await() }
                            readStopped.await()
                            pages.status shouldBe Cancelled
                            source.readCount shouldBe 2
                        } finally {
                            releaseRead.countDown()
                        }
                    }
            }
        }
    }

    /**
     * A decorator may not relay `CLOSED`, so observation cancellation must also end navigation.
     */
    @Test
    fun `end a live observation without a closed connection status`(): Unit = runBlocking {
        withTimeout(5_000) {
            ObservationChannel().use { source ->
                val client = object : Client by source.client {
                    /**
                     * Reproduces a decorator that retains its last connection status.
                     */
                    override val connectionStatus = MutableStateFlow(CONNECTED)
                }
                PagedDataNavigator(query = query(), client = client, scope = this)
                    .use { pages ->
                        pages.awaitItems()
                        source.client.close()

                        awaitCondition { pages.status == Cancelled }
                        shouldThrow<CancellationException> { pages.awaitItems() }
                        client.connectionStatus.value shouldBe CONNECTED
                    }
            }
        }
    }

    /**
     * The real query forwards recovery status both before and after its first successful read.
     */
    @ParameterizedTest(name = "lose connection before first data={0}")
    @ValueSource(booleans = [true, false])
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `forward first-page recovery status from the observation`(initially: Boolean) = runTest {
        ObservationChannel(StandardTestDispatcher(testScheduler)).use { source ->
            source.items = items(4)
            if (initially) source.onRead = { throw Status.UNAVAILABLE.asRuntimeException() }
            PagedDataNavigator(query = query(), client = source.client, scope = backgroundScope)
                .use { pages ->
                    runCurrent()
                    if (!initially) {
                        pages.status shouldBe Active
                        source.onRead = { throw Status.UNAVAILABLE.asRuntimeException() }
                        source.update(
                            items(4)
                                .last()
                        )
                        runCurrent()
                    }

                    pages.status shouldBe WaitingForConnection
                    pages.canGoNext shouldBe false
                    if (!initially) pages.items shouldBe source.items.take(3)
                    source.onRead = {}
                    advanceTimeBy(1_000)
                    runCurrent()

                    pages.status shouldBe Active
                    pages.items shouldBe source.items.take(3)
                    pages.canGoNext shouldBe true
                }
        }
    }

    /**
     * Idle and connecting clients can perform an initial read without an external status change.
     */
    @ParameterizedTest(name = "read next page while {0}")
    @EnumSource(value = ConnectionStatus::class, names = ["IDLE", "CONNECTING"])
    fun `read fixed pages while idle or connecting`(status: ConnectionStatus): Unit = runBlocking {
        val connection = MutableStateFlow(status)
        val source = ConfigurablePagedDataSource(pageSize = 3)
        source.items = (1..5).toList()
        val scope = CoroutineScope(coroutineContext + Unconfined)
        navigator(source = source, scope = scope, connection = connection).use { pages ->
            val response = CompletableDeferred<Unit>()
            source.pending = response
            try {
                pages.next()

                pages.status shouldBe Refreshing
                source.cursors.size shouldBe 2
                response.complete(Unit)
                withTimeout(5_000) { pages.awaitItems() shouldBe listOf(2, 1) }
                connection.value shouldBe status
            } finally {
                response.complete(Unit)
            }
        }
    }

    /**
     * A known unavailable connection parks the initial fixed-page read until reconnection.
     */
    @Test
    fun `avoid a fixed-page read while the connection is unavailable`(): Unit = runBlocking {
        val connection = MutableStateFlow(CONNECTED)
        val source = ConfigurablePagedDataSource(pageSize = 3)
        source.items = (1..5).toList()
        val scope = CoroutineScope(coroutineContext + Unconfined)
        navigator(source = source, scope = scope, connection = connection).use { pages ->
            connection.value = UNAVAILABLE
            pages.next()

            pages.status shouldBe WaitingForConnection
            source.cursors.size shouldBe 1
            connection.value = CONNECTED
            withTimeout(5_000) { pages.awaitItems() shouldBe listOf(2, 1) }
            source.cursors.size shouldBe 2
        }
    }

    /**
     * Reaching the first page keeps its loaded data active while its live observation starts.
     */
    @Test
    fun `keep the first page active while resuming observation`(): Unit = runBlocking {
        val source = ConfigurablePagedDataSource(pageSize = 3)
        source.items = (1..5).toList()
        navigator(source, CoroutineScope(coroutineContext + Unconfined)).use { pages ->
            pages.next()
            val ready = CompletableDeferred<Unit>()
            source.observationReady = ready
            try {
                pages.previous()

                pages.status shouldBe Active
                pages.items shouldBe listOf(5, 4, 3)
                pages.isFirstPage shouldBe true
                pages.canGoNext shouldBe true
                ready.isCompleted shouldBe false
                source.status = WaitingForConnection
                ready.complete(Unit)

                pages.status shouldBe WaitingForConnection
                pages.canGoNext shouldBe false
                source.status = Active
                source.updates.tryEmit(Unit)
                pages.status shouldBe Active
            } finally {
                ready.complete(Unit)
            }
        }
    }

    /**
     * A navigator created during composition must not monitor the connection before the
     * composition commits.
     */
    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `defer connection monitoring until the first request`() = runTest {
        ObservationChannel(StandardTestDispatcher(testScheduler)).use { source ->
            val connection = MutableStateFlow(CONNECTED)
            val client = object : Client by source.client {
                /**
                 * Exposes the number of active connection collectors for lifecycle assertions.
                 */
                override val connectionStatus = connection
            }
            createPagedDataNavigator(query = query(), client = client, scope = backgroundScope)
                .use { pages ->
                    connection.subscriptionCount.value shouldBe 0
                    runCurrent()
                    source.readCount shouldBe 0
                    source.subscribeCount shouldBe 0

                    pages.first()
                    runCurrent()

                    connection.subscriptionCount.value shouldBe 1
                    source.readCount shouldBe 1
                    pages.retry()
                    runCurrent()
                    connection.subscriptionCount.value shouldBe 1
                }
            runCurrent()
            connection.subscriptionCount.value shouldBe 0
        }
    }
}
