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

package io.spine.chords.core.styling

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.chords.core.layout.TestScene
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the public Chords theme contract.
 */
@DisplayName("`ChordsTheme` should")
internal class ChordsThemeSpec {

    /**
     * Theme inputs supplied by an application must reach all descendants.
     */
    @Test
    fun `install application overrides`() {
        val expectedDimensions = ChordsDimensions(
            controlHeight = 48.dp,
            supportingPaneWidth = 420.dp
        )
        val expectedInteraction = ChordsInteraction(hoveredStateAlpha = 0.12f)
        val expectedScheme = chordsDarkColorScheme()
        lateinit var observedDimensions: ChordsDimensions
        lateinit var observedInteraction: ChordsInteraction
        var observedPrimary = Color.Unspecified

        TestScene {
            ChordsTheme(
                colorScheme = expectedScheme,
                dimensions = expectedDimensions,
                interaction = expectedInteraction
            ) {
                observedDimensions = ChordsTheme.dimensions
                observedInteraction = ChordsTheme.interaction
                observedPrimary = MaterialTheme.colorScheme.primary
            }
        }.use { }

        observedDimensions shouldBe expectedDimensions
        observedInteraction shouldBe expectedInteraction
        observedPrimary shouldBe expectedScheme.primary
    }

    /**
     * The default density must remain compact enough for desktop forms and
     * data grids.
     */
    @Test
    fun `provide compact desktop dimensions`() {
        val dimensions = ChordsDimensions()

        dimensions.controlHeight shouldBe 44.dp
        dimensions.dropdownItemHeight shouldBe 40.dp
        dimensions.tableRowHeight shouldBe 40.dp
        dimensions.tableRowMaxHeight shouldBe 100.dp
        dimensions.navigationItemHeight shouldBe 40.dp
    }

    /**
     * Light and dark palettes must provide distinct semantic surfaces and
     * accents instead of tinting a single palette.
     */
    @Test
    fun `provide distinct light and dark palettes`() {
        val light = chordsLightColorScheme()
        val dark = chordsDarkColorScheme()

        light.background shouldNotBe dark.background
        light.surface shouldNotBe dark.surface
        light.primary shouldNotBe dark.primary
    }

    /**
     * Text and icons outside a Surface used to inherit black in a dark theme.
     */
    @Test
    fun `provide a readable foreground outside surfaces and honor nested surfaces`() {
        val scheme = chordsDarkColorScheme()
        var uncontained = Color.Unspecified
        var contained = Color.Unspecified

        TestScene {
            ChordsTheme(colorScheme = scheme) {
                uncontained = LocalContentColor.current
                Surface(color = scheme.primary, contentColor = scheme.onPrimary) {
                    contained = LocalContentColor.current
                }
            }
        }.use { }

        uncontained shouldBe scheme.onSurface
        contained shouldBe scheme.onPrimary
    }

    /**
     * Dark overlays need lighter surfaces and readable content independent of their shadows.
     */
    @Test
    fun `separate dark overlays and retain readable hint text`() {
        val scheme = chordsDarkColorScheme()
        var overlay = Color.Unspecified

        TestScene {
            ChordsTheme(colorScheme = scheme) {
                overlay = ChordsTheme.overlayColor
            }
        }.use { }

        overlay.luminance() shouldBeGreaterThan scheme.surface.luminance()
        assertTextContrast(scheme.onSurface, overlay)
        assertTextContrast(scheme.onSurfaceVariant, overlay)
        assertTextContrast(scheme.primary, overlay)
        assertTextContrast(scheme.onPrimary, scheme.primary)
        assertTextContrast(scheme.onSurfaceVariant, scheme.surface)
    }

    /**
     * Light overlays stay neutral and retain their surface foreground.
     */
    @Test
    fun `retain a light surface for light overlays`() {
        val scheme = chordsLightColorScheme()
        var overlay = Color.Unspecified

        TestScene {
            ChordsTheme(colorScheme = scheme) {
                overlay = ChordsTheme.overlayColor
            }
        }.use { }

        overlay shouldBe scheme.surface
        assertTextContrast(scheme.onSurface, overlay)
        assertTextContrast(scheme.onSurfaceVariant, overlay)
        assertTextContrast(scheme.primary, overlay)
        assertTextContrast(scheme.onPrimary, scheme.primary)
        assertTextContrast(scheme.onSurfaceVariant, scheme.surface)
    }

    /**
     * Disabled controls retain readable text in forms and native dialogs in either theme.
     */
    @Test
    fun `retain readable disabled content on surfaces and overlays`() {
        listOf(chordsLightColorScheme(), chordsDarkColorScheme()).forEach { scheme ->
            var disabled = Color.Unspecified
            var overlay = Color.Unspecified
            TestScene {
                ChordsTheme(colorScheme = scheme) {
                    disabled = ChordsTheme.disabledContentColor
                    overlay = ChordsTheme.overlayColor
                }
            }.use { }

            assertTextContrast(disabled.compositeOver(scheme.surface), scheme.surface)
            assertTextContrast(disabled.compositeOver(overlay), overlay)
        }
    }

    /**
     * Light input outlines and scrollbar thumbs must remain distinguishable from the page.
     */
    @Test
    fun `keep light control outlines and scrollbars visible`() {
        val scheme = chordsLightColorScheme()
        var scrollbarThumb = Color.Unspecified
        TestScene {
            ChordsTheme(colorScheme = scheme) {
                scrollbarThumb = LocalScrollbarStyle.current.unhoverColor
            }
        }.use { }

        listOf(scheme.outline, scrollbarThumb).forEach { foreground ->
            val contrast = (scheme.background.luminance() + 0.05f) /
                    (foreground.luminance() + 0.05f)
            contrast shouldBeGreaterThanOrEqual 3f
        }
        assertTextContrast(scheme.onSurfaceVariant, scheme.background)
    }

    /**
     * Ordinary text must preserve a contrast ratio of at least 4.5 to one.
     */
    private fun assertTextContrast(foreground: Color, background: Color) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val dark = minOf(foreground.luminance(), background.luminance())
        (light + 0.05f) / (dark + 0.05f) shouldBeGreaterThanOrEqual 4.5f
    }

}
