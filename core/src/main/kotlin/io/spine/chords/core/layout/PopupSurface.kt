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
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.chords.core.layout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.spine.chords.core.styling.ChordsTheme

/**
 * Gives a Material dropdown menu the Chords overlay surface and outline.
 *
 * Apply this to the menu's content modifier. It covers the menu padding as well
 * as its items, while retaining Material's positioning, scrolling, and shadow.
 *
 * Example using Material 3's `DropdownMenu`:
 * ```kotlin
 * @Composable
 * fun RefreshMenu(onRefresh: () -> Unit) {
 *     var expanded by remember { mutableStateOf(false) }
 *     Box {
 *         SecondaryButton(onClick = { expanded = true }) { Text("Actions") }
 *         DropdownMenu(
 *             expanded = expanded,
 *             onDismissRequest = { expanded = false },
 *             modifier = Modifier.popupAppearance()
 *         ) {
 *             DropdownMenuItem(
 *                 text = { Text("Refresh") },
 *                 onClick = {
 *                     expanded = false
 *                     onRefresh()
 *                 }
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * The background uses [ChordsTheme.overlayColor], a 1 dp `outline` border, and the Material
 * `extraSmall` shape. Apply it once to the whole menu, not to each item. Use [PopupSurface]
 * when constructing a custom popup panel instead.
 *
 * @return This modifier with the themed background and border appended.
 */
@Composable
public fun Modifier.popupAppearance(): Modifier =
    background(ChordsTheme.overlayColor, shapes.extraSmall)
        .border(1.dp, colorScheme.outline, shapes.extraSmall)

/**
 * Draws the themed background, outline, and shadow of a custom popup panel.
 *
 * This is a surface, not a popup window. The caller owns visibility, anchoring, focus, dismissal,
 * and content padding. Place it inside Compose's `Popup` for floating content:
 * ```kotlin
 * @Composable
 * fun HelpPopup(visible: Boolean, onDismiss: () -> Unit) {
 *     if (visible) {
 *         Popup(
 *             alignment = Alignment.TopEnd,
 *             onDismissRequest = onDismiss,
 *             properties = PopupProperties(focusable = true)
 *         ) {
 *             PopupSurface(Modifier.width(280.dp)) {
 *                 Column(Modifier.padding(16.dp)) {
 *                     Text("Choose an item to view its details.")
 *                     SecondaryButton(onClick = onDismiss) { Text("Close") }
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * By default, the background is [ChordsTheme.overlayColor]. A positive [tonalElevation] switches
 * to Material's `surface` color so Material can apply its elevation tint. Both modes use
 * `onSurface` for content and a 1 dp `outline` border. Use [popupAppearance] with a Material menu
 * that already supplies its own surface.
 *
 * @param modifier The panel's size and placement.
 * @param shape The outline of the panel.
 * @param tonalElevation A positive value requests Material's tonal surface instead of the overlay.
 * @param shadowElevation The shadow separating the panel from adjacent content.
 * @param content The panel's content, which inherits the surface foreground.
 */
@Composable
public fun PopupSurface(
    modifier: Modifier = Modifier,
    shape: Shape = shapes.small,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (tonalElevation > 0.dp) colorScheme.surface else ChordsTheme.overlayColor,
        contentColor = colorScheme.onSurface,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = BorderStroke(1.dp, colorScheme.outline),
        content = content
    )
}
