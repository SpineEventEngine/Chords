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

package io.spine.chords.client.testing

import io.spine.chords.client.DataObservationStatus
import io.spine.chords.client.DataObservationStatus.Active
import io.spine.chords.client.DataObservationStatus.Refreshing
import io.spine.chords.client.DataPage
import io.spine.chords.client.DataPageCursor
import io.spine.chords.client.DataPageCursor.After
import io.spine.chords.client.DataPageCursor.Before
import io.spine.chords.client.DataPageCursor.Start
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext

/**
 * Drives ordered page responses, delayed reads, and live notifications without a connection.
 * Integer items supply cursor values independently of any application model.
 *
 * @property pageSize The maximum number of displayed items; reads include one lookahead item.
 * @property ascending Whether navigation follows increasing item values.
 */
internal class ConfigurablePagedDataSource(
    private val pageSize: Int = 50,
    private val ascending: Boolean = false
) {

    /**
     * Items available to the next query.
     */
    var items: List<Int> = emptyList()

    /**
     * Captures every requested cursor, including first-page reads.
     */
    val cursors = mutableListOf<DataPageCursor>()

    /**
     * Optionally delays the next read even if its caller is cancelled.
     */
    @Volatile
    var pending: CompletableDeferred<Unit>? = null

    /**
     * The exception returned by the next read, if configured.
     */
    var failure: Exception? = null

    /**
     * Counts active subscriptions so teardown can be verified.
     */
    var subscriptions: Int = 0
        private set

    /**
     * Signals a change after the source data has been replaced.
     */
    val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Supplies statuses independently of values, including automatic connection recovery.
     */
    var status: DataObservationStatus = Active

    /**
     * Ends the live stream normally when the next notification is delivered.
     * If set before observation starts, the stream completes without emitting a value.
     */
    var complete = false

    /**
     * Delays the next observation's initial value after it reports that reading has started.
     */
    var observationReady: CompletableDeferred<Unit>? = null

    /**
     * Emits first-page values until cancelled or explicitly completed by the test.
     */
    fun observeFirstPage() = flow {
        subscriptions++
        try {
            if (complete) return@flow
            observationReady?.let {
                emit(Refreshing to DataPage())
                it.await()
            }
            emit(status to read(Start))
            updates.takeWhile { !complete }
                .collect { emit(status to read(Start)) }
        } finally {
            subscriptions--
        }
    }

    /**
     * Simulates an ordered query while allowing cancellation races to be reproduced.
     */
    suspend fun read(cursor: DataPageCursor): DataPage<Int> {
        cursors.add(cursor)
        failure?.let {
            failure = null
            throw it
        }
        val result = items.filter {
            when (cursor) {
                Start -> true
                is After -> if (ascending) it > cursor.key as Int else it < cursor.key as Int
                is Before -> if (ascending) it < cursor.key as Int else it > cursor.key as Int
            }
        }
            .sorted()
            .let { if (ascending != (cursor is Before)) it else it.reversed() }
            .take(pageSize + 1)
        val gate = pending
        pending = null
        if (gate != null) withContext(NonCancellable) { gate.await() }
        return DataPage.from(items = result, cursor = cursor, pageSize = pageSize)
    }
}
