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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role.Companion.Button
import androidx.compose.ui.semantics.Role.Companion.Tab
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.spine.chords.core.layout.Tooltip
import io.spine.chords.core.layout.WithTooltip
import io.spine.chords.core.primitive.CircularIconButton
import io.spine.chords.core.styling.ChordsTheme

/**
 * Displays application destinations beside the current view, with a collapsible sidebar.
 *
 * The sidebar starts expanded. Its toggle stays above the destinations in both states.
 * Collapsing hides destination labels and gives the freed width to the current view without
 * recreating it. Icon tooltips and the selection highlight remain available. The sidebar keeps
 * its expansion state across navigation for as long as this composition remains present.
 *
 * The application shell supplies the view navigator. Within that navigator, use:
 * ```kotlin
 * NavigationDrawer(appViews = views, topPadding = padding.calculateTopPadding())
 * ```
 *
 * @param appViews The destinations available in the current view navigator.
 * @param topPadding The space reserved for the application's top bar.
 * @param modifier Layout adjustments for the sidebar.
 */
@Composable
public fun NavigationDrawer(
    appViews: List<AppView>,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    NavigationDrawer(appViews = appViews, topPadding = topPadding, modifier = modifier, footer = {})
}

/**
 * Displays a collapsible sidebar with application actions anchored below the destinations.
 *
 * [footer] receives the expansion state and can display a [NavigationDrawerAction] and its popup.
 * Its controls do not participate in view selection. The existing view and footer compositions
 * remain present when the sidebar changes width.
 *
 * ```kotlin
 * NavigationDrawer(appViews = views, topPadding = padding.calculateTopPadding()) { expanded ->
 *     AccountAction(expanded)
 * }
 * ```
 *
 * Here `AccountAction` is an application composable that renders its button and menu.
 */
