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

package io.spine.chords.core.layout

import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import io.spine.chords.core.styling.ChordsTheme

/**
 * Shows a decorative illustration and guidance when a work area has no content or selection.
 *
 * Fills the available area and centers the illustration and caption as a group. Constrain the
 * parent or [modifier] to the intended empty area; the component does not decide whether data is
 * empty and does not provide scrolling.
 *
 * Example in a details pane:
 * ```kotlin
 * @Composable
 * fun EmptyDetails() {
 *     EmptyState(
 *         illustration = rememberVectorPainter(Icons.Outlined.FolderOpen),
 *         message = "Select an item to see its details",
 *         modifier = Modifier.size(width = 360.dp, height = 400.dp)
 *     )
 * }
 * ```
 *
 * The illustration is rendered at 96 dp and tinted with `onSurfaceVariant` at 85% opacity;
 * use a monochrome asset. It has no accessibility description because the caption carries the
 * instruction. The caption inherits the surrounding text style and uses `onSurfaceVariant`.
 * Their gap follows `ChordsTheme.dimensions.spacingLarge`.
 *
 * @param illustration A decorative illustration whose tint follows the active theme.
 * @param message Explains what the user can do to populate the area.
 * @param modifier Bounds and padding for the empty area.
 */
@Composable
public fun EmptyState(
    illustration: Painter,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Center,
        horizontalAlignment = CenterHorizontally
    ) {
        Icon(
            painter = illustration,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.85F)
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = ChordsTheme.dimensions.spacingLarge),
            color = colorScheme.onSurfaceVariant
        )
    }
}
