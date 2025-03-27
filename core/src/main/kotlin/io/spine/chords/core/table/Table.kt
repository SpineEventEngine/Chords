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
import androidx.compose.foundation.layout.Arrangement.End
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
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterEnd
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
 * @param E The type of entities represented in the table.
 */
public abstract class Table<E> : Component() {

    /**
     * A list of entities with data that should be displayed in table rows.
     *
     * Each entity should represent a row in the table.
     */
    public var entities: List<E> by mutableStateOf(listOf())

    /**
     * The currently selected entity (row) in the table.
     */
    public var selectedEntity: MutableState<E?> by mutableStateOf(
        mutableStateOf(null)
    )

    /**
     * A callback which is invoked whenever selected entity value is changed.
     *
     * The parameter of the lambda receives the new selected entity value.
     */
    public var onSelect: ((E) -> Unit)? = null

    /**
     * A list of columns to be displayed in the table.
     */
    public var columns: List<TableColumn<E>> by mutableStateOf(listOf())

    /**
     * A callback that allows to modify any row behaviour and style.
     *
     * An entity displayed in a row comes as a parameter of a callback.
     */
    protected var rowModifier: (E) -> Modifier by mutableStateOf({ Modifier })

    /**
     * Specifies the row actions available in the table.
     *
     * Row actions are displayed as a dropdown menu when the "More" button
     * is clicked at the end of each row.
     *
     * If this property is `null`, no "More" button will be shown in table rows.
     */
    protected var rowActions: RowActionsConfig<E>? by mutableStateOf(null)

    /**
     * Defines the sorting logic for the entities displayed in the table.
     *
     * By default, entities are displayed in their original order.
     */
    protected var sortBy: Comparator<E> by mutableStateOf(Comparator { _, _ -> 0 })

    /**
     * The padding applied to the entire content of the table.
     */
    protected var contentPadding: PaddingValues by mutableStateOf(PaddingValues(16.dp))

    /**
     * The color of the selected row.
     *
     * The default value is `MaterialTheme.colorScheme.surfaceVariant`.
     */
    protected var selectedRowColor: Color? by mutableStateOf(null)

    /**
     * Specifies the content to be displayed when the table has no entities.
     */
    @Composable
    protected abstract fun ColumnScope.EmptyTableContent()

    /**
     * Extracts a unique identifier from an entity.
     *
     * This function is used to identify entities based on a stable value,
     * ensuring that changes to mutable properties do not affect
     * entity identification.
     *
     * The ID's equality is determined using structural equality operator (`==`).
     * Therefore, the returned identifier should be a type that supports
     * meaningful structural equality.
     *
     * @param entity An entity from which to extract the identifier.
     * @return The ID of an entity.
     */
    protected abstract fun extractEntityId(entity: E): Any

    /**
     * Changes the [selectedEntity] whenever selected row is changed.
     *
     * @param entity The entity associated with the clicked row.
     */
    private fun changeSelectedEntity(entity: E) {
        selectedEntity.value = entity
        onSelect?.invoke(entity)
    }

