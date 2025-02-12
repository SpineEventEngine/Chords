/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.chords.core.primitive

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
import io.spine.chords.core.keyboard.KeyModifiers.Companion.Shift
import io.spine.chords.core.keyboard.key
import io.spine.chords.core.keyboard.on

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
 * @param width
 *         preferred width of composable.
 */
public fun Modifier.preventWidthAutogrowing(
    width: Dp = TextFieldDefaults.MinWidth
): Modifier = composed {
    this.width(width)
}
