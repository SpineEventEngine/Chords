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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.chords.core.TestApplication
import io.spine.chords.core.layout.WindowType.LightweightWindow
import io.spine.chords.core.layout.testing.InputTextDialogScene
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests text retention, discard checks, and results of suspending input requests.
 */
@DisplayName("`InputTextDialog` should")
internal class InputTextDialogSpec {

    /**
     * A suppressed input request receives no text, while submitting the single
     * displayed dialog resumes only the first request with its entered value.
     */
    @Test
    fun `return null immediately for a suppressed input request`() {
        runBlocking {
            withTimeout(5_000) {
                val firstRequest = async {
                    InputTextDialog.inputText()
                }
                yield()
                val displayedDialog = TestApplication.currentBottomDialog
                    .shouldBeInstanceOf<InputTextDialog>()

                val suppressedResult = InputTextDialog.inputText()

                suppressedResult shouldBe null
                TestApplication.currentBottomDialog shouldBeSameInstanceAs displayedDialog
                InputTextDialogScene(displayedDialog).use { scene ->
                    scene.submit()
                    firstRequest.await() shouldBe ""
                }
            }
        }
    }

    /**
     * A refused cancellation keeps the request suspended and its edited input intact.
     */
    @Test
    fun `honor cancellation veto before resolving the request`(): Unit = runBlocking {
        withTimeout(5_000) {
            var allowCancel = false
            var cancellations = 0
            val request = async {
                InputTextDialog.inputText {
                    onBeforeCancel = {
                        cancellations += 1
                        allowCancel
                    }
                }
            }
            yield()
            val dialog = TestApplication.currentBottomDialog
                .shouldBeInstanceOf<InputTextDialog>()
            InputTextDialogScene(dialog).use { scene ->
                scene.enterText("Draft instructions")

                scene.cancel()

                cancellations shouldBe 1
                request.isCompleted shouldBe false
                TestApplication.currentBottomDialog shouldBeSameInstanceAs dialog
                scene.text shouldBe "Draft instructions"

                allowCancel = true
                scene.cancel()

                cancellations shouldBe 2
                request.await() shouldBe null
                TestApplication.currentBottomDialog shouldBe null
            }
        }
    }

    /**
     * A dialog recomposition must not restore the initial text over the user's edits.
     */
    @Test
    fun `preserve edited text when its prompt changes`(): Unit = runBlocking {
        withTimeout(5_000) {
            var prompt by mutableStateOf("Initial prompt")
            val request = async {
                InputTextDialog.inputText {
                    message = prompt
                    defaultText = "Initial value"
                    textFieldLabel = "Details"
                    textFieldHint = "This value is optional"
                }
            }
            yield()
            val dialog = TestApplication.currentBottomDialog
                .shouldBeInstanceOf<InputTextDialog>()
            InputTextDialogScene(dialog).use { scene ->
                scene.enterText("Edited value")

                prompt = "Updated prompt"
                scene.render()

                scene.text shouldBe "Edited value"
                scene.submit()
                request.await() shouldBe "Edited value"
            }
        }
    }

    /**
     * Required input must remain editable after a failed submission and accept a later correction.
     */
    @ParameterizedTest
    @ValueSource(strings = ["", " \t\n", "\u00a0"])
    fun `retain blank required text until corrected`(blank: String): Unit = runBlocking {
        withTimeout(5_000) {
            val request = async {
                InputTextDialog.inputText {
                    textRequired = true
                }
            }
            yield()
            val dialog = TestApplication.currentBottomDialog
                .shouldBeInstanceOf<InputTextDialog>()
            InputTextDialogScene(dialog).use { scene ->
                scene.enterText(blank)
                scene.hasValidationError shouldBe false

                scene.submit()

                TestApplication.currentBottomDialog shouldBeSameInstanceAs dialog
                request.isCompleted shouldBe false
                scene.hasValidationError shouldBe true
                scene.text shouldBe blank

                scene.enterText("A supplied reason")

                scene.hasValidationError shouldBe false
                scene.submit()
                request.await() shouldBe "A supplied reason"
                TestApplication.currentBottomDialog shouldBe null
            }
        }
    }