    @Composable
    override fun content() {
        val sortedEntities = entities.sortedWith(sortBy)
        val tableColumns = columns.toMutableList()
        if (rowActions != null) {
            tableColumns.add(rowActionsColumn(rowActions!!))
        }
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Center,
        ) {
            HeaderTableRow(tableColumns)
            if (entities.isNotEmpty()) {
                ContentList(sortedEntities, tableColumns)
            } else {
                EmptyContentList()
            }
        }
    }

    /**
     * Displays the table content without a header.
     *
     * @param entities The list of entities with data that
     *   should be displayed in table rows.
     * @param columns A list of columns to be displayed in the table.
     */
    @Composable
    private fun ContentList(
        entities: List<E>,
        columns: List<TableColumn<E>>
    ) {
        val listState = rememberLazyListState()
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
                            modifier = contentTableRowModifier(value)
                        ) {
                            changeSelectedEntity(value)
                        }
                    }
                }
            }
            VerticalScrollBar(listState) { Modifier.align(CenterEnd) }
        }
    }

    private fun contentTableRowModifier(entity: E): Modifier {
        val selectedEntityValue = selectedEntity.value
        return if (selectedEntityValue != null &&
            extractEntityId(selectedEntityValue) == extractEntityId(entity)
        ) {
            if (entity != selectedEntityValue) {
                // Make sure that selected entity value is always up to date
                // with the `entities` list if it contains an updated
                // entity value.
                changeSelectedEntity(entity)
            }
            rowModifier(entity).background(selectedRowColor!!)
        } else {
            rowModifier(entity)
        }
    }

    /**
     * Displays the empty state of the table.
     */
    @Composable
    private fun EmptyContentList() {
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
 * @param name The name of the column to be displayed in a header.
 * @param horizontalArrangement The horizontal arrangement of the column's content.
 *   The default value is `Arrangement.Center`.
 * @param weight The proportional width to allocate to this column
 *   relative to other columns. Must be positive. The default value is `1F`
 *   meaning that if all columns have this `weight` value, their width is equal.
 * @param padding The padding values of each cell's content in this column.
 *   By default, no padding is applied.
 * @param cellContent A callback that specifies what element to display
 *   inside each cell of this column.
 */
public data class TableColumn<E>(
    val name: String,
    val horizontalArrangement: Horizontal = Center,
    val weight: Float = 1F,
    val padding: PaddingValues = PaddingValues(),
    val cellContent: @Composable (E) -> Unit
)

/**
 * Configuration object for row actions in a table.
 *
 * Row actions are presented as a dropdown menu that opens when the "More" button
 * displayed at the end of each table row is clicked.
 *
 * This object defines what actions should be displayed and how they are styled.
 *
 * @param E The type of entity for which the actions are defined.
 * @param itemsProvider A function that provides a list of actions
 *   based on the given entity.
 * @param itemsLook The styling configuration applied to all row actions
 *   in this table.
 * @param modifier A modifier to be applied to the menu.
 * @param buttonPadding The padding around the "More" button,
 *   affecting its placement within the cell. By default, no padding is applied,
 *   placing the button at the right edge of the cell.
 */
public data class RowActionsConfig<E>(
    val itemsProvider: (E) -> List<RowActionsItem<E>>,
    val itemsLook: RowActionsItemLook,
    val modifier: Modifier = Modifier,
    val buttonPadding: PaddingValues = PaddingValues()
)

/**
 * Describes an item in a row actions menu.
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
 * @param textColor The color of the item text.
 * @param modifier A modifier to apply additional styling to the item.
 * @param contentPadding The padding applied inside each dropdown menu item.
 *   By default, no padding is applied for the item content.
 */
public data class RowActionsItemLook(
    val textColor: Color,
    val modifier: Modifier = Modifier,
    val contentPadding: PaddingValues = PaddingValues(0.dp)
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
 * @param columns A list of column configuration objects
 *   with information about headers.
 */
@Composable
private fun <E> HeaderTableRow(
    columns: List<TableColumn<E>>,
) {
    TableRow(columns = columns) { column ->
        Text(
            text = column.name,
            style = typography.titleMedium
        )
    }
}

/**
 * Table row component that supports a click action.
 *
 * @param columns A list of columns from which the row consists.
 * @param entity The entity to represent in a row.
 * @param modifier The [Modifier] to be applied to this row.
 * @param onClick A callback that is triggered when a user clicks on a row.
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
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
            ) { onClick() },
    ) { column -> column.cellContent(entity) }
}

/**
 * Table row component.
 *
 * @param columns A list of columns from which the row consists.
 * @param modifier The [Modifier] to be applied to this row.
 * @param cellContent A callback that specifies what element to display
 *   inside each cell of this column.
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
            .then(modifier),
        horizontalArrangement = SpaceBetween,
        verticalAlignment = CenterVertically
    ) {
        columns.forEach { column ->
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
        color = colorScheme.outlineVariant
    )
}

/**
 * Creates a `TableColumn` that displays a "More" button in each cell.
 *
 * Clicking the button opens the row actions menu.
 *
 * @param rowActionsConfig The configuration for row actions.
 * @return A `TableColumn` with a "More" button for triggering row actions.
 */
private fun <E> rowActionsColumn(
    rowActionsConfig: RowActionsConfig<E>
): TableColumn<E> {
    return TableColumn(
        name = "",
        horizontalArrangement = End,
        padding = rowActionsConfig.buttonPadding
    ) { entity ->
        val rowActionsVisible = remember { mutableStateOf(false) }
        RowActionsButton(
            entity,
            rowActionsConfig,
            rowActionsVisible
        )
    }
}

/**
 * Displays a button that opens a row actions dropdown menu.
 *
 * When clicked, this button reveals a dropdown menu containing
 * actions specific to the given entity.
 *
 * @param E The type of entity for which the row actions are defined.
 * @param entity The entity for which the row actions are displayed.
 * @param config The configuration defining available actions
 *   and their appearance.
 * @param visibility A state that controls the visibility
 *   of the dropdown menu.
 */
@Composable
private fun <E> RowActionsButton(
    entity: E,
    config: RowActionsConfig<E>,
    visibility: MutableState<Boolean>,
) {
    IconButton(
        modifier = Modifier.size(48.dp),
        onClick = {
            visibility.value = true
        }
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        if (visibility.value) {
            RowActionsDropdown(entity, config, visibility.value) {
                visibility.value = false
            }
        }
    }
}

/**
 * Displays the dropdown menu with row actions.
 *
 * @param E The type of entity for which the actions are provided.
 * @param value The entity for which the actions should be displayed.
 * @param config The configuration object specifying the row actions
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
        modifier = config.modifier
    ) {
        items.forEach {
            DropdownMenuItem(
                text = { Text(it.text) },
                onClick = {
                    onCancel()
                    it.onClick(value)
                },
                enabled = it.enabled(value),
                modifier = look.modifier,
                colors = MenuDefaults.itemColors(
                    textColor = look.textColor
                ),
                contentPadding = look.contentPadding
            )
        }
    }
}
