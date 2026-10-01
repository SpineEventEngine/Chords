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

import com.google.common.testing.EqualsTester
import io.grpc.Status
import io.grpc.Status.Code.PERMISSION_DENIED
import io.grpc.StatusRuntimeException
import io.kotest.matchers.shouldBe
import io.spine.chords.client.DataObservationStatus.Active
import io.spine.chords.client.DataObservationStatus.Failed
import io.spine.chords.client.DataPageCursor.After
import io.spine.chords.client.DataPageCursor.At
import io.spine.chords.client.DataPageCursor.Before
import io.spine.chords.client.DataPageCursor.End
import io.spine.chords.client.DataPageCursor.Start
import io.spine.chords.client.given.ObservedItem
import io.spine.chords.client.given.ObservedItemPages.items
import io.spine.chords.client.given.ObservedItemPages.query
import io.spine.chords.client.testing.ObservationChannel
import io.spine.chords.client.testing.awaitCondition
import io.spine.client.CompositeFilter.CompositeOperator.EITHER
import io.spine.client.CompositeQueryFilter
import io.spine.client.Filters.ge
import io.spine.client.Filters.gt
import io.spine.client.Filters.le
import io.spine.client.Filters.lt
import io.spine.client.OrderBy.Direction.ASCENDING
import io.spine.client.OrderBy.Direction.DESCENDING
import io.spine.client.Query
import io.spine.client.QueryFilter
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Verifies bounded server queries and independently collected first-page observations.
 */
@DisplayName("`PagedDataQuery` should")
internal class PagedDataQuerySpec {

    /**
     * Direct final-page reads preserve filters and use one bounded query in reverse order.
     */
    @ParameterizedTest(name = "last page with ascending={0}, items={1}")
    @MethodSource("lastPageCases")
    fun `read the final page directly`(ascending: Boolean, count: Int): Unit = runBlocking {
        ObservationChannel().use { source ->
            val direction = if (ascending) ASCENDING else DESCENDING
            val sample = items(count)
            val ordered = if (ascending) sample else sample.reversed()
            source.items = ordered.reversed()
                .take(3)

            val page = query(direction = direction, pageSize = 2)
                .read(source.client, End)

            page.items shouldBe ordered.takeLast(2)
            page.hasPrevious shouldBe (count > 2)
            page.hasNext shouldBe false
            val request = checkNotNull(source.lastQuery)
            request.format.limit shouldBe 3
            request.format.orderBy.direction shouldBe if (ascending) DESCENDING else ASCENDING
            request.target.filters.filterList.flatMap { it.filterList } shouldBe
                listOf(gt(ObservedItem.Column.label(), ""))
            source.readCount shouldBe 1
            source.subscribeCount shouldBe 0
        }
    }

    /**
     * Seeking uses an inclusive cursor and honors both results of its predecessor check.
     */
    @ParameterizedTest(name = "seek with ascending={0}, preceding items={1}")
    @MethodSource("seekCases")
    fun `include a requested cursor`(
        ascending: Boolean,
        precedingItemsExist: Boolean
    ): Unit = runBlocking {
        ObservationChannel().use { source ->
            val direction = if (ascending) ASCENDING else DESCENDING
            val sample = items(7)
            val selected = if (ascending) sample.drop(3) else sample.take(4).reversed()
            val requests = mutableListOf<Query>()
            source.items = selected.take(3)
            source.onRead = {
                requests.add(checkNotNull(source.lastQuery))
                source.items = if (precedingItemsExist) {
                    listOf(if (ascending) sample.first() else sample.last())
                } else emptyList()
            }

            val page = query(direction = direction, pageSize = 2)
                .read(source.client, At(sample[3].label))

            page.items shouldBe selected.take(2)
            page.hasNext shouldBe true
            page.hasPrevious shouldBe precedingItemsExist
            requests.size shouldBe 2
            val first = requests.first()
            val preceding = requests.last()
            first.format.limit shouldBe 3
            first.format.orderBy.direction shouldBe direction
            first.target.filters.filterList.last().filterList shouldBe listOf(
                if (ascending) ge(ObservedItem.Column.label(), sample[3].label)
                else le(ObservedItem.Column.label(), sample[3].label)
            )
            preceding.format.limit shouldBe 1
            preceding.format.orderBy.direction shouldBe
                if (ascending) DESCENDING else ASCENDING
            preceding.target.filters.filterList.last().filterList shouldBe listOf(
                if (ascending) lt(ObservedItem.Column.label(), sample[3].label)
                else gt(ObservedItem.Column.label(), sample[3].label)
            )
            source.subscribeCount shouldBe 0
        }
    }

