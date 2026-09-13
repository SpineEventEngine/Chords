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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsTheme
import io.spine.chords.core.styling.chordsDarkColorScheme
import io.spine.chords.core.styling.chordsLightColorScheme
import java.awt.event.KeyEvent.VK_TAB
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies that keyboard focus remains visible without leaving mouse actions highlighted.
 */
@DisplayName("`CircularIconButton` should")
internal class CircularIconButtonSpec {

    /**
     * A mouse click must not leave the keyboard focus surface behind after the pointer exits.
     */
    @Test
    fun `clear its highlight after a mouse click and pointer exit`() {
        val scheme = chordsDarkColorScheme()
        var clicks = 0
        TestScene {
            ChordsTheme(colorScheme = scheme) {
                Box(Modifier.size(120.dp).background(scheme.surface)) {
                    CircularIconButton(onClick = { clicks++ }) { }
                }
            }
        }.use { scene ->
            scene.movePointerTo(Offset(20f, 20f))
            scene.click(20.dp, 20.dp)
            scene.movePointerTo(Offset(80f, 80f))
            repeat(30) { scene.render() }

            clicks shouldBe 1
            scene.pixelAt(20.dp, 20.dp) shouldBe scheme.surface.toArgb()
        }
    }

    /**
     * A light theme uses a blue hover surface that clears when a modal window covers the action.
     */
    @Test
    fun `use a blue hover surface only in an active window`() {
        val scheme = chordsLightColorScheme()
        TestScene {
            ChordsTheme(colorScheme = scheme) {
                Box(Modifier.size(120.dp).background(scheme.surface)) { CircularIconButton({}) {} }
            }
        }.use { scene ->
            scene.movePointerTo(Offset(20f, 20f))
            repeat(30) { scene.render() }
            scene.pixelAt(20.dp, 20.dp) shouldBe scheme.primaryContainer.toArgb()
            scene.setWindowFocused(false)
            scene.render()
            scene.pixelAt(20.dp, 20.dp) shouldBe scheme.surface.toArgb()
        }
    }

    /**
     * Tab navigation must still show which action will receive a keyboard command.
     */
    @Test
    fun `retain a visible keyboard focus indicator`() {
        val scheme = chordsDarkColorScheme()
        TestScene {
            ChordsTheme(colorScheme = scheme) {
                Box(Modifier.size(120.dp).background(scheme.surface)) {
                    CircularIconButton(onClick = {}) { }
                }
            }
        }.use { scene ->
            scene.pressKey(VK_TAB)
            scene.releaseKey(VK_TAB)
            repeat(30) { scene.render() }

            scene.pixelAt(20.dp, 20.dp) shouldNotBe scheme.surface.toArgb()
        }
    }
}
