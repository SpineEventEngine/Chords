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

package io.spine.chords.core

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.spine.chords.core.given.CustomItemSelector
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsDimensions
import io.spine.chords.core.styling.ChordsTheme
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Verifies [DropdownSelector] item insets and validation message spacing.
 */
@DisplayName("`DropdownSelector` should")
internal class DropdownSelectorSpec {

    /**
     * Custom content used to lose the insets that were owned by the default Text renderer.
     */
    @Test
    fun `inset custom items using the active theme`() {
        val selector = CustomItemSelector()

        TestScene {
            ChordsTheme(dimensions = ChordsDimensions(spacingMedium = 20.dp)) {
                selector.ItemContent("Item")
            }
        }.use { scene ->
            scene.contentSize.width shouldBe 60
            scene.contentSize.height shouldBe 20
        }
    }

    /**
     * A caller may deliberately supply edge-to-edge item content.
     */
    @Test
    fun `preserve explicit zero item padding`() {
        val selector = CustomItemSelector().apply {
            itemContentPadding = PaddingValues()
        }

        TestScene {
            ChordsTheme(dimensions = ChordsDimensions(spacingMedium = 20.dp)) {
                selector.ItemContent("Item")
            }
        }.use { scene ->
            scene.contentSize.width shouldBe 20
            scene.contentSize.height shouldBe 20
        }
    }

    /**
     * Forms retain their reserved row; compact filters reclaim it after an error clears.
     */
    @ParameterizedTest(name = "reserveValidationSpace = {0}")
    @ValueSource(booleans = [false, true])
    fun `show errors and reserve empty validation space only when enabled`(reserveSpace: Boolean) {
        TestApplication.install()
        val validation = mutableStateOf<String?>(null)
        val selector = CustomItemSelector()
            .apply {
                label = "Filter"
                value = mutableStateOf(null)
                reserveValidationSpace = reserveSpace
                externalValidationMessage = validation
            }

        TestScene {
            ChordsTheme { selector.Content() }
        }.use { scene ->
            val initialHeight = scene.contentSize.height

            validation.value = "A value must be set."
            repeat(30) { scene.render() }

            val errorHeight = scene.contentSize.height
            if (reserveSpace) {
                errorHeight shouldBe initialHeight
            } else {
                errorHeight shouldBeGreaterThan initialHeight
            }

            validation.value = null
            repeat(30) { scene.render() }

            scene.contentSize.height shouldBe initialHeight
        }
    }
}
