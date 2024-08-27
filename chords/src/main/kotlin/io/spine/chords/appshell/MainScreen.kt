/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.appshell

import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.spine.chords.styling.borderBottom
import io.spine.chords.styling.borderRight

/**
 * Represents the main screen in the application.
 */
@Composable
public fun MainScreen(appViews: List<AppView>, initialView: AppView?) {
    val selectedItemHolder = remember {
        mutableStateOf(
            initialView ?: appViews[0]
        )
    }

    Scaffold(
        topBar = {
            TopBar(
                modifier = Modifier.borderBottom(1.dp, colorScheme.outlineVariant)
            )
        }
    ) {
        val topPadding = it.calculateTopPadding()
        NavigationDrawer(
            appViews, selectedItemHolder, topPadding,
            modifier = Modifier.borderRight(1.dp, colorScheme.outlineVariant)
        ) {
            selectedItemHolder.value.Content()
        }
    }
}
