/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.primitive

import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection.Companion.Next
import androidx.compose.ui.focus.FocusDirection.Companion.Previous
import androidx.compose.ui.input.key.Key.Companion.Tab
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import io.spine.chords.keyboard.KeyModifiers.Companion.Shift
import io.spine.chords.keyboard.key
import io.spine.chords.keyboard.on

/**
 * A [Modifier], which can be used for multiline [TextField] components to
 * establish the usual navigation behavior with the Tab key.
 */
public fun Modifier.moveFocusOnTab(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    on(Tab.key.down) {
        focusManager.moveFocus(Next)
    }
    on(Shift(Tab.key).down) {
        focusManager.moveFocus(Previous)
    }
    this
}

/**
 * A [Modifier], which can be used for [TextField] components to
 * prevent them from increasing their width size when entered
 * text doesn't fit within default [TextField] width.
 *
 * This function essentially acts as an alias for the standard
 * [Modifier.width] function, responsible for defining
 * the preferred width of the field.
 *
 * Here's an example of the issue that is solved by adding this modifier:
 * https://github.com/Projects-tm/1DAM/issues/62
 *
 * @param width
 *         preferred width of composable.
 */
public fun Modifier.preventWidthAutogrowing(
    width: Dp = TextFieldDefaults.MinWidth
): Modifier = composed {
    this.width(width)
}
