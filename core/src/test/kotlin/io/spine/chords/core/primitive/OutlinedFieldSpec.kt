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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsTheme
import io.spine.chords.core.styling.chordsDarkColorScheme
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_END
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Checks validation rendering without interrupting the native editor's focus and selection.
 */
@DisplayName("`OutlinedField` should")
internal class OutlinedFieldSpec {

    /**
     * Focused fields retain a thin outline in both normal and validation states.
     */
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `keep a focused outline thin`(error: Boolean) {
        val scheme = chordsDarkColorScheme()
        TestScene {
            ChordsTheme(colorScheme = scheme) {
                Box(Modifier.size(320.dp, 120.dp).background(scheme.surface)) {
                    OutlinedField(
                        value = TextFieldValue("invalid"),
                        onValueChange = {},
                        modifier = Modifier.width(280.dp),
                        label = { Text("Domain") },
                        isError = error
                    )
                }
            }
        }.use { scene ->
            scene.click(120.dp, 32.dp)
            repeat(30) { scene.render() }
            val borderColor = if (error) scheme.error else scheme.primary
            scene.pixelAt(200.dp, 8.dp) shouldBe borderColor.toArgb()
            scene.pixelAt(200.dp, 9.dp) shouldBe scheme.surface.toArgb()
        }
    }

    /**
     * Validation must not replace the editor and lose focus while the user corrects a value.
     */
    @Test
    fun `continue editing when validation changes`() {
        val value = mutableStateOf(TextFieldValue("abc", TextRange(3)))
        TestScene {
            ChordsTheme {
                OutlinedField(
                    value = value.value,
                    onValueChange = { value.value = it },
                    label = { Text("Domain") },
                    isError = value.value.text.length < 3
                )
            }
        }.use { scene ->
            scene.click(120.dp, 32.dp)
            scene.pressKey(VK_END)
            scene.releaseKey(VK_END)
            scene.pressKey(VK_BACK_SPACE)
            scene.releaseKey(VK_BACK_SPACE)
            repeat(30) { scene.render() }
            value.value.text shouldBe "ab"
            scene.pressKey(VK_BACK_SPACE)
            scene.releaseKey(VK_BACK_SPACE)
            scene.render()
            value.value.text shouldBe "a"
        }
    }
}