    /**
     * Page direction reverses both comparison and read order while preserving selection.
     */
    @ParameterizedTest(name = "read adjacent pages with ascending={0}")
    @ValueSource(booleans = [true, false])
    fun `read adjacent pages in either display order`(ascending: Boolean): Unit = runBlocking {
        withTimeout(5_000) {
            ObservationChannel().use { source ->
                val direction = if (ascending) ASCENDING else DESCENDING
                val query = query(direction = direction, pageSize = 2)
                val sample = items(7)
                val ordered = if (ascending) sample else sample.reversed()
                val cursor = sample[3].label
                source.items = ordered.take(3)

                val first = query.read(source.client, Start)
                val initial = checkNotNull(source.lastQuery)

                first.items shouldBe ordered.take(2)
                first.hasPrevious shouldBe false
                first.hasNext shouldBe true
                initial.format.limit shouldBe 3
                initial.format.orderBy.direction shouldBe direction
                initial.format.orderBy.column shouldBe "label"
                initial.target.filters.filterList.flatMap { it.filterList } shouldBe
                    listOf(gt(ObservedItem.Column.label(), ""))

                source.items = ordered.drop(4)
                    .take(3)
                val nextPage = query.read(source.client, After(cursor))
                val next = checkNotNull(source.lastQuery)
                source.items = ordered.take(3)
                    .reversed()
                val previous = query.read(source.client, Before(cursor))
                val backward = checkNotNull(source.lastQuery)

                nextPage.items shouldBe ordered.drop(4)
                    .take(2)
                nextPage.hasPrevious shouldBe true
                nextPage.hasNext shouldBe true
                next.format.limit shouldBe 3
                backward.format.limit shouldBe 3
                next.format.orderBy.direction shouldBe direction
                backward.format.orderBy.direction shouldBe
                    if (ascending) DESCENDING else ASCENDING
                next.target.filters.filterList.flatMap { it.filterList } shouldBe listOf(
                    gt(ObservedItem.Column.label(), ""),
                    if (ascending) gt(ObservedItem.Column.label(), cursor)
                    else lt(ObservedItem.Column.label(), cursor)
                )
                backward.target.filters.filterList.flatMap { it.filterList } shouldBe listOf(
                    gt(ObservedItem.Column.label(), ""),
                    if (ascending) lt(ObservedItem.Column.label(), cursor)
                    else gt(ObservedItem.Column.label(), cursor)
                )
                previous.items shouldBe ordered.take(3)
                    .takeLast(2)
                previous.hasPrevious shouldBe true
                previous.hasNext shouldBe true
                source.subscribeCount shouldBe 0
            }
        }
    }

    /**
     * First-page updates keep the displayed result bounded and cancel the collector's feed.
     */
    @Test
    fun `reread the first page on updates and cancel its observation`(): Unit = runBlocking {
        withTimeout(5_000) {
            ObservationChannel().use { source ->
                val sample = items(5)
                    .reversed()
                source.items = sample.drop(1)
                val pages = Channel<DataPage<ObservedItem>>(Channel.CONFLATED)
                val collection = launch {
                    query()
                        .observeFirstPage(source.client)
                        .collect { (status, page) -> if (status == Active) pages.send(page) }
                }
                pages.receive().items shouldBe sample.drop(1)
                    .take(3)
                checkNotNull(source.lastQuery).format.limit shouldBe 4
                val topic = checkNotNull(source.lastTopic)
                topic.target.filters.filterList.flatMap { it.filterList } shouldBe
                    listOf(gt(ObservedItem.Field.label(), ""))

                source.items = sample.take(4)
                source.update(sample.first())

                pages.receive().items shouldBe sample.take(3)
                source.readCount shouldBe 2
                source.subscribeCount shouldBe 1
                collection.cancelAndJoin()
                awaitCondition { source.cancelCount == 1 }
                source.client.isOpen shouldBe true
            }
        }
    }

    /**
     * Terminal observation errors remain available as status alongside the last successful page.
     */
    @Test
    fun `report a terminal first-page observation failure as status`(): Unit = runBlocking {
        withTimeout(5_000) {
            ObservationChannel().use { source ->
                source.items = items(4)
                    .reversed()
                val changes = Channel<Pair<DataObservationStatus, DataPage<ObservedItem>>>(
                    Channel.UNLIMITED
                )
                val collection = launch {
                    query()
                        .observeFirstPage(source.client)
                        .collect { changes.send(it) }
                }
                var change = changes.receive()
                while (change.first != Active) change = changes.receive()
                source.onRead = { throw Status.PERMISSION_DENIED.asRuntimeException() }

                source.update(
                    items(4)
                        .last()
                )

                change = changes.receive()
                while (change.first !is Failed) change = changes.receive()
                val failure = (change.first as Failed).error as StatusRuntimeException
                failure.status.code shouldBe PERMISSION_DENIED
                change.second.items shouldBe source.items.take(3)
                collection.cancelAndJoin()
                awaitCondition { source.cancelCount == 1 }
            }
        }
    }

