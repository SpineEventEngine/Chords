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

package io.spine.chords.core.appshell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.spine.chords.core.styling.ChordsTheme

/**
 * Lays out a desktop view with page actions, a toolbar, a work area, and an optional details pane.
 *
 * The scaffold fills its parent, which must provide a bounded height. The work area fills the
 * space below the header and toolbar. Callers manage navigation, selection, and scrolling.
 *
 * - [actions] appear beside the title and are omitted when [showHeader] is `false`.
 * - [toolbar] spans both the work area and details pane. It can sit inside the bordered surface
 *   or directly on the page background through [toolbarInWorkArea].
 * - [supportingPane] reserves a fixed width on the right. Pass `null` to give that space back to
 *   [content]. The pane does not collapse automatically in narrow windows.
 * - [content] and [supportingPane] own their scrolling. Use a lazy list for a table-like list or
 *   `ScrollableColumn` for details; keep fixed headings and actions outside the scroll container.
 *
 * Example with a selectable list and details:
 * ```kotlin
 * @Composable
 * fun ItemBrowser(labels: List<String>, onAdd: () -> Unit) {
 *     var selected by remember { mutableStateOf<String?>(null) }
 *     AppViewScaffold(
 *         title = "Items",
 *         actions = { PrimaryButton(onClick = onAdd) { Text("Add") } },
 *         supportingPane = {
 *             Text("Details", style = MaterialTheme.typography.titleMedium)
 *             ScrollableColumn {
 *                 Text(selected ?: "Select an item to see its details.")
 *             }
 *         }
 *     ) {
 *         LazyColumn {
 *             items(labels) { label ->
 *                 Text(
 *                     text = label,
 *                     modifier = Modifier.fillMaxWidth()
 *                         .clickable { selected = label }
 *                         .padding(12.dp)
 *                 )
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * Example with controls in place of a heading. Put actions in [toolbar] when hiding the header:
 * ```kotlin
 * @Composable
 * fun SearchView(onSearch: (String) -> Unit, results: @Composable () -> Unit) {
 *     var query by remember { mutableStateOf("") }
 *     AppViewScaffold(
 *         title = "Search",
 *         showHeader = false,
 *         toolbarInWorkArea = false,
 *         toolbar = {
 *             OutlinedTextField(
 *                 value = query,
 *                 onValueChange = { query = it },
 *                 modifier = Modifier.weight(1F),
 *                 singleLine = true
 *             )
 *             PrimaryButton(onClick = { onSearch(query) }) { Text("Search") }
 *         }
 *     ) {
 *         results()
 *     }
 * }
 * ```
 *
 * Omitted appearance values come from [ChordsTheme] and `MaterialTheme`. Set application-wide
 * dimensions on the theme; use parameters here for differences specific to one view.
 *
 * @param title The view title.
 * @param modifier A modifier applied to the complete view.
 * @param description Optional supporting text displayed below the title.
 * @param containerColor The page background, or [Color.Unspecified] to use the
 *   current Material background color.
 * @param workAreaColor The work-area surface, or [Color.Unspecified] to use the
 *   current Material surface color.
 * @param outlineColor The work-area border, or [Color.Unspecified] to use the
 *   current Material outline variant.
 * @param pagePadding The page inset, or `null` to use the Chords theme value.
 * @param contentPadding Padding around the primary work area content.
 * @param workAreaShape The work-area shape, or `null` to use the current
 *   Material medium shape.
 * @param toolbarHeight The minimum toolbar height, or `null` to use the current Chords
 *   control height.
 * @param supportingPaneWidth The details pane width including its padding, or `null` to use the
 *   current Chords theme value.
 * @param supportingPanePadding The details pane inset, or `null` to use the
 *   current Chords theme value.
 * @param showHeader Whether to display the view title, description, and header actions.
 * @param toolbarInWorkArea Whether the toolbar belongs to the bordered work area.
 *   When false, it appears directly on the page background above the work area.
 * @param sectionSpacing The gap between page sections. Defaults to the theme's large gap.
 * @param actions Primary and secondary page actions displayed near the title.
 * @param toolbar Optional controls displayed above the work area.
 * @param supportingPane Optional details or contextual content displayed on
 *   the right side of the work area.
 * @param content The primary work area content.
 */
