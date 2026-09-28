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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.chords.client.given.DesktopClientSpecEnv.awaitActive
import io.spine.chords.client.given.DesktopClientSpecEnv.item
import io.spine.chords.client.given.IdlessItem
import io.spine.chords.client.given.NamedItem
import io.spine.chords.client.given.ObservedItem
import io.spine.chords.client.testing.ObservationChannel
import io.spine.client.CompositeEntityStateFilter
import io.spine.client.CompositeQueryFilter
import io.spine.client.EntityStateFilter
import io.spine.client.Filters.gt
import io.spine.client.Filters.lt
import io.spine.client.OrderBy.Direction
import io.spine.client.OrderBy.Direction.ASCENDING
import io.spine.client.OrderBy.Direction.DESCENDING
import io.spine.client.QueryFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Verifies bounded queries and notifications through the real Spine client adapter.
 */
@DisplayName("`DesktopClient` should")
@OptIn(ExperimentalCoroutinesApi::class)
internal class DesktopClientSpec {

    /**
     * Removing one entity preserves its neighbours, and its next matching state restores it.
     */
    @Test
    fun `remove and restore list entities`(): Unit =
        ObservationChannel()
            .use { source ->
                val first = item("first")
                val second = item("second")
                source.items = listOf(first, second)
                val observation = source.client.readAndObserve(
                    ObservedItem::class.java,
                    ObservedItem::getId
                )
                observation.awaitActive()

                source.remove(first.id)
                observation.value shouldBe listOf(second)
                source.remove(item("unseen").id)
                observation.value shouldBe listOf(second)

                val restored = item("first", "restored")
                source.update(restored)
                observation.value shouldBe listOf(second, restored)
                source.remove(first.id)
                observation.value shouldBe listOf(second)
                observation.status.value shouldBe DataObservationStatus.Active
            }

    /**
     * Filtered subscriptions receive the same removal notifications as unfiltered ones.
     */
    @Test
    fun `remove entities from a filtered list`(): Unit =
        ObservationChannel()
            .use { source ->
                val entity = item("first")
                source.items = listOf(entity)
                val observation = source.client.readAndObserve(
                    entityClass = ObservedItem::class.java,
                    extractId = ObservedItem::getId,
                    queryFilter = CompositeQueryFilter.all(
                        QueryFilter.eq(ObservedItem.Column.label(), "first")
                    ),
                    observeFilter = CompositeEntityStateFilter.all(
                        EntityStateFilter.eq(ObservedItem.Field.label(), "first")
                    )
                )
                observation.awaitActive()

                source.remove(entity.id)
                observation.value shouldBe emptyList()
                source.update(entity)
                observation.value shouldBe listOf(entity)
            }

    /**
     * Nullable observations reread the query after removals and recover on matching updates.
     */
    @Test
    fun `clear and restore a single entity`(): Unit = runTest {
        ObservationChannel(StandardTestDispatcher(testScheduler))
            .use { source ->
                val entity = item("first")
                source.items = listOf(entity)
                val observation = source.client.readOneAndObserve(
                    entityClass = ObservedItem::class.java,
                    queryFilter = CompositeQueryFilter.all(
                        QueryFilter.eq(ObservedItem.Column.label(), "first")
                    ),
                    observeFilter = CompositeEntityStateFilter.all(
                        EntityStateFilter.eq(ObservedItem.Field.label(), "first")
                    )
                )
                runCurrent()

                source.remove(item("unseen").id)
                runCurrent()
                source.readCount shouldBe 2
                observation.value shouldBe entity
                source.items = emptyList()
                source.remove(entity.id)
                runCurrent()
                source.readCount shouldBe 3
                observation.value shouldBe null
                source.update(entity)
                observation.value shouldBe entity
                source.remove(entity.id)
                runCurrent()
                source.readCount shouldBe 4
                observation.value shouldBe null
            }
    }

