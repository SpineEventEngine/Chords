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

import androidx.compose.runtime.mutableStateOf
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsTheme
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Verifies how [InputField] reserves space for external validation messages.
 */
@DisplayName("`InputField` should")
internal class InputFieldSpec {

    /**
     * Forms retain their reserved row; compact filters reclaim it after an error clears.
     */
    @ParameterizedTest(name = "reserveValidationSpace = {0}")
    @ValueSource(booleans = [false, true])
    fun `show errors and reserve empty validation space only when enabled`(reserveSpace: Boolean) {
        TestApplication.install()
        val validation = mutableStateOf<String?>(null)
        val field = InputField<String>()
            .apply {
                label = "Filter"
                value = mutableStateOf(null)
                reserveValidationSpace = reserveSpace
                externalValidationMessage = validation
            }

        TestScene {
            ChordsTheme { field.Content() }
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
