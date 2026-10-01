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

import androidx.compose.runtime.snapshotFlow
import io.spine.base.EntityColumn
import io.spine.base.EntityState
import io.spine.chords.client.DataObservationStatus.Cancelled
import io.spine.chords.client.DataPageCursor.After
import io.spine.chords.client.DataPageCursor.At
import io.spine.chords.client.DataPageCursor.Before
import io.spine.chords.client.DataPageCursor.End
import io.spine.chords.client.DataPageCursor.Start
import io.spine.client.CompositeEntityStateFilter
import io.spine.client.CompositeQueryFilter
import io.spine.client.OrderBy.Direction
import io.spine.client.OrderBy.Direction.ASCENDING
import io.spine.client.OrderBy.Direction.DESCENDING
import io.spine.client.QueryFilter
import java.util.Objects
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runInterruptible

/**
 * An ordered entity selection for browsing through [PagedDataNavigator].
 *
 * [orderBy] must be unique and stable, or items can be skipped or repeated between pages.
 * [keyOf] must extract that column's value. Queries compare by their selection and paging
 * settings; equivalent queries must extract the same cursor values, even though function
 * identity is excluded from equality. Creating a query starts no requests.
 *
 * @param T The selected entity type.
 * @param entityClass The entity type to select.
 * @param queryFilters Filters combined with AND; an empty list selects every entity of the type.
 *   Each composite can contain AND or OR conditions. The list is copied on construction.
 * @param observeFilter When present, must cover every change that can affect the first page's
 *   membership or order. Defaults to `null`, which observes all changes to this entity type.
 * @param orderBy The column providing each item's unique, stable cursor value.
 * @param direction The display order.
 * @param pageSize The maximum displayed items; each read includes one extra item for navigation.
 * @param keyOf Extracts the item's cursor value, which is its [orderBy] column value.
 */
public class PagedDataQuery<T : EntityState>(
    internal val entityClass: Class<T>,
    queryFilters: List<CompositeQueryFilter>,
    internal val observeFilter: CompositeEntityStateFilter? = null,
    internal val orderBy: EntityColumn,
    internal val direction: Direction,
    internal val pageSize: Int,
    internal val keyOf: (T) -> Any
) {

    /**
     * A stable selection filter, unaffected by later changes to the caller's list.
     */
    internal val queryFilters = queryFilters.toList()

    init {
        require(direction == ASCENDING || direction == DESCENDING) {
            "A page query requires an ascending or descending order."
        }
        require(pageSize in 1 until Int.MAX_VALUE) {
            "A page must leave room for one lookahead item."
        }
    }

    /**
     * Compares selection and paging settings without comparing [keyOf] function instances.
     */
    override fun equals(other: Any?): Boolean = other is PagedDataQuery<*> &&
            entityClass == other.entityClass && queryFilters == other.queryFilters &&
            observeFilter == other.observeFilter && orderBy == other.orderBy &&
            direction == other.direction && pageSize == other.pageSize

    /**
     * Hashes the same selection and paging settings used by [equals].
     */
    override fun hashCode(): Int = Objects.hash(
        entityClass, queryFilters, observeFilter, orderBy, direction, pageSize
    )

    /**
     * Fixed page data for navigation away from the live first page.
     * Blocking reads run on the IO dispatcher and create no subscription.
     * Seeking also checks for preceding items with a separate bounded read.
     */
    internal suspend fun read(client: Client, cursor: DataPageCursor): DataPage<T> =
        runInterruptible(IO) {
            val items = client.readPage(
                entityClass = entityClass,
                queryFilters = filters(cursor),
                orderBy = orderBy,
                direction = if (cursor is Before || cursor == End) reverseDirection else direction,
                limit = pageSize + 1
            )
            val hasPrevious = if (cursor is At) {
                val preceding = client.readPage(
                    entityClass = entityClass,
                    queryFilters = filters(Before(cursor.key)),
                    orderBy = orderBy,
                    direction = reverseDirection,
                    limit = 1
                )
                preceding.isNotEmpty()
            } else cursor != Start
            DataPage.from(
                items = items,
                cursor = cursor,
                pageSize = pageSize,
                hasPrevious = hasPrevious
            )
        }

    /**
     * Live first-page data and status for keeping the beginning of the selection current.
     * Each collection has a separate observation; cancellation of either ends both.
     */
    internal fun observeFirstPage(client: Client): Flow<Pair<DataObservationStatus, DataPage<T>>> =
        flow {
            val observation = client.readPageAndObserve(
                entityClass = entityClass,
                queryFilters = queryFilters,
                observeFilter = observeFilter,
                orderBy = orderBy,
                direction = direction,
                limit = pageSize + 1
            )
            try {
                snapshotFlow { observation.status.value to observation.value }
                    .takeWhile { (status, _) -> status != Cancelled }
                    .collect { (status, items) ->
                        val page = DataPage.from(
                            items = items,
                            cursor = Start,
                            pageSize = pageSize,
                            hasPrevious = false
                        )
                        emit(status to page)
                    }
            } finally {
                observation.cancel()
            }
        }

    /**
     * The opposite of [direction], used to read items before a cursor.
     */
    private val reverseDirection: Direction
        get() = if (direction == ASCENDING) DESCENDING else ASCENDING

    /**
     * Page-specific filters that preserve the selection's AND or OR conditions.
     */
    private fun filters(cursor: DataPageCursor): List<CompositeQueryFilter> {
        val comparison = when (cursor) {
            Start, End -> null
            is At -> if (direction == ASCENDING) QueryFilter.ge(orderBy, cursor.key)
                else QueryFilter.le(orderBy, cursor.key)
            is After -> if (direction == ASCENDING) QueryFilter.gt(orderBy, cursor.key)
                else QueryFilter.lt(orderBy, cursor.key)
            is Before -> if (direction == ASCENDING) QueryFilter.lt(orderBy, cursor.key)
                else QueryFilter.gt(orderBy, cursor.key)
        }
        return if (comparison == null) queryFilters
            else queryFilters + CompositeQueryFilter.all(comparison)
    }
}
