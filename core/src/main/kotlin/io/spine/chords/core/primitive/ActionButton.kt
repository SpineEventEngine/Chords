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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.InputMode.Companion.Keyboard
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.spine.chords.core.styling.ChordsTheme

/**
 * Keeps the sizing and interaction behavior of [PrimaryButton] and [SecondaryButton] consistent.
 *
 * Internal rendering primitive; application code uses the public buttons. [filled] selects a
 * primary surface or an outlined surface without changing the content layout or interaction area.
 * A wrapper supplies the variant, for example:
 * ```kotlin
 * @Composable
 * fun ApplyAction(enabled: Boolean, onApply: () -> Unit) {
 *     ActionButton(
 *         onClick = onApply,
 *         modifier = Modifier,
 *         enabled = enabled,
 *         filled = true
 *     ) {
 *         Text("Apply")
 *     }
 * }
 * ```
 *
 * The single clickable row owns pointer and keyboard events. Its surface reserves Material's
 * minimum interaction area so filled and outlined buttons align. Keep interaction feedback on
 * this layer: adding a clickable Material surface would introduce a second indication.
 * Press takes precedence over keyboard focus, then hover. Feedback is cleared for disabled
 * controls and inactive windows; mouse focus alone does not keep a highlight.
 *
 * @param onClick Invoked when the enabled action is activated.
 * @param modifier Layout adjustments applied before shared sizing.
 * @param enabled Whether pointer and keyboard activation are allowed.
 * @param filled Whether to use the primary surface instead of an outlined surface.
 * @param content The centered row content, inheriting `labelLarge` and the variant's foreground.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ActionButton(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    filled: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val pressed by interactions.collectIsPressedAsState()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val keyboardFocus = focused && windowFocused &&
            LocalInputModeManager.current.inputMode == Keyboard
    val states = ChordsTheme.interaction
    val stateAlpha = when {
        !enabled || !windowFocused -> 0F
        pressed -> states.pressedStateAlpha
        keyboardFocus -> states.focusedStateAlpha
        hovered -> states.hoveredStateAlpha
        else -> 0F
    }
    val container = if (filled) {
        colorScheme.onSurface
            .copy(alpha = (stateAlpha * FilledStateEmphasis).coerceIn(0F, 1F))
            .compositeOver(colorScheme.primary)
    } else {
        colorScheme.onSurface.copy(alpha = stateAlpha)
    }
    val foreground = if (filled) colorScheme.onPrimary else colorScheme.onSurface
    Surface(
        modifier = modifier
            .heightIn(min = ChordsTheme.dimensions.compactControlHeight)
            .minimumInteractiveComponentSize(),
        shape = ButtonDefaults.shape,
        color = if (enabled) {
            container
        } else {
            colorScheme.onSurface.copy(alpha = if (filled) DisabledContainerAlpha else 0F)
        },
        contentColor = if (enabled) foreground else ChordsTheme.disabledContentColor,
        border = actionBorder(filled, enabled, keyboardFocus)
    ) {
        ProvideTextStyle(typography.labelLarge) {
            Row(
                modifier = Modifier
                    .defaultMinSize(ButtonDefaults.MinWidth, ButtonDefaults.MinHeight)
                    .clickable(
                        interactionSource = interactions,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick
                    )
                    .padding(ButtonDefaults.ContentPadding),
                horizontalArrangement = Center,
                verticalAlignment = CenterVertically,
                content = content
            )
        }
    }
}

/**
 * Uses a clear keyboard outline while keeping ordinary filled actions borderless.
 */
@Composable
private fun actionBorder(filled: Boolean, enabled: Boolean, keyboardFocus: Boolean): BorderStroke? {
    if (filled) {
        return if (enabled && keyboardFocus) BorderStroke(1.dp, colorScheme.onPrimary) else null
    }
    val color = when {
        !enabled -> colorScheme.outlineVariant
        keyboardFocus -> colorScheme.primary
        else -> colorScheme.outline
    }
    return BorderStroke(1.dp, color)
}

/**
 * Filled actions need a stronger surface shift than outlined controls.
 */
private const val FilledStateEmphasis = 3F

/**
 * Matches the subdued Material filled-button surface while an action is disabled.
 */
private const val DisabledContainerAlpha = 0.12F
