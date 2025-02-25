/*
 * Copyright 2025, TeamDev. All rights reserved.
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

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Arrangement.Horizontal
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.spine.chords.core.Component

/**
 * A list of entities in a tabular format.
 *
 * This component is intended for rendering tabular data,
 * where each row corresponds to an entity of the [E] type.
 * Users can customize row behavior and style, specify column
 * configurations for displaying the data, and visual appearance of a table.
 *
 * @param E
 *         the type of entities represented in the table.
 */
public abstract class Table<E> : Component() {

    /**
     * The list of entities with data that should be displayed in table rows.
     * Each entity should represent a row in a table.
     */
    public var entities: List<E> by mutableStateOf(listOf())

    /**
     * A list of columns to be displayed in the table.
     */
    protected abstract fun defineColumns(): List<TableColumn<E>>

    /**
     *  A callback that configures the click action for any row.
     *
     *  An entity displayed on a row comes as a parameter of the callback.
     */
    protected abstract fun onRowClick(entity: E)

    /**
     * Specifies the content to be displayed when the table has no entities.
     */
    @Composable
    protected abstract fun ColumnScope.EmptyTableContent()

    /**
     * A callback that allows to modify any row behaviour and style.
     *
     * An entity displayed on a row comes as a parameter of a callback.
     */
    protected var rowModifier: (E) -> Modifier = { Modifier }

    /**
     * Specifies the row actions available in the table.
     *
     * Row actions are displayed as a dropdown menu when the "More" button is clicked
     * at the end of each row.
     *
     * If this property is `null`, no "More" button will be shown in the table rows.
     */
    protected var rowActions: RowActionsConfig<E>? = null

    /**
     * Defines the sorting logic for the entities displayed in the table.
     *
     * By default, entities are displayed in their original order.
     */
    protected var sortBy: (List<E>) -> List<E> = { it }

    /**
     * The padding applied to the entire content of the table.
     */
    protected var contentPadding: PaddingValues = PaddingValues(16.dp)

    /**
     * The color of the selected row.
     *
     * The default value is `MaterialTheme.colorScheme.surfaceVariant`.
     */
    protected var selectedRowColor: Color? = null

    @Composable
    override fun content() {
        val columns = defineColumns()
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Center,
        ) {
            HeaderTableRow(columns)
            if (entities.isNotEmpty()) {
                ListContent(sortBy(entities), columns, rowModifier, rowActions)
            } else {
                EmptyListContent()
            }
        }
    }

    /**
     * Displays a vertical list of table rows without header.
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
    private fun ListContent(
        entities: List<E>,
        columns: List<TableColumn<E>>,
        rowModifier: (E) -> Modifier,
        rowActionsConfig: (RowActionsConfig<E>)?,
    ) {
        val listState = rememberLazyListState()
        val selectedItem: MutableState<E?> = remember { mutableStateOf(null) }
        if (selectedRowColor == null) {
            selectedRowColor = colorScheme.surfaceVariant
        }
        Box(
            modifier = Modifier.fillMaxHeight(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                state = listState
            ) {
                entities.forEach { value ->
                    item {
                        ContentTableRow(
                            entity = value,
                            columns = columns,
                            modifier = if (selectedItem.value == value) {
                                Modifier.background(selectedRowColor!!)
                                    .then(rowModifier(value))
                            } else {
                                rowModifier(value)
                            },
                            rowActionsConfig = rowActionsConfig
                        ) {
                            selectedItem.value = value
                            onRowClick(value)
                        }
                    }
                }
            }
            VerticalScrollBar(listState) { Modifier.align(Alignment.CenterEnd) }
        }
    }

    /**
     * Displays the empty state of the table.
     */
    @Composable
    private fun EmptyListContent() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Center,
            horizontalAlignment = CenterHorizontally
        ) {
            EmptyTableContent()
        }
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
 * Configuration object for row actions in a table.
 *
 * It defines how row actions should be generated and displayed for a given entity type.
 *
 * @param E The type of entity for which the actions are defined.
 * @param itemsProvider A function that dynamically generates a list of actions
 *   based on the given entity.
 * @param itemsLook The styling configuration applied to all row actions in this table.
 */
public data class RowActionsConfig<E>(
    val itemsProvider: (E) -> List<RowActionsItem<E>>,
    val itemsLook: RowActionsItemLook
)

/**
 * Configuration object for an item in a row actions menu.
 *
 * @param E The type of entity to which this action applies.
 * @param text The label of the action.
 * @param onClick A callback executed when the action is clicked,
 *   receiving the corresponding entity as a parameter.
 * @param enabled A function that determines whether the action should be enabled,
 *   based on the given entity's state. By default, it is always enabled.
 */
public data class RowActionsItem<E>(
    val text: String,
    val onClick: (E) -> Unit,
    val enabled: (E) -> Boolean = { true }
)

/**
 * An object allowing adjustments of row action item visual appearance parameters.
 *
 * @param colors The color scheme applied to the item.
 * @param modifier A modifier to apply additional styling to the item.
 * @param contentPadding The padding applied inside each dropdown menu item.
 */
public data class RowActionsItemLook(
    val colors: MenuItemColors,
    val modifier: Modifier = Modifier,
    val contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding
)

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
    rowActionsConfig: (RowActionsConfig<E>)?,
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
        rowActions = rowActionsConfig,
        value = entity
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
    rowActions: (RowActionsConfig<E>)? = null,
    value: E? = null,
    cellContent: @Composable (TableColumn<E>) -> Unit
) {
    val rowActionsVisible = remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeightIn(70.dp, 100.dp)
            .height(Min)
            .then(modifier),
        horizontalArrangement = SpaceBetween,
        verticalAlignment = CenterVertically
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
        if (rowActions != null && value != null) {
            IconButton({
                rowActionsVisible.value = true
            }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                if (rowActionsVisible.value) {
                    RowActionsDropdown(value, rowActions, rowActionsVisible.value) {
                        rowActionsVisible.value = false
                    }
                }
            }
        }
    }
    Divider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = colorScheme.outlineVariant
    )
}

/**
 * Displays the dropdown menu with row actions.
 *
 * @param E The type of entity for which the actions are provided.
 * @param value The entity for which the actions should be displayed.
 * @param config The configuration object specifying the available row actions
 *   and their appearance.
 * @param visible Whether the dropdown menu is currently visible.
 * @param onCancel A callback invoked when the dropdown menu is dismissed.
 */
@Composable
private fun <E> RowActionsDropdown(
    value: E,
    config: RowActionsConfig<E>,
    visible: Boolean,
    onCancel: () -> Unit
) {
    val items = config.itemsProvider(value)
    val look = config.itemsLook
    DropdownMenu(
        expanded = visible,
        onDismissRequest = onCancel,
    ) {
        items.forEach {
            DropdownMenuItem(
                text = { Text(it.text) },
                onClick = { it.onClick(value) },
                enabled = it.enabled(value),
                modifier = look.modifier,
                colors = look.colors,
                contentPadding = look.contentPadding
            )
        }
    }
}