    /**
     * The non-null overload restores the caller's fallback after a removal.
     */
    @Test
    fun `restore the default after a removal`(): Unit = runTest {
        ObservationChannel(StandardTestDispatcher(testScheduler))
            .use { source ->
                val entity = item("first")
                val fallback = item("fallback")
                source.items = listOf(entity)
                val observation = source.client.readOneAndObserve(
                    entityClass = ObservedItem::class.java,
                    queryFilter = CompositeQueryFilter.all(
                        QueryFilter.eq(ObservedItem.Column.label(), "first")
                    ),
                    observeFilter = CompositeEntityStateFilter.all(
                        EntityStateFilter.eq(ObservedItem.Field.label(), "first")
                    ),
                    defaultValue = fallback
                )
                runCurrent()

                source.remove(item("unseen").id)
                runCurrent()
                source.readCount shouldBe 2
                observation.value shouldBe entity
                source.items = emptyList()
                source.remove(entity.id)
                runCurrent()
                source.readCount shouldBe 3
                observation.value shouldBe fallback
                source.update(entity)
                observation.value shouldBe entity
                source.remove(entity.id)
                runCurrent()
                source.readCount shouldBe 4
                observation.value shouldBe fallback
            }
    }

    /**
     * Single observations cannot assume that the first state field stores the entity ID.
     */
    @Test
    fun `remove a single entity whose ID is outside its state`(): Unit = runTest {
        ObservationChannel(StandardTestDispatcher(testScheduler))
            .use { source ->
                val entity = IdlessItem.newBuilder()
                    .setLabel("visible")
                    .build()
                source.items = listOf(entity)
                val observation = source.client.readOneAndObserve(
                    entityClass = IdlessItem::class.java,
                    queryFilter = CompositeQueryFilter.all(
                        QueryFilter.eq(IdlessItem.Column.label(), "visible")
                    ),
                    observeFilter = CompositeEntityStateFilter.all(
                        EntityStateFilter.eq(IdlessItem.Field.label(), "visible")
                    )
                )
                runCurrent()
                observation.value shouldBe entity

                source.items = emptyList()
                source.remove(item("actual-id").id)
                runCurrent()
                source.readCount shouldBe 2

                observation.value shouldBe null
            }
    }

    /**
     * A single observation may still have another matching entity after a removal.
     */
    @Test
    fun `reread a defaulted entity whose ID is outside its state`(): Unit = runTest {
        ObservationChannel(StandardTestDispatcher(testScheduler))
            .use { source ->
                val first = IdlessItem.newBuilder()
                    .setLabel("first")
                    .build()
                val second = IdlessItem.newBuilder()
                    .setLabel("second")
                    .build()
                val fallback = IdlessItem.getDefaultInstance()
                source.items = listOf(first, second)
                val observation = source.client.readOneAndObserve(
                    entityClass = IdlessItem::class.java,
                    queryFilter = CompositeQueryFilter.either(
                        QueryFilter.eq(IdlessItem.Column.label(), "first"),
                        QueryFilter.eq(IdlessItem.Column.label(), "second")
                    ),
                    observeFilter = CompositeEntityStateFilter.either(
                        EntityStateFilter.eq(IdlessItem.Field.label(), "first"),
                        EntityStateFilter.eq(IdlessItem.Field.label(), "second")
                    ),
                    defaultValue = fallback
                )
                runCurrent()
                observation.value shouldBe first

                source.items = listOf(second)
                source.remove(item("first-id").id)
                runCurrent()
                source.readCount shouldBe 2
                observation.value shouldBe second

                source.items = emptyList()
                source.remove(item("second-id").id)
                runCurrent()
                source.readCount shouldBe 3
                observation.value shouldBe fallback
            }
    }

    /**
     * A removal during each read must trigger another read after that snapshot completes.
     */
    @Test
    fun `reread removals received during initial and subsequent single reads`(): Unit = runTest {
        ObservationChannel(StandardTestDispatcher(testScheduler))
            .use { source ->
                source.items = listOf(item("initial"))
                source.onRead = {
                    when (source.readCount) {
                        1 -> {
                            source.items = listOf(item("replacement"))
                            source.remove(item("initial").id)
                        }
                        2 -> {
                            source.items = emptyList()
                            source.remove(item("replacement").id)
                        }
                    }
                }
                val observation = source.client.readOneAndObserve(
                    entityClass = ObservedItem::class.java,
                    queryFilter = CompositeQueryFilter.either(
                        QueryFilter.eq(ObservedItem.Column.label(), "initial"),
                        QueryFilter.eq(ObservedItem.Column.label(), "replacement")
                    ),
                    observeFilter = CompositeEntityStateFilter.either(
                        EntityStateFilter.eq(ObservedItem.Field.label(), "initial"),
                        EntityStateFilter.eq(ObservedItem.Field.label(), "replacement")
                    )
                )

                runCurrent()
                source.readCount shouldBe 3

                observation.value shouldBe null
                source.readCount shouldBe 3
            }
    }

