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

package io.spine.chords.core.primitive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode.Companion.Keyboard
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.spine.chords.core.styling.ChordsTheme

/**
 * A circular button for an action represented by a single icon.
 *
 * The default target size comes from `ChordsTheme.dimensions.iconButtonSize`; [modifier] can
 * constrain it for compact controls. The icon's own size is independent of the target. Supply an
 * accessible description on the icon and inherit `LocalContentColor` for disabled and hover colors.
 *
 * Example:
 * ```kotlin
 * @Composable
 * fun CloseDetailsAction(onClose: () -> Unit) {
 *     CircularIconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
 *         Icon(
 *             imageVector = Icons.Default.Close,
 *             contentDescription = "Close details",
 *             modifier = Modifier.size(20.dp)
 *         )
 *     }
 * }
 * ```
 *
 * Hover uses `primaryContainer` with a light outline. Press and keyboard focus use a solid
 * `primary` outline. Mouse focus does not retain the highlight after the pointer leaves, and
 * inactive windows show no interaction highlight. Add tooltips or toggle state at the call site.
 *
 * @param onClick Invoked when the action is activated.
 * @param modifier Layout adjustments for the action.
 * @param enabled Whether pointer and keyboard activation are allowed. Disabled icons are dimmed.
 * @param content The icon, including its accessible description.
 */
@Composable
public fun CircularIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val keyboardFocus = focused && LocalInputModeManager.current.inputMode == Keyboard
    val interactive = enabled && LocalWindowInfo.current.isWindowFocused
    val active = interactive && (pressed || keyboardFocus)
    val hover = interactive && hovered
    val highlighted = active || hover
    val container = if (highlighted) colorScheme.primaryContainer else Color.Transparent
    val foreground = when {
        !enabled -> LocalContentColor.current.copy(
            alpha = ChordsTheme.interaction.disabledContentAlpha
        )
        highlighted -> colorScheme.primary
        else -> LocalContentColor.current
    }
    Box(
        modifier = modifier
            .size(ChordsTheme.dimensions.iconButtonSize)
            .clip(CircleShape)
            .background(container)
            .border(
                width = 1.dp,
                color = when {
                    active -> colorScheme.primary
                    hover -> colorScheme.primary.copy(alpha = HoverOutlineAlpha)
                    else -> Color.Transparent
                },
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Center
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground, content = content)
    }
}

/**
 * A light blue hover outline distinguishes pointer feedback from the solid keyboard focus ring.
 */
private const val HoverOutlineAlpha = 0.35F
