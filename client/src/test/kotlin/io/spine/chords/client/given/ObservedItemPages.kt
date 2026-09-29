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

import io.spine.chords.client.PagedDataQuery
import io.spine.chords.client.given.DesktopClientSpecEnv.item
import io.spine.client.CompositeEntityStateFilter
import io.spine.client.CompositeQueryFilter
import io.spine.client.EntityStateFilter
import io.spine.client.OrderBy.Direction
import io.spine.client.OrderBy.Direction.DESCENDING
import io.spine.client.QueryFilter

/**
 * Supplies ordered entities and queries shared by paging lifecycle tests.
 */
internal object ObservedItemPages {

    /**
     * Creates distinct IDs and labels whose lexical order matches their numeric order.
     */
    fun items(count: Int): List<ObservedItem> = (1..count).map {
        val label = it.toString()
            .padStart(4, '0')
        item(label)
    }

    /**
     * Selects labels above a lower bound, with a small page that exposes navigation boundaries.
     */
    fun query(
        lowerBound: String = "",
        direction: Direction = DESCENDING,
        pageSize: Int = 3
    ): PagedDataQuery<ObservedItem> = PagedDataQuery(
        entityClass = ObservedItem::class.java,
        queryFilters = listOf(
            CompositeQueryFilter.all(QueryFilter.gt(ObservedItem.Column.label(), lowerBound))
        ),
        observeFilter = CompositeEntityStateFilter.all(
            EntityStateFilter.gt(ObservedItem.Field.label(), lowerBound)
        ),
        orderBy = ObservedItem.Column.label(),
        direction = direction,
        pageSize = pageSize,
        keyOf = { it.label }
    )
}
