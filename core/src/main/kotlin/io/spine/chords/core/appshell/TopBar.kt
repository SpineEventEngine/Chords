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

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.spine.chords.core.Component
import io.spine.chords.core.styling.ChordsTheme

/**
 * Represents the TopBar, aka 'Header', of the main screen.
 *
 * @param modifier A [Modifier] for this component.
 */
@OptIn(ExperimentalMaterial3Api::class)
public class TopBar(private val modifier: Modifier = Modifier) : Component() {

    /**
     * The actions displayed in the right corner of the top app bar.
     *
     * This should typically be [IconButton]s.
     * The default layout here is a Row, so icons inside will be placed horizontally.
     */
    internal var actions: @Composable RowScope.() -> Unit = {}

    @Composable
    protected override fun content() {
        val dividerColor = MaterialTheme.colorScheme.outlineVariant
        TopAppBar(
            title = {
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            actions = actions,
            modifier = modifier
                .fillMaxWidth()
                .height(ChordsTheme.dimensions.appBarHeight)
                .drawBehind {
                    val thickness = 1.dp.toPx()
                    val y = size.height - thickness / 2
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = thickness
                    )
                },
            windowInsets = WindowInsets(
                left = ChordsTheme.dimensions.spacingMedium,
                top = 0.dp,
                right = ChordsTheme.dimensions.spacingSmall,
                bottom = 0.dp
            ),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
