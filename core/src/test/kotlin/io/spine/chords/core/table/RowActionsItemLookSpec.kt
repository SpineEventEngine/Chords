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

package io.spine.chords.core.table

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsDimensions
import io.spine.chords.core.styling.ChordsTheme
import io.spine.chords.core.styling.chordsDarkColorScheme
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies that row-action defaults follow the theme without discarding explicit overrides.
 */
@DisplayName("`RowActionsItemLook` should")
internal class RowActionsItemLookSpec {

    /**
     * A menu configured only with its actions must inherit the active palette and spacing.
     */
    @Test
    fun `resolve default appearance from the active theme`() {
        val config = RowActionsConfig<String>(itemsProvider = { emptyList() })
        val scheme = chordsDarkColorScheme()
        val dimensions = ChordsDimensions(spacingMedium = 20.dp)
        var observed: RowActionsItemLook? = null

        TestScene {
            ChordsTheme(colorScheme = scheme, dimensions = dimensions) {
                observed = config.itemsLook.resolved()
            }
        }.use { }

        observed?.textColor shouldBe scheme.onSurface
        observed?.contentPadding shouldBe PaddingValues(20.dp, 0.dp)
    }

    /**
     * Zero padding is an explicit override and must not be replaced by themed spacing.
     */
    @Test
    fun `preserve explicit foreground and zero padding`() {
        val look = RowActionsItemLook(
            textColor = Color.Red,
            contentPadding = PaddingValues()
        )
        var observed: RowActionsItemLook? = null

        TestScene {
            ChordsTheme(dimensions = ChordsDimensions(spacingMedium = 20.dp)) {
                observed = look.resolved()
            }
        }.use { }

        observed shouldBe look
    }

    /**
     * A supplied baseline value is still an override when the theme uses another spacing.
     */
    @Test
    fun `preserve explicit padding equal to the default baseline`() {
        val padding = PaddingValues(12.dp, 0.dp)
        val look = RowActionsItemLook(contentPadding = padding)
        var observed: RowActionsItemLook? = null

        TestScene {
            ChordsTheme(dimensions = ChordsDimensions(spacingMedium = 20.dp)) {
                observed = look.resolved()
            }
        }.use { }

        observed?.contentPadding shouldBe padding
    }

}
