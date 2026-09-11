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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsTheme
import io.spine.chords.core.styling.chordsDarkColorScheme
import io.spine.chords.core.styling.chordsLightColorScheme
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Checks pointer states shared by filled and outlined desktop actions.
 */
@DisplayName("Desktop actions should")
internal class ActionButtonSpec {

    /**
     * A modal window must clear feedback on the covered window's actions even without a mouse exit.
     */
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `clear hover when the parent window becomes inactive`(filled: Boolean) {
        listOf(chordsLightColorScheme(), chordsDarkColorScheme()).forEach { scheme ->
            TestScene {
                ChordsTheme(colorScheme = scheme) {
                    Box(Modifier.size(200.dp, 80.dp).background(scheme.surface)) {
                        Action(filled)
                    }
                }
            }.use { scene ->
                val resting = scene.pixelAt(80.dp, 20.dp)
                scene.movePointerTo(Offset(80F, 20F))
                repeat(30) { scene.render() }
                scene.pixelAt(80.dp, 20.dp) shouldNotBe resting
                scene.setWindowFocused(false)
                repeat(30) { scene.render() }
                scene.pixelAt(80.dp, 20.dp) shouldBe resting
            }
        }
    }

    /**
     * Mouse focus must not leave a gray or blue surface after the pointer moves away.
     */
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `clear pointer feedback after clicking and moving away`(filled: Boolean) {
        val scheme = chordsLightColorScheme()
        TestScene {
            ChordsTheme(colorScheme = scheme) {
                Box(Modifier.size(200.dp, 80.dp).background(scheme.surface)) { Action(filled) }
            }
        }.use { scene ->
            val resting = scene.pixelAt(80.dp, 20.dp)
            scene.click(80.dp, 20.dp)
            scene.movePointerTo(Offset(160F, 60F))
            repeat(30) { scene.render() }
            scene.pixelAt(80.dp, 20.dp) shouldBe resting
        }
    }

    /**
     * Keeps the two public button variants under identical pointer and window conditions.
     */
    @Composable
    private fun Action(filled: Boolean) {
        val modifier = Modifier.size(120.dp, 40.dp)
        if (filled) PrimaryButton({}, modifier) {} else SecondaryButton({}, modifier) {}
    }
}