    /**
     * Scalar IDs must be unwrapped before comparison with the caller's ID extractor.
     */
    @Test
    fun `remove entities with scalar IDs`(): Unit =
        ObservationChannel()
            .use { source ->
                val entity = NamedItem.newBuilder()
                    .setName("first")
                    .build()
                source.items = listOf(entity)
                val observation = source.client.readAndObserve(
                    NamedItem::class.java,
                    NamedItem::getName
                )
                observation.awaitActive()

                source.remove(entity.name)

                observation.value shouldBe emptyList()
                observation.status.value shouldBe DataObservationStatus.Active
            }

    /**
     * A removal arriving during a read must win over that read's older result.
     */
    @Test
    fun `apply removals buffered during initial and later reads`(): Unit =
        ObservationChannel()
            .use { source ->
                val entity = item("first")
                source.items = listOf(entity)
                source.onRead = { source.remove(entity.id) }
                val observation = source.client.readAndObserve(
                    ObservedItem::class.java,
                    ObservedItem::getId
                )
                observation.awaitActive()
                observation.value shouldBe emptyList()

                source.update(entity)
                observation.value shouldBe listOf(entity)
                runBlocking { observation.refresh() }

                observation.value shouldBe emptyList()
                observation.status.value shouldBe DataObservationStatus.Active
            }

    /**
     * Cancellation ignores a removal delivered through the previously active stream.
     */
    @Test
    fun `ignore removals after cancellation`(): Unit =
        ObservationChannel()
            .use { source ->
                val entity = item("first")
                source.items = listOf(entity)
                val observation = source.client.readAndObserve(
                    ObservedItem::class.java,
                    ObservedItem::getId
                )
                observation.awaitActive()
                source.remove(entity.id)
                observation.value shouldBe emptyList()
                source.update(entity)
                val lateRemoval = source.captureRemoval(entity.id)
                observation.cancel()

                lateRemoval() shouldBe 1

                observation.value shouldBe listOf(entity)
                observation.status.value shouldBe DataObservationStatus.Cancelled
            }

    /**
     * Sends cursor boundaries, order, and limits to the server for bounded selection.
     */
    @Test
    fun `send a bounded ordered query with its cursor filter`() {
        ObservationChannel()
            .use { source ->
                val filter = CompositeQueryFilter.all(
                    QueryFilter.gt(ObservedItem.Column.label(), "B")
                )
                val expected = listOf(item("third", "C"), item("fourth", "D"))
                source.items = expected

                val actual = source.client.readPage(
                    entityClass = ObservedItem::class.java,
                    queryFilter = filter,
                    orderBy = ObservedItem.Column.label(),
                    direction = ASCENDING,
                    limit = 2
                )

                actual shouldBe expected
                val query = checkNotNull(source.lastQuery)
                query.format.limit shouldBe 2
                query.format.orderBy.direction shouldBe ASCENDING
                query.format.orderBy.column shouldBe "label"
                query.target.filters.filterList.flatMap { it.filterList } shouldBe
                    listOf(gt(ObservedItem.Column.label(), "B"))
                source.lastTopic shouldBe null
            }
    }