@Composable
public fun NavigationDrawer(
    appViews: List<AppView>,
    topPadding: Dp,
    modifier: Modifier = Modifier,
    footer: @Composable (expanded: Boolean) -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    var expanded by remember { mutableStateOf(true) }
    val toggleIcon = if (expanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight
    // Give each 24 dp icon equal 20 dp insets within its destination.
    val collapsedWidth = ChordsTheme.dimensions.spacingSmall * 2 + CollapsedDestinationWidth
    val width = if (expanded) ChordsTheme.dimensions.navigationWidth else collapsedWidth + 1.dp
    PermanentNavigationDrawer(
        modifier = Modifier.padding(top = topPadding),
        drawerContent = {
            Row(modifier = modifier.width(width)) {
                PermanentDrawerSheet(
                    modifier = Modifier
                        .weight(1F)
                        .fillMaxHeight(),
                    drawerContainerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(modifier = Modifier.height(ChordsTheme.dimensions.spacingSmall))
                    Box(
                        modifier = Modifier
                            .width(collapsedWidth)
                            .height(48.dp),
                        contentAlignment = Center
                    ) {
                        val action = if (expanded) "Collapse" else "Expand"
                        WithTooltip(tooltip = action) {
                            CircularIconButton(onClick = { expanded = !expanded }) {
                                Icon(
                                    imageVector = toggleIcon,
                                    contentDescription = "$action sidebar"
                                )
                            }
                        }
                    }
                    appViews.forEach { view ->
                        WithTooltip(tooltip = view.name) {
                            NavigationDestination(
                                view = view,
                                expanded = expanded,
                                selected = navigator.lastItem == view,
                                onClick = { navigator.push(view) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1F))
                    footer(expanded)
                    Spacer(modifier = Modifier.height(ChordsTheme.dimensions.spacingSmall))
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        },
        content = { CurrentScreen() }
    )
}

/**
 * Keeps icon layout, selection, and keyboard interaction unchanged when its label is hidden.
 */
@Composable
private fun NavigationDestination(
    view: AppView,
    expanded: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        unselectedContainerColor = MaterialTheme.colorScheme.surface,
        unselectedIconColor = ChordsTheme.supportingTextColor
    )
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .padding(
                horizontal = ChordsTheme.dimensions.spacingSmall,
                vertical = ChordsTheme.dimensions.spacingXSmall
            )
            .fillMaxWidth()
            .heightIn(min = maxOf(56.dp, ChordsTheme.dimensions.navigationItemHeight))
            .semantics { role = Tab },
        shape = MaterialTheme.shapes.small,
        color = colors.containerColor(selected).value,
        contentColor = colors.iconColor(selected).value
    ) {
        NavigationItemContent(
            label = view.name,
            expanded = expanded,
            icon = { Icon(view.icon, contentDescription = if (expanded) null else view.name) },
            badge = { view.NavigationBadge() }
        )
    }
}

/**
 * Displays a sidebar button that performs an action without selecting a view.
 *
 * Use this in [Application.NavigationFooter] to anchor a menu or another action. The icon keeps
 * its size and position when [expanded] changes; only the label is hidden. The button has no
 * selected state. Its tooltip and accessible name come from [label]. The tooltip appears beside
 * the button so it cannot cover the click area near the window's bottom edge.
 *
 * ```kotlin
 * NavigationDrawerAction(
 *     label = "Account",
 *     expanded = expanded,
 *     onClick = { showMenu = true },
 *     icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) }
 * )
 * ```
 *
 * Here `expanded` is the footer's argument and `showMenu` is observable menu state.
 */
@Composable
public fun NavigationDrawerAction(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationDrawerAction(
        label = label,
        expanded = expanded,
        onClick = onClick,
        icon = icon,
        showTooltip = true,
        modifier = modifier
    )
}

/**
 * Displays a sidebar action whose tooltip can be omitted for a recognizable icon.
 *
 * Setting [showTooltip] to `false` removes the hover popup while preserving the [label] as the
 * accessible name and, when [expanded], the visible button text. Layout and clicks are unchanged.
 *
 * ```kotlin
 * NavigationDrawerAction(
 *     label = "Account",
 *     expanded = expanded,
 *     onClick = { showMenu = true },
 *     icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
 *     showTooltip = false
 * )
 * ```
 *
 * Here `expanded` is the footer's argument and `showMenu` is observable menu state.
 */
@OptIn(ExperimentalFoundationApi::class) // Compose 1.5 requires opt-in for tooltip placement.
@Composable
public fun NavigationDrawerAction(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    showTooltip: Boolean,
    modifier: Modifier = Modifier
) {
    val content: @Composable () -> Unit = {
        Surface(
            onClick = onClick,
            modifier = modifier
                .padding(
                    horizontal = ChordsTheme.dimensions.spacingSmall,
                    vertical = ChordsTheme.dimensions.spacingXSmall
                )
                .fillMaxWidth()
                .heightIn(min = maxOf(56.dp, ChordsTheme.dimensions.navigationItemHeight))
                .semantics {
                    role = Button
                    contentDescription = label
                },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            contentColor = ChordsTheme.supportingTextColor
        ) {
            NavigationItemContent(label = label, expanded = expanded, icon = icon)
        }
    }
    if (showTooltip) {
        TooltipArea(
            tooltip = { Tooltip(text = label) },
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = CenterEnd,
                alignment = CenterEnd
            ),
            content = content
        )
    } else {
        content()
    }
}

/**
 * Aligns view icons and application actions in one fixed column, with optional labels and badges.
 */
@Composable
private fun NavigationItemContent(
    label: String,
    expanded: Boolean,
    icon: @Composable () -> Unit,
    badge: @Composable () -> Unit = {}
) {
    Box(propagateMinConstraints = true) {
        Row(
            modifier = Modifier.padding(end = if (expanded) 20.dp else 0.dp),
            verticalAlignment = CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .padding(start = 8.dp),
                contentAlignment = Center
            ) {
                icon()
            }
            if (expanded) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1F),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = Ellipsis
                )
            }
        }
        Box(modifier = Modifier.matchParentSize(), contentAlignment = BottomStart) {
            Box(
                modifier = Modifier
                    .width(CollapsedDestinationWidth)
                    .padding(
                        end = ChordsTheme.dimensions.spacingSmall + 2.dp,
                        bottom = ChordsTheme.dimensions.spacingSmall
                    ),
                contentAlignment = BottomEnd
            ) {
                badge()
            }
        }
    }
}

/**
 * Reserves the same icon and badge area before the label in either sidebar state.
 */
private val CollapsedDestinationWidth = 64.dp
