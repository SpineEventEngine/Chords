/*
 * Copyright 2024, TeamDev. All rights reserved.
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

package io.spine.chords.table

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Arrangement.Horizontal
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A list of entities in a tabular format.
 *
 * This component is intended for rendering tabular data,
 * where each row corresponds to an entity of the [E] type.
 * Users can customize row behavior and style, as well as specify column
 * configurations for displaying the data.
 *
 * @param entities
 *         the list of entities with data that should be displayed in table rows.
 *         Each entity should represent a row in a table.
 * @param onRowClick
 *         a callback that configures the click action for any row;
 *         An entity displayed on a row comes as a parameter of the callback.
 * @param rowModifier
 *         a callback that allows to modify any row behaviour and style.
 *         An entity displayed on a row comes as a parameter of a callback.
 * @param columns
 *         a list of columns to be displayed in the table.
 *         They are displayed in the order they are passed.
 * @param E
 *         the type of entities represented in the table.
 */
@Composable
public fun <E> Table(
    entities: List<E>,
    onRowClick: (E) -> Unit = {},
    rowModifier: (E) -> Modifier = { Modifier },
    columns: List<TableColumn<E>>
) {
    Column {
        HeaderTableRow(columns)
        ContentList(entities, columns, onRowClick, rowModifier)
    }
}

/**
 * Table column configuration.
 *
 * @param name
 *         the name of the column to be displayed in column's header.
 * @param horizontalArrangement
 *         the horizontal arrangement of the column's content.
 * @param weight
 *         the proportional width to allocate to this column
 *         relative to other columns. Must be positive.
 * @param padding
 *         the padding values of each cell's content in this column.
 * @param cellContent
 *         a callback that specifies what element to display
 *         inside each cell of this column.
 *         An entity of type [E] comes as a parameter of a callback.
 */
public data class TableColumn<E>(
    val name: String,
    val horizontalArrangement: Horizontal = Center,
    val weight: Float = 1F,
    val padding: PaddingValues = PaddingValues(0.dp),
    val cellContent: @Composable (E) -> Unit
)

/**
 * A component that displays a vertical list of table rows without header.
 *
 * @param entities
 *         the list of entities with data that should be displayed in table rows.
 * @param columns
 *         a list of columns to be displayed in the table.
 * @param onRowClick
 *         a callback that configures the click action for any row;
 *         An entity displayed on a row comes as a parameter of the callback.
 * @param rowModifier
 *         a callback that allows to modify any row behaviour and style.
 *         An entity displayed on a row comes as a parameter of the callback.
 */
@Composable
private fun <E> ContentList(
    entities: List<E>,
    columns: List<TableColumn<E>>,
    onRowClick: (E) -> Unit,
    rowModifier: (E) -> Modifier
) {
    val listState = rememberLazyListState()
    Box(
        modifier = Modifier.fillMaxHeight(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            state = listState
        ) {
            entities.forEach { value ->
                item {
                    ContentTableRow(value, columns, rowModifier(value)) {
                        onRowClick(value)
                    }
                }
            }
        }
        VerticalScrollBar(listState) { Modifier.align(Alignment.CenterEnd) }
    }
}

/**
 * Vertical scrollbar component.
 */
@Composable
private fun VerticalScrollBar(
    listState: LazyListState,
    modifierExtender: Modifier.() -> Modifier
) {
    VerticalScrollbar(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 5.dp)
            .modifierExtender(),
        adapter = rememberScrollbarAdapter(
            scrollState = listState
        )
    )
}

/**
 * Table row with headers.
 *
 * @param columns
 *         a list of column configuration objects
 *         with information about headers.
 */
@Composable
private fun <E> HeaderTableRow(
    columns: List<TableColumn<E>>,
) {
    TableRow(columns = columns) { column ->
        Text(
            text = column.name,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/**
 * Table row component that supports a click action.
 *
 * @param columns
 *         a list of columns from which the row consists.
 * @param entity
 *         the entity to represent in a row.
 * @param modifier
 *         the [Modifier] to be applied to this row.
 * @param onClick
 *         a callback that is triggered when a user clicks on a row.
 */
@Composable
private fun <E> ContentTableRow(
    entity: E,
    columns: List<TableColumn<E>>,
    modifier: Modifier,
    onClick: () -> Unit
) {
    TableRow(
        columns = columns,
        modifier = Modifier
            .then(modifier)
            .clickable (
                interactionSource = MutableInteractionSource(),
                indication = null,
            ) { onClick() },
    ) { column -> column.cellContent(entity) }
}

/**
 * Table row component.
 *
 * @param columns
 *         a list of columns from which the row consists.
 * @param modifier
 *         the [Modifier] to be applied to this row.
 * @param cellContent
 *         a callback that specifies what element to display
 *         inside each cell of this column.
 *         A column to which the cell belongs comes
 *         as a parameter of a callback.
 */
@Composable
private fun <E> TableRow(
    columns: List<TableColumn<E>>,
    modifier: Modifier = Modifier,
    cellContent: @Composable (TableColumn<E>) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeightIn(70.dp, 100.dp)
            .height(Min)
            .then(modifier)
    ) {
        columns.forEach {column ->
            Row(
                modifier = Modifier
                    .weight(column.weight)
                    .fillMaxHeight()
                    .padding(column.padding),
                horizontalArrangement = column.horizontalArrangement,
                verticalAlignment = CenterVertically
            ) { cellContent(column) }
        }
    }
    Divider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