@Composable
@Suppress(
    "LongMethod", // The complete scaffold hierarchy is clearer in one composable.
    "LongParameterList" // Each parameter is an independent layout override point.
)
public fun AppViewScaffold(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    containerColor: Color = Color.Unspecified,
    workAreaColor: Color = Color.Unspecified,
    outlineColor: Color = Color.Unspecified,
    pagePadding: PaddingValues? = null,
    contentPadding: PaddingValues = PaddingValues(),
    workAreaShape: Shape? = null,
    toolbarHeight: Dp? = null,
    supportingPaneWidth: Dp? = null,
    supportingPanePadding: PaddingValues? = null,
    showHeader: Boolean = true,
    toolbarInWorkArea: Boolean = true,
    sectionSpacing: Dp = ChordsTheme.dimensions.spacingLarge,
    actions: @Composable RowScope.() -> Unit = {},
    toolbar: (@Composable RowScope.() -> Unit)? = null,
    supportingPane: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val pageColor = if (containerColor == Color.Unspecified) {
        MaterialTheme.colorScheme.background
    } else {
        containerColor
    }
    val surfaceColor = if (workAreaColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        workAreaColor
    }
    val borderColor = if (outlineColor == Color.Unspecified) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        outlineColor
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = pageColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    pagePadding ?: PaddingValues(ChordsTheme.dimensions.spacingLarge)
                ),
            verticalArrangement = spacedBy(sectionSpacing)
        ) {
            if (showHeader) {
                ViewHeader(title, description, actions)
            }
            if (toolbar != null && !toolbarInWorkArea) {
                ViewToolbar(toolbarHeight, 0.dp, toolbar)
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1F),
                shape = workAreaShape ?: MaterialTheme.shapes.medium,
                color = surfaceColor,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (toolbar != null && toolbarInWorkArea) {
                        ViewToolbar(
                            toolbarHeight,
                            ChordsTheme.dimensions.spacingMedium,
                            toolbar
                        )
                        Divider(color = borderColor)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1F)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1F)
                                .fillMaxHeight()
                                .padding(contentPadding),
                            content = content
                        )
                        if (supportingPane != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .background(borderColor)
                            )
                            Column(
                                modifier = Modifier
                                    .width(
                                        supportingPaneWidth
                                            ?: ChordsTheme.dimensions.supportingPaneWidth
                                    )
                                    .fillMaxHeight()
                                    .padding(
                                        supportingPanePadding ?: PaddingValues(
                                            ChordsTheme.dimensions.spacingLarge
                                        )
                                    ),
                                content = supportingPane
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Aligns toolbar controls consistently inside the work area or directly on the page.
 */
@Composable
private fun ViewToolbar(
    height: Dp?,
    horizontalPadding: Dp,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height ?: ChordsTheme.dimensions.controlHeight)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = spacedBy(ChordsTheme.dimensions.spacingSmall),
        verticalAlignment = CenterVertically,
        content = content
    )
}

/**
 * Renders an application view's heading and actions.
 *
 * @param title The view title.
 * @param description Optional text displayed below the title.
 * @param actions Page actions placed at the end of the heading row.
 */
@Composable
private fun ViewHeader(
    title: String,
    description: String?,
    actions: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = SpaceBetween,
        verticalAlignment = CenterVertically
    ) {
        Column(modifier = Modifier.weight(1F)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.padding(end = ChordsTheme.dimensions.spacingLarge),
            horizontalArrangement = spacedBy(ChordsTheme.dimensions.spacingLarge),
            verticalAlignment = CenterVertically,
            content = actions
        )
    }
}
