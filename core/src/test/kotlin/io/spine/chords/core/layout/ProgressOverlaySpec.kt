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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Blue
import androidx.compose.ui.graphics.Color.Companion.Green
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the way that [ProgressOverlay] displays its content, and the way that
 * the overlay prevents that content from being operated while it is active.
 */
@DisplayName("`ProgressOverlay` should")
internal class ProgressOverlaySpec {

    /**
     * An inactive overlay is not composed at all, so it neither displays
     * anything, nor intercepts the interaction with the content.
     */
    @Test
    fun `not display the overlay when it is inactive`() {
        var indicatorDisplayed = false

        overlayScene(active = false, indicator = { indicatorDisplayed = true }).use { scene ->
            indicatorDisplayed shouldBe false
            scene.pixelAt(ContentWidth / 2, ContentHeight / 2) shouldBe Blue.toArgb()
        }
    }

    /**
     * An active overlay displays its indicator above the content, and covers
     * the content's bounds with its background.
     */
    @Test
    fun `display the indicator above the content when it is active`() {
        var indicatorDisplayed = false

        overlayScene(active = true, indicator = { indicatorDisplayed = true }).use { scene ->
            indicatorDisplayed shouldBe true
            scene.pixelAt(ContentWidth / 2, ContentHeight / 2) shouldNotBe Blue.toArgb()
            scene.pixelAt(1.dp, 1.dp) shouldNotBe Blue.toArgb()
            scene.pixelAt(ContentWidth - 1.dp, ContentHeight - 1.dp) shouldNotBe Blue.toArgb()
        }
    }

    /**
     * The indicator is placed in the center of the covered content, rather
     * than in an arbitrary position within it.
     *
     * A custom indicator stands in for the default one here, because the
     * default [androidx.compose.material3.CircularProgressIndicator] is
     * animated, and its indeterminate animation does not advance in the
     * headless scene that this suite renders into — it draws nothing at all,
     * no matter how many frames are rendered. What the default indicator looks
     * like is therefore left to the manual test plan for this feature.
     */
    @Test
    fun `display the indicator in the center of the content`() {
        overlayScene(active = true, indicator = { CenterMarker() }).use { scene ->
            scene.pixelAt(ContentWidth / 2, ContentHeight / 2) shouldBe Green.toArgb()

            scene.pixelAt(MarkerSize, MarkerSize) shouldNotBe Green.toArgb()
            scene.pixelAt(
                ContentWidth - MarkerSize,
                ContentHeight - MarkerSize
            ) shouldNotBe Green.toArgb()
        }
    }

    /**
     * The default background dims the covered content instead of hiding it.
     *
     * An opaque background would produce the same pixel no matter what it
     * covers, so covering two different contents and comparing the result is
     * what tells a semitransparent background from an opaque one, without
     * depending on the theme's colors.
     */
    @Test
    fun `dim the content with a semitransparent background by default`() {
        overlayScene(active = true, contentColor = Blue).use { overBlue ->
            overlayScene(active = true, contentColor = Red).use { overRed ->
                overBlue.pixelAt(1.dp, 1.dp) shouldNotBe overRed.pixelAt(1.dp, 1.dp)
            }
        }
    }

    /**
     * The overlay is not measured along with the content that it covers, so
     * the content is laid out the same way as it is without the overlay, even
     * if the overlay's indicator is bigger than the content.
     */
    @Test
    fun `preserve the content's size when it is active`() {
        val contentSize = IntSize(ContentWidth.value.toInt(), ContentHeight.value.toInt())

        overlayScene(active = false).use { scene ->
            scene.contentSize shouldBe contentSize
        }
        overlayScene(active = true, indicator = { OversizedIndicator() }).use { scene ->
            scene.contentSize shouldBe contentSize
        }
    }

    /**
     * The content that the active overlay covers cannot be operated with
     * a pointing device, and it becomes operable again once the overlay
     * is deactivated.
     */
    @Test
    fun `prevent the covered content from receiving pointer input`() {
        var active by mutableStateOf(false)
        var clicks = 0

        TestScene {
            ProgressOverlay(active) {
                Box(Modifier.size(ContentWidth, ContentHeight).clickable { clicks += 1 })
            }
        }.use { scene ->
            scene.click(ContentWidth / 2, ContentHeight / 2)
            clicks shouldBe 1

            active = true
            scene.render()
            scene.click(ContentWidth / 2, ContentHeight / 2)
            clicks shouldBe 1

            active = false
            scene.render()
            scene.click(ContentWidth / 2, ContentHeight / 2)
            clicks shouldBe 2
        }
    }

    /**
     * A caller that customizes the overlay's appearance gets the background
     * and the indicator that it has specified, and the overlay keeps blocking
     * the covered content.
     */
    @Test
    fun `display the custom background and indicator`() {
        var customIndicatorDisplayed = false
        var clicks = 0

        TestScene {
            ProgressOverlay(
                active = true,
                background = Red,
                indicator = { customIndicatorDisplayed = true }
            ) {
                Box(
                    Modifier
                        .size(ContentWidth, ContentHeight)
                        .background(Blue)
                        .clickable { clicks += 1 }
                )
            }
        }.use { scene ->
            customIndicatorDisplayed shouldBe true
            scene.pixelAt(ContentWidth / 2, ContentHeight / 2) shouldBe Red.toArgb()

            scene.click(ContentWidth / 2, ContentHeight / 2)
            clicks shouldBe 0
        }
    }

    /**
     * Displays a fixed-size content of a recognizable color, covered with
     * an overlay in the given state.
     *
     * @param active Whether the overlay has to be active.
     * @param indicator The content displayed by the overlay.
     * @return The scene that displays the content.
     */
    private fun overlayScene(
        active: Boolean,
        indicator: @Composable () -> Unit = {},
        contentColor: Color = Blue
    ): TestScene = TestScene {
        ProgressOverlay(active, indicator = indicator) {
            Box(Modifier.size(ContentWidth, ContentHeight).background(contentColor))
        }
    }

    /**
     * An indicator that is bigger than the content that this suite covers
     * with an overlay.
     */
    @Composable
    private fun OversizedIndicator() {
        Box(Modifier.size(ContentWidth * 2, ContentHeight * 2).background(Color.Green))
    }

    /**
     * A small indicator of a recognizable color, which marks the position that
     * the overlay places its indicator at.
     */
    @Composable
    private fun CenterMarker() {
        Box(Modifier.size(MarkerSize).background(Green))
    }

    /**
     * The dimensions of the content that this suite covers with an overlay.
     */
    private companion object {

        /**
         * The width of the content that this suite covers with an overlay.
         */
        val ContentWidth: Dp = 200.dp

        /**
         * The height of the content that this suite covers with an overlay.
         */
        val ContentHeight: Dp = 100.dp

        /**
         * The size of the indicator that marks its own placement.
         */
        val MarkerSize: Dp = 10.dp
    }
}
