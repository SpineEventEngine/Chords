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

package io.spine.chords.core.table

import io.kotest.matchers.shouldBe
import io.spine.chords.core.table.TableSortingDirection.DESCENDING
import org.junit.jupiter.api.Test

internal class TableColumnSpec {

    @Test
    fun `store the extracted column value`() {
        val column = valuedColumn()

        column.value?.invoke(User("Alice", 2)) shouldBe "Alice"
    }

    @Test
    fun `sort naturally by column value`() {
        val column = valuedColumn()
        val state = TableSortingState<User>()
        val users = listOf(User("Charlie", 3), User("Alice", 1), User("Bob", 2))

        state.toggle(column)

        users.sortedWith(state.currentSorting!!.sorting.comparator) shouldBe
                listOf(User("Alice", 1), User("Bob", 2), User("Charlie", 3))
    }

    @Test
    fun `respect the initial sorting direction`() {
        val column = TableColumn<User>(
            name = "Created",
            value = User::created,
            sorting = TableColumnSorting(initialDirection = DESCENDING)
        )
        val state = TableSortingState<User>()

        state.toggle(column)

        state.currentSorting?.direction shouldBe DESCENDING
    }

    @Test
    fun `allow custom content for a valued column`() {
        val column = TableColumn<User>(
            name = "Name",
            value = User::name
        ) { }

        column.value?.invoke(User("Alice", 2)) shouldBe "Alice"
    }

    @Test
    fun `preserve explicit entity comparator declarations`() {
        val column = TableColumn<User>(
            name = "Name",
            sorting = TableColumnSorting(compareBy(User::name))
        ) { }
        val state = TableSortingState<User>()
        val users = listOf(User("Charlie", 3), User("Alice", 1), User("Bob", 2))

        state.toggle(column)

        users.sortedWith(state.currentSorting!!.sorting.comparator) shouldBe
                listOf(User("Alice", 1), User("Bob", 2), User("Charlie", 3))
    }

    @Test
    fun `prefer an explicit comparator over natural value sorting`() {
        val column = TableColumn<User>(
            name = "Name",
            sorting = TableColumnSorting(compareBy(User::created)),
            value = User::name
        ) { }
        val state = TableSortingState<User>()
        val users = listOf(User("Alice", 3), User("Bob", 1), User("Charlie", 2))

        state.toggle(column)

        users.sortedWith(state.currentSorting!!.sorting.comparator) shouldBe
                listOf(User("Bob", 1), User("Charlie", 2), User("Alice", 3))
    }

    @Test
    fun `keep a column without value non-sortable by default`() {
        val column = TableColumn<User>(name = "Name") { }
        val state = TableSortingState<User>()

        state.toggle(column)

        state.currentSorting shouldBe null
    }

    private fun valuedColumn(): TableColumn<User> = TableColumn(
        name = "Name",
        value = User::name
    )

    private data class User(
        val name: String,
        val created: Int
    )
}
