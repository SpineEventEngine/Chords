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
import io.spine.chords.client.given.DesktopClientSpecEnv.awaitActive
import io.spine.chords.client.given.DesktopClientSpecEnv.awaitReads
import io.spine.chords.client.given.DesktopClientSpecEnv.item
import io.spine.chords.client.given.IdlessItem
import io.spine.chords.client.given.NamedItem
import io.spine.chords.client.given.ObservationChannel
import io.spine.chords.client.given.ObservedItem
import io.spine.client.CompositeEntityStateFilter
import io.spine.client.CompositeQueryFilter
import io.spine.client.EntityStateFilter
import io.spine.client.QueryFilter
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies removal notifications through the real Spine subscription adapter.
 */
@DisplayName("`DesktopClient` should")
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
    fun `clear and restore a single entity`(): Unit =
        ObservationChannel()
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
                observation.awaitActive()

                source.remove(item("unseen").id)
                source.awaitReads(2)
                observation.awaitActive()
                observation.value shouldBe entity
                source.items = emptyList()
                source.remove(entity.id)
                source.awaitReads(3)
                observation.awaitActive()
                observation.value shouldBe null
                source.update(entity)
                observation.value shouldBe entity
                source.remove(entity.id)
                source.awaitReads(4)
                observation.awaitActive()
                observation.value shouldBe null
            }

    /**
     * The non-null overload restores the caller's fallback after a removal.
     */
    @Test
    fun `restore the default after a removal`(): Unit =
        ObservationChannel()
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
                observation.awaitActive()

                source.remove(item("unseen").id)
                source.awaitReads(2)
                observation.awaitActive()
                observation.value shouldBe entity
                source.items = emptyList()
                source.remove(entity.id)
                source.awaitReads(3)
                observation.awaitActive()
                observation.value shouldBe fallback
                source.update(entity)
                observation.value shouldBe entity
                source.remove(entity.id)
                source.awaitReads(4)
                observation.awaitActive()
                observation.value shouldBe fallback
            }

    /**
     * Single observations cannot assume that the first state field stores the entity ID.
     */
    @Test
    fun `remove a single entity whose ID is outside its state`(): Unit =
        ObservationChannel()
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
                observation.awaitActive()
                observation.value shouldBe entity

                source.items = emptyList()
                source.remove(item("actual-id").id)
                source.awaitReads(2)
                observation.awaitActive()

                observation.value shouldBe null
            }

    /**
     * A single observation may still have another matching entity after a removal.
     */
    @Test
    fun `reread a defaulted entity whose ID is outside its state`(): Unit =
        ObservationChannel()
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
                observation.awaitActive()
                observation.value shouldBe first

                source.items = listOf(second)
                source.remove(item("first-id").id)
                source.awaitReads(2)
                observation.awaitActive()
                observation.value shouldBe second

                source.items = emptyList()
                source.remove(item("second-id").id)
                source.awaitReads(3)
                observation.awaitActive()
                observation.value shouldBe fallback
            }

    /**
     * A removal during each read must trigger another read after that snapshot completes.
     */
    @Test
    fun `reread removals received during initial and subsequent single reads`(): Unit =
        ObservationChannel()
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

                source.awaitReads(3)
                observation.awaitActive()

                observation.value shouldBe null
                source.readCount shouldBe 3
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
}
