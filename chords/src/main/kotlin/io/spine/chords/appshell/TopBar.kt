/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.appshell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Represents the TopBar, aka 'Header', of the main screen.
 *
 * @param modifier
 *         a [Modifier] for this component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopBar(modifier: Modifier = Modifier) {
    TopAppBar(
        {
            Text(
                app.name,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = modifier
            .padding(0.dp)
            .fillMaxWidth(),
        windowInsets = WindowInsets(8.dp)
    )
}
