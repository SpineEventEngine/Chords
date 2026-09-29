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

import io.spine.chords.client.DataPageCursor.Before
import io.spine.chords.client.DataPageCursor.Start

/**
 * Holds one page in the query's display order and the available navigation directions.
 *
 * @param T The type of item displayed by the page.
 * @property items The displayed items, excluding the query's lookahead item.
 * @property hasNext Whether another page follows this page.
 * @property hasPrevious Whether another page precedes this page.
 */
internal data class DataPage<T>(
    val items: List<T> = emptyList(),
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
) {

    /**
     * Interprets bounded query results for navigation in either direction.
     */
    companion object {

        /**
         * Removes the lookahead item and restores display order after a backward query.
         * The query must request at most [pageSize] plus one item.
         * A [DataPageCursor.Before] result always has a next page; a [DataPageCursor.After] result
         * always has a previous page. The lookahead item determines whether more items exist in the
         * requested direction.
         */
        fun <T> from(
            items: List<T>,
            cursor: DataPageCursor,
            pageSize: Int
        ): DataPage<T> {
            val backward = cursor is Before
            val displayed = items.take(pageSize)
            val hasMore = items.size > pageSize
            return DataPage(
                items = if (backward) displayed.reversed() else displayed,
                hasNext = if (backward) true else hasMore,
                hasPrevious = if (backward) hasMore else cursor != Start
            )
        }
    }
}