    /**
     * New records and removals replace the server-selected page without growing the client list.
     * Its subscription covers more than the cursor query so changes can invalidate the page.
     */
    @Test
    fun `reread a limited page on updates and removals`() = runTest {
        ObservationChannel(StandardTestDispatcher(testScheduler))
            .use { source ->
                val first = item("first", "A")
                val second = item("second", "B")
                val third = item("third", "C")
                source.items = listOf(second, first)
                val observation = source.client.readPageAndObserve(
                    entityClass = ObservedItem::class.java,
                    queryFilter = CompositeQueryFilter.all(
                        QueryFilter.gt(ObservedItem.Column.label(), ""),
                        QueryFilter.lt(ObservedItem.Column.label(), "D")
                    ),
                    observeFilter = CompositeEntityStateFilter.all(
                        EntityStateFilter.gt(ObservedItem.Field.label(), "")
                    ),
                    orderBy = ObservedItem.Column.label(),
                    direction = DESCENDING,
                    limit = 2
                )
                runCurrent()
                observation.status.value shouldBe DataObservationStatus.Active
                source.onRead = {
                    observation.status.value shouldBe DataObservationStatus.Active
                }

                source.items = listOf(third, second)
                source.update(third)
                runCurrent()
                source.readCount shouldBe 2
                observation.value shouldBe listOf(third, second)

                source.items = listOf(third, first)
                source.remove(second.id)
                runCurrent()
                source.readCount shouldBe 3
                observation.value shouldBe listOf(third, first)
                val query = checkNotNull(source.lastQuery)
                query.format.limit shouldBe 2
                query.format.orderBy.direction shouldBe DESCENDING
                query.target.filters.filterList.flatMap { it.filterList } shouldBe
                    listOf(
                        gt(ObservedItem.Column.label(), ""),
                        lt(ObservedItem.Column.label(), "D")
                    )
                val topic = checkNotNull(source.lastTopic)
                topic.target.filters.filterList.flatMap { it.filterList } shouldBe
                    listOf(gt(ObservedItem.Field.label(), ""))
                source.subscribeCount shouldBe 1
                source.activationCount shouldBe 1
                source.cancelCount shouldBe 0

                val staleRemoval = source.captureRemoval(first.id)
                observation.cancel()
                val reads = source.readCount
                staleRemoval() shouldBe 1
                runCurrent()
                source.readCount shouldBe reads
                observation.status.value shouldBe DataObservationStatus.Cancelled
            }
    }

    /**
     * Invalid sort values are caller errors and must fail before an observation starts.
     */
    @ParameterizedTest
    @EnumSource(Direction::class, names = ["OD_UNKNOWN", "UNRECOGNIZED"])
    fun `reject invalid ordering before making a request`(direction: Direction) {
        ObservationChannel()
            .use { source ->
                val queryFilter = CompositeQueryFilter.all(
                    QueryFilter.gt(ObservedItem.Column.label(), "")
                )
                val observeFilter = CompositeEntityStateFilter.all(
                    EntityStateFilter.gt(ObservedItem.Field.label(), "")
                )
                shouldThrow<IllegalArgumentException> {
                    source.client.readPage(
                        entityClass = ObservedItem::class.java,
                        queryFilter = queryFilter,
                        orderBy = ObservedItem.Column.label(),
                        direction = direction,
                        limit = 2
                    )
                }
                shouldThrow<IllegalArgumentException> {
                    source.client.readPageAndObserve(
                        entityClass = ObservedItem::class.java,
                        queryFilter = queryFilter,
                        observeFilter = observeFilter,
                        orderBy = ObservedItem.Column.label(),
                        direction = direction,
                        limit = 2
                    )
                }
                source.subscribeCount shouldBe 0
                source.activationCount shouldBe 0
                source.readCount shouldBe 0
            }
    }

    /**
     * A zero limit must not accidentally request an unlimited result set.
     */
    @ParameterizedTest
    @ValueSource(ints = [0, -1])
    fun `reject an unbounded page limit before making a request`(limit: Int) {
        ObservationChannel()
            .use { source ->
                shouldThrow<IllegalArgumentException> {
                    source.client.readPage(
                        entityClass = ObservedItem::class.java,
                        queryFilter = CompositeQueryFilter.all(
                            QueryFilter.gt(ObservedItem.Column.label(), "")
                        ),
                        orderBy = ObservedItem.Column.label(),
                        direction = ASCENDING,
                        limit = limit
                    )
                }
                shouldThrow<IllegalArgumentException> {
                    source.client.readPageAndObserve(
                        entityClass = ObservedItem::class.java,
                        queryFilter = CompositeQueryFilter.all(
                            QueryFilter.gt(ObservedItem.Column.label(), "")
                        ),
                        observeFilter = CompositeEntityStateFilter.all(
                            EntityStateFilter.gt(ObservedItem.Field.label(), "")
                        ),
                        orderBy = ObservedItem.Column.label(),
                        direction = ASCENDING,
                        limit = limit
                    )
                }
                source.lastTopic shouldBe null
                source.readCount shouldBe 0
            }
    }
}
