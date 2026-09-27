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

package io.spine.chords.core.table.testing

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.table.Table
import io.spine.chords.core.table.TableColumn
import io.spine.chords.core.table.TableColumnSorting
import io.spine.chords.core.table.TableSortingDirection.DESCENDING
import java.awt.EventQueue.invokeAndWait
import java.awt.event.KeyEvent.VK_ENTER

/**
 * Drives sorting through real table headers and reads the displayed row order.
 */
internal class TableSortingScene(
    width: Int = 480,
    heading: String = "Value",
    sortable: Boolean = true
) : AutoCloseable {

    /**
     * Supplies rows whose default order differs from both directions of column sorting.
     */
    private val table = SortingTable(heading, sortable)

    /**
     * Renders the table at the requested width.
     */
    private val scene = TestScene(width = width.dp, height = 240.dp) { table.Content() }

    /**
     * The displayed order, read from the first cell of each row.
     */
    val rowOrder: List<Int>
        get() = scene.semanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .mapNotNull {
                it.config
                    .getOrNull(SemanticsProperties.Text)
                    ?.singleOrNull()?.text
                    ?.toIntOrNull()
            }

    /**
     * Whether interactive sorting currently exposes a reset button.
     */
    val canClear: Boolean
        get() = clearButtons()
            .isNotEmpty()

    /**
     * The reset button's layout bounds, excluding any expanded input target.
     */
    val clearButtonBounds: Rect
        get() = clearButtons()
            .single().boundsInRoot

    /**
     * The cross icon's bounds, independent of the surrounding button.
     */
    val clearIconBounds: Rect
        get() = scene.semanticsNodes(mergingEnabled = false)
            .single { it.config.getOrNull(SemanticsProperties.ContentDescription) != null }
            .boundsInRoot

    /**
     * The selected entity, which must survive changes in row ordering.
     */
    val selected: Int?
        get() = table.selectedEntity.value

    /**
     * Clicks a sortable header without invoking its nested reset action.
     */
    fun clickHeader(index: Int) {
        scene.click(headerNode(index).boundsInRoot.center)
        render()
    }

    /**
     * Measures the displayed heading, including lines created by wrapping.
     */
    fun headingBounds(index: Int): Rect = headerNode(index).boundsInRoot

    /**
     * Finds the heading independently of its parent cell's click and reset actions.
     */
    private fun headerNode(index: Int): SemanticsNode = scene.semanticsNodes(mergingEnabled = false)
        .single {
            it.config
                .getOrNull(SemanticsProperties.Text)
                ?.any { text -> text.text == table.columns[index].name } == true
        }

    /**
     * Activates the reset control by pointer or keyboard, then applies the resulting order.
     */
    fun clear(usingKeyboard: Boolean) {
        val button = clearButtons()
            .single()
        if (usingKeyboard) {
            invokeAndWait {
                checkNotNull(button.config[SemanticsActions.RequestFocus].action)
                    .invoke()
            }
            scene.pressKey(VK_ENTER)
            scene.releaseKey(VK_ENTER)
        } else {
            scene.click(button.boundsInRoot.center)
        }
        render()
    }

    /**
     * Finds reset controls by their role; this fixture has no row-action buttons.
     */
    private fun clearButtons(): List<SemanticsNode> = scene.semanticsNodes()
        .filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Button }

    /**
     * Applies state changes and selection visibility effects before reading rows.
     */
    private fun render() {
        repeat(4) { scene.render() }
    }

    /**
     * Releases composition resources after each scenario.
     */
    override fun close() {
        scene.close()
    }
}

/**
 * Keeps the middle value first by default and the first value selected through sorting changes.
 */
private class SortingTable(heading: String, sortable: Boolean) : Table<Int>() {
    init {
        entities = listOf(1, 2, 3)
        selectedEntity.value = 1
        defaultComparator = compareBy<Int> { it != 2 }
            .thenBy { it }
        columns = listOf(
            if (sortable) {
                TableColumn(name = heading, value = { it })
            } else {
                TableColumn(name = heading) { Text(it.toString()) }
            },
            TableColumn(
                name = "Reverse",
                value = { it },
                sorting = TableColumnSorting(initialDirection = DESCENDING)
            ) { }
        )
    }

    /**
     * Keeps row identity stable when its position changes.
     */
    override fun extractEntityId(entity: Int): Any = entity

    /**
     * This fixture always supplies rows.
     */
    @Composable
    override fun ColumnScope.EmptyTableContent() = Unit
}
