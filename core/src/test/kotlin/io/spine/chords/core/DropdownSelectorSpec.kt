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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.spine.chords.core.given.CustomItemSelector
import io.spine.chords.core.given.DropdownSelectorSpecEnv.choices
import io.spine.chords.core.given.DropdownSelectorSpecEnv.selector
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsDimensions
import io.spine.chords.core.styling.ChordsTheme
import io.spine.chords.core.testing.DropdownSelectorDriver
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Verifies selection, search, and layout behavior of [DropdownSelector].
 */
@DisplayName("`DropdownSelector` should")
internal class DropdownSelectorSpec {

    /**
     * A clear press must not turn the previous selection into an unfinished search.
     */
    @ParameterizedTest(name = "menu initially open = {0}")
    @ValueSource(booleans = [false, true])
    fun `clear selected values without restoring their labels as search text`(openMenu: Boolean) {
        TestApplication.install()
        val selector = selector()

        TestScene { ChordsTheme { selector.Content() } }.use { scene ->
            val driver = DropdownSelectorDriver(scene)
            if (openMenu) {
                driver.openChoices()
            }
            driver.clearSelection()

            selector.selectedItem shouldBe null
            selector.valid.value shouldBe true
            driver.input shouldBe ""
            driver.choiceCount shouldBe 0
        }
    }

    /**
     * Consuming a clear press must retain the normal focus-on-click behavior.
     */
    @Test
    fun `accept keyboard navigation after clearing an unfocused selector`() {
        TestApplication.install()
        val selector = selector()

        TestScene { ChordsTheme { selector.Content() } }.use { scene ->
            val driver = DropdownSelectorDriver(scene)
            driver.focused shouldBe false

            driver.clearSelection()
            driver.openChoicesWithKeyboard()

            driver.focused shouldBe true
            driver.choiceCount shouldBe choices.size + 1
        }
    }

    /**
     * An invisible clear icon must leave an empty editor's ordinary click handling intact.
     */
    @Test
    fun `focus and open an empty selector when clicking the clear slot`() {
        TestApplication.install()
        val selector = selector(selected = null)

        TestScene { ChordsTheme { selector.Content() } }.use { scene ->
            val driver = DropdownSelectorDriver(scene)

            driver.clearSelection()

            driver.focused shouldBe true
            driver.choiceCount shouldBe choices.size + 1
        }
    }

    /**
     * A draft has no selected value, so clicking the invisible icon should still move its caret.
     */
    @Test
    fun `place the caret when clicking the clear slot with unfinished search text`() {
        TestApplication.install()
        val selector = selector(selected = null)

        TestScene { ChordsTheme { selector.Content() } }.use { scene ->
            val driver = DropdownSelectorDriver(scene)
            val draft = choices.last().take(2)
            driver.enterText(draft)
            driver.moveCaretTo(0)

            driver.clearSelection()

            driver.input shouldBe draft
            driver.selection shouldBe TextRange(draft.length)
            selector.valid.value shouldBe false
        }
    }

    /**
     * The cleared label must not restrict the menu opened by the next pointer click.
     */
    @Test
    fun `offer every choice when reopening after clearing`() {
        TestApplication.install()
        val selector = selector()

        TestScene { ChordsTheme { selector.Content() } }.use { scene ->
            val driver = DropdownSelectorDriver(scene)
            driver.clearSelection()

            driver.openChoices()

            driver.choiceCount shouldBe choices.size + 1
        }
    }

    /**
     * Reusing the same selector must reset its state after each selection and clear operation.
     */
    @ParameterizedTest(name = "icon spacing = {0} dp")
    @ValueSource(ints = [8, 16])
    fun `clear a newly selected value after an earlier clear`(spacing: Int) {
        TestApplication.install()
        val selector = selector()
        val dimensions = ChordsDimensions(spacingSmall = spacing.dp)

        TestScene { ChordsTheme(dimensions = dimensions) { selector.Content() } }.use { scene ->
            val driver = DropdownSelectorDriver(scene, dimensions)
            driver.clearSelection()
            driver.openChoices()
            driver.selectChoice(2)
            selector.selectedItem shouldBe choices[1]

            driver.clearSelection()

            selector.selectedItem shouldBe null
            selector.valid.value shouldBe true
            driver.input shouldBe ""
            driver.choiceCount shouldBe 0
        }
    }

    /**
     * Unfinished input after clearing must still participate in filtering and validation.
     */
    @Test
    fun `filter choices by new search text after clearing`() {
        TestApplication.install()
        val selector = selector()

        TestScene { ChordsTheme { selector.Content() } }.use { scene ->
            val driver = DropdownSelectorDriver(scene)
            driver.clearSelection()

            driver.enterText(choices.last().take(2))

            selector.valid.value shouldBe false
            driver.choiceCount shouldBe 2
        }
    }

    /**
     * Choosing the search result must replace unfinished input with a valid selection.
     */
    @Test
    fun `accept a filtered choice after clearing`() {
        TestApplication.install()
        val selector = selector()

        TestScene { ChordsTheme { selector.Content() } }.use { scene ->
            val driver = DropdownSelectorDriver(scene)
            driver.clearSelection()
            driver.enterText(choices.last().take(2))

            driver.selectChoice(1)

            selector.selectedItem shouldBe choices.last()
            selector.valid.value shouldBe true
            driver.input shouldBe choices.last()
        }
    }

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
            scene.renderUntilIdle()

            val errorHeight = scene.contentSize.height
            if (reserveSpace) {
                errorHeight shouldBe initialHeight
            } else {
                errorHeight shouldBeGreaterThan initialHeight
            }

            validation.value = null
            scene.renderUntilIdle()

            scene.contentSize.height shouldBe initialHeight
        }
    }
}
