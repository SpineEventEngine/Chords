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

package io.spine.chords.proto.money

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.spine.chords.proto.TestApplication
import io.spine.chords.proto.form.ValidationDisplayMode.MANUAL
import io.spine.chords.proto.form.given.MessageFormFixtures.inScene
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks that payment-method validation preserves the surrounding form layout.
 */
@DisplayName("`PaymentMethodEditor` should")
internal class PaymentMethodEditorSpec {

    /**
     * A missing selection must not grow a content-sized dialog after it opens.
     * Clearing and validating again must preserve the same reserved space.
     */
    @Test
    fun `keep its height when showing and clearing a missing-method error`() {
        val editor = PaymentMethodEditor()
            .apply {
                value = mutableStateOf(null)
                validationDisplayMode = MANUAL
            }
        var height = 0
        inScene({
            Box(
                Modifier
                    .width(584.dp)
                    .onSizeChanged { height = it.height }
            ) {
                editor.Content()
            }
        }) { scene ->
            val initialHeight = height
            val initialText = scene.displayedText()
            initialHeight shouldBeGreaterThan 0

            editor.updateValidationDisplay(focusInvalidPart = false)
            scene.render()
            val errorText = scene.displayedText() - initialText
            errorText shouldHaveSize 1
            editor.valid.value shouldBe false
            height shouldBe initialHeight

            editor.clear()
            scene.render()
            scene.displayedText() shouldBe initialText
            height shouldBe initialHeight

            editor.updateValidationDisplay(focusInvalidPart = false)
            scene.render()
            scene.displayedText() - initialText shouldBe errorText
            height shouldBe initialHeight
        }
    }

    /**
     * Installs the component defaults used by the off-screen form.
     */
    private companion object {

        /**
         * Initializes the shared application before composing an editor.
         */
        @JvmStatic
        @BeforeAll
        fun setUpApplication() {
            TestApplication.install()
        }
    }
}