    /**
     * Reusing one query does not share the observations created by its collectors.
     */
    @Test
    fun `keep observations independent for the same query`(): Unit = runBlocking {
        withTimeout(5_000) {
            ObservationChannel().use { source ->
                source.items = items(4)
                    .reversed()
                val query = query()
                val firstPages = Channel<DataPage<ObservedItem>>(Channel.CONFLATED)
                val secondPages = Channel<DataPage<ObservedItem>>(Channel.CONFLATED)
                val first = launch {
                    query.observeFirstPage(source.client)
                        .collect { (status, page) ->
                            if (status == Active) firstPages.send(page)
                        }
                }
                val second = launch {
                    query.observeFirstPage(source.client)
                        .collect { (status, page) ->
                            if (status == Active) secondPages.send(page)
                        }
                }
                firstPages.receive().items shouldBe source.items.take(3)
                secondPages.receive().items shouldBe source.items.take(3)
                source.subscribeCount shouldBe 2

                first.cancelAndJoin()

                awaitCondition { source.cancelCount == 1 }
                second.isActive shouldBe true
                val updated = items(5)
                    .reversed()
                source.items = updated.take(4)
                source.update(updated.first())
                secondPages.receive().items shouldBe updated.take(3)
                source.client.isOpen shouldBe true
                second.cancelAndJoin()
                awaitCondition { source.cancelCount == 2 }
            }
        }
    }

    /**
     * Equivalent selections remain equal even when callers recreate the extraction lambda.
     */
    @Test
    fun `compare queries by selection and paging settings`() {
        EqualsTester()
            .addEqualityGroup(query(), query())
            .addEqualityGroup(query(lowerBound = "0001"))
            .addEqualityGroup(query(direction = ASCENDING))
            .addEqualityGroup(query(pageSize = 2))
            .testEquals()
    }

    /**
     * OR conditions remain one composite, ANDed with the separate cursor condition.
     * Empty filters remain unfiltered even if the caller later changes the supplied list.
     */
    @Test
    fun `preserve disjunctions and support unfiltered selections`(): Unit = runBlocking {
        ObservationChannel().use { source ->
            val base = query()
            val filters = mutableListOf<CompositeQueryFilter>()
            val unfiltered = PagedDataQuery(
                entityClass = base.entityClass,
                queryFilters = filters,
                orderBy = base.orderBy,
                direction = base.direction,
                pageSize = base.pageSize,
                keyOf = base.keyOf
            )
            filters.add(CompositeQueryFilter.either(
                QueryFilter.eq(ObservedItem.Column.label(), "0001"),
                QueryFilter.eq(ObservedItem.Column.label(), "0002")
            ))
            unfiltered.read(source.client, Start)
            checkNotNull(source.lastQuery).target.filters.filterCount shouldBe 0
            val disjunction = PagedDataQuery(
                entityClass = base.entityClass,
                queryFilters = filters,
                observeFilter = base.observeFilter,
                orderBy = base.orderBy,
                direction = base.direction,
                pageSize = base.pageSize,
                keyOf = base.keyOf
            )

            disjunction.read(source.client, After("0003"))

            val sent = checkNotNull(source.lastQuery).target.filters.filterList
            sent.size shouldBe 2
            sent.first().filterCount shouldBe 2
            sent.first().operator shouldBe EITHER
            sent.last().filterList shouldBe listOf(lt(ObservedItem.Column.label(), "0003"))
            unfiltered.queryFilters shouldBe emptyList()
        }
    }

    /**
     * An unfiltered query observes all changes of its type without a placeholder condition.
     */
    @Test
    fun `observe an unfiltered first page and its updates`(): Unit = runBlocking {
        withTimeout(5_000) {
            ObservationChannel().use { source ->
                val sample = items(5)
                    .reversed()
                source.items = sample.drop(1)
                val query = PagedDataQuery(
                    entityClass = ObservedItem::class.java,
                    queryFilters = emptyList(),
                    orderBy = ObservedItem.Column.label(),
                    direction = DESCENDING,
                    pageSize = 3,
                    keyOf = { it.label }
                )
                val pages = Channel<DataPage<ObservedItem>>(Channel.CONFLATED)
                val collecting = launch {
                    query.observeFirstPage(source.client)
                        .collect { (status, page) -> if (status == Active) pages.send(page) }
                }
                try {
                    pages.receive().items shouldBe sample.drop(1)
                        .take(3)
                    checkNotNull(source.lastQuery).target.hasFilters() shouldBe false
                    checkNotNull(source.lastTopic).target.hasFilters() shouldBe false

                    source.items = sample.take(4)
                    source.update(sample.first())

                    pages.receive().items shouldBe sample.take(3)
                    source.readCount shouldBe 2
                    source.subscribeCount shouldBe 1
                } finally {
                    collecting.cancelAndJoin()
                }
                awaitCondition { source.cancelCount == 1 }
            }
        }
    }

    /**
     * Supplies seek cases for both sort directions and predecessor outcomes.
     */
    companion object {

        /**
         * Covers empty, short, exact, and multiple pages in both display orders.
         */
        @JvmStatic
        fun lastPageCases(): List<Arguments> = listOf(true, false)
            .flatMap { ascending ->
                listOf(0, 1, 2, 3, 7).map { arguments(ascending, it) }
            }

        /**
         * Covers each combination of sort direction and predecessor presence.
         */
        @JvmStatic
        fun seekCases(): List<Arguments> = listOf(
            arguments(true, true),
            arguments(true, false),
            arguments(false, true),
            arguments(false, false)
        )
    }
}