    /**
     * Unchanged or restored input closes directly, including fields that normalize empty to null.
     */
    @ParameterizedTest
    @ValueSource(strings = ["", "Initial value"])
    fun `track edits against the initially displayed text`(initial: String): Unit = runBlocking {
        withTimeout(5_000) {
            var discardChecks = 0
            val request = async {
                InputTextDialog.inputText {
                    defaultText = initial
                    onBeforeCancel = {
                        if (dirty) {
                            discardChecks += 1
                            false
                        } else true
                    }
                }
            }
            yield()
            val dialog = TestApplication.currentBottomDialog
                .shouldBeInstanceOf<InputTextDialog>()
            InputTextDialogScene(dialog).use { scene ->
                dialog.dirty shouldBe false
                scene.text shouldBe initial

                scene.enterText(initial + " ")
                scene.cancel()

                dialog.dirty shouldBe true
                discardChecks shouldBe 1
                request.isCompleted shouldBe false

                scene.enterText(initial)
                scene.cancel()

                dialog.dirty shouldBe false
                discardChecks shouldBe 1
                request.await() shouldBe null
            }
        }
    }

    /**
     * Refusing cancellation must leave submission usable with the same text.
     */
    @Test
    fun `submit the preserved input after refusing cancellation`(): Unit = runBlocking {
        withTimeout(5_000) {
            var submissions = 0
            val request = async {
                InputTextDialog.inputText {
                    onBeforeCancel = { false }
                    onBeforeSubmit = {
                        submissions += 1
                        submissions > 1
                    }
                }
            }
            yield()
            val dialog = TestApplication.currentBottomDialog
                .shouldBeInstanceOf<InputTextDialog>()
            InputTextDialogScene(dialog).use { scene ->
                scene.enterText("Keep this text")
                scene.cancel()
                scene.submit()

                submissions shouldBe 1
                request.isCompleted shouldBe false
                scene.text shouldBe "Keep this text"

                scene.submit()

                submissions shouldBe 2
                request.await() shouldBe "Keep this text"
                TestApplication.currentBottomDialog shouldBe null
            }
        }
    }

    /**
     * A real nested confirmation preserves edits when declined and resolves cancellation once.
     */
    @Test
    fun `confirm discarding edited text before closing`(): Unit = runBlocking {
        withTimeout(5_000) {
            val request = async {
                InputTextDialog.inputText {
                    onBeforeCancel = {
                        !dirty || ConfirmationDialog.showConfirmation {
                            message = "Discard the entered text?"
                            windowType = LightweightWindow()
                        }
                    }
                }
            }
            yield()
            val dialog = TestApplication.currentBottomDialog
                .shouldBeInstanceOf<InputTextDialog>()
            InputTextDialogScene(dialog).use { scene ->
                scene.enterText("Keep until discard is confirmed")
                scene.cancel()

                dialog.nestedDialog.shouldBeInstanceOf<ConfirmationDialog>()
                request.isCompleted shouldBe false

                scene.confirmDiscard(accept = false)

                dialog.nestedDialog shouldBe null
                scene.text shouldBe "Keep until discard is confirmed"
                request.isCompleted shouldBe false

                scene.cancel()
                scene.confirmDiscard(accept = true)

                request.await() shouldBe null
                TestApplication.currentBottomDialog shouldBe null
            }
        }
    }

    /**
     * Clears dialog state left by an earlier request-level case.
     */
    @BeforeEach
    fun resetDialogs() {
        TestApplication.closeDialogs()
    }

    /**
     * Installs the unrendered application used by dialog requests.
     */
    private companion object {

        /**
         * Initializes the shared application UI before this suite runs.
         */
        @JvmStatic
        @BeforeAll
        fun setUpApplication() {
            TestApplication.install()
        }
    }
}
