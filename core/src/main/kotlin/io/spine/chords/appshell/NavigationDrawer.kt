/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.appshell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Represents a navigation bar that changes the current view
 * of the main screen.
 *
 * @param appViews
 *         a list application views for which to display respective
 *         navigation items.
 * @param currentAppView
 *         the currently selected view.
 * @param appContent
 *         content displayed to the right of the drawer.
 */
@Composable
public fun NavigationDrawer(
    appViews: List<AppView>,
    currentAppView: MutableState<AppView>,
    topPadding: Dp,
    modifier: Modifier = Modifier,
    appContent: @Composable () -> Unit
) {
    PermanentNavigationDrawer(
        modifier = Modifier.padding(top = topPadding),
        drawerContent = {
            PermanentDrawerSheet(modifier = modifier.width(240.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                appViews.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.name) },
                        selected = currentAppView.value.name == item.name,
                        onClick = {
                            currentAppView.value = item
                        },
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 4.dp
                        )
                    )
                }
            }
        },
        content = appContent
    )
}
