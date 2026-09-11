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

package io.spine.chords.core.primitive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsTheme
import io.spine.chords.core.styling.chordsDarkColorScheme
import io.spine.chords.core.styling.chordsLightColorScheme
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_TAB
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies filled-button feedback, readable labels, and keyboard activation.
 */
@DisplayName("`PrimaryButton` should")
internal class PrimaryButtonSpec {

    /**
     * Filled and outlined actions share the same visible top edge in a dialog button row.
     */
    @Test
    fun `align its surface with an outlined action`() {
        val scheme = chordsDarkColorScheme()
        TestScene {
            ChordsTheme(colorScheme = scheme) {
                Row(Modifier.background(scheme.surface)) {
                    PrimaryButton({}, Modifier.width(120.dp)) { Text("Confirm") }
                    SecondaryButton({}, Modifier.width(120.dp)) { Text("Cancel") }
                }
            }
        }.use { scene ->
            val background = scene.pixelAt(0.dp, 0.dp)
            val primaryTop = (0..20).first { scene.pixelAt(60.dp, it.dp) != background }
            val secondaryTop = (0..20).first { scene.pixelAt(180.dp, it.dp) != background }
            primaryTop shouldBe secondaryTop
        }
    }

    /**
     * Hover must visibly change the surface without dimming its label or retaining mouse focus.
     */
    @Test
    fun `show a clear hover surface and restore it after the pointer leaves`() {
        listOf(chordsLightColorScheme(), chordsDarkColorScheme()).forEach { scheme ->
            var clicks = 0
            TestScene {
                ChordsTheme(colorScheme = scheme) {
                    Box(Modifier.size(200.dp, 80.dp)) {
                        PrimaryButton(
                            onClick = { clicks++ },
                            modifier = Modifier.size(120.dp, 40.dp)
                        ) { }
                    }
                }
            }.use { scene ->
                repeat(30) { scene.render() }
                val resting = scene.pixelAt(80.dp, 20.dp)
                scene.movePointerTo(Offset(80F, 20F))
                repeat(30) { scene.render() }
                val hovered = scene.pixelAt(80.dp, 20.dp)
                val luminanceChange = kotlin.math.abs(
                    Color(hovered).luminance() - Color(resting).luminance()
                )
                luminanceChange shouldBeGreaterThanOrEqual 0.045F
                val bright = maxOf(Color(hovered).luminance(), scheme.onPrimary.luminance())
                val dark = minOf(Color(hovered).luminance(), scheme.onPrimary.luminance())
                (bright + 0.05F) / (dark + 0.05F) shouldBeGreaterThanOrEqual 4.5F

                scene.click(80.dp, 20.dp)
                scene.movePointerTo(Offset(160F, 60F))
                repeat(30) { scene.render() }
                clicks shouldBe 1
                scene.pixelAt(80.dp, 20.dp) shouldBe resting
            }
        }
    }

    /**
     * Keyboard focus remains visible, and Enter still invokes the action exactly once.
     */
    @Test
    fun `show keyboard focus and support keyboard activation`() {
        var clicks = 0
        TestScene {
            ChordsTheme(colorScheme = chordsDarkColorScheme()) {
                PrimaryButton(
                    onClick = { clicks++ },
                    modifier = Modifier.size(120.dp, 40.dp)
                ) { }
            }
        }.use { scene ->
            val resting = scene.pixelAt(80.dp, 20.dp)
            scene.pressKey(VK_TAB)
            scene.releaseKey(VK_TAB)
            repeat(30) { scene.render() }
            scene.pixelAt(80.dp, 20.dp) shouldNotBe resting

            scene.pressKey(VK_ENTER)
            scene.releaseKey(VK_ENTER)
            repeat(30) { scene.render() }
            clicks shouldBe 1
        }
    }

    /**
     * Disabled actions must not respond to a pointer click.
     */
    @Test
    fun `leave disabled actions inactive`() {
        var clicks = 0
        val scheme = chordsDarkColorScheme()
        TestScene {
            ChordsTheme(colorScheme = scheme) {
                Box(Modifier.size(200.dp, 80.dp).background(scheme.surface)) {
                    PrimaryButton(
                        onClick = { clicks++ },
                        modifier = Modifier.size(120.dp, 40.dp),
                        enabled = false
                    ) { }
                }
            }
        }.use { scene ->
            val resting = scene.pixelAt(80.dp, 20.dp)
            scene.movePointerTo(Offset(80F, 20F))
            scene.click(80.dp, 20.dp)
            repeat(30) { scene.render() }
            clicks shouldBe 0
            scene.pixelAt(80.dp, 20.dp) shouldBe resting
        }
    }
}
