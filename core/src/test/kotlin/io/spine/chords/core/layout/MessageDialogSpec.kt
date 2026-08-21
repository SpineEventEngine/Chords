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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.chords.core.TestApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests how [MessageDialog] resolves repeated suspending requests.
 */
@DisplayName("`MessageDialog` should")
internal class MessageDialogSpec {

    /**
     * A repeated message returns before the displayed message closes and
     * cannot replace the text configured by the first request.
     */
    @Test
    fun `discard a suppressed message and return immediately`() {
        runBlocking {
            withTimeout(5_000) {
                val firstResult = async {
                    MessageDialog.showMessage("first")
                }
                yield()
                val displayedDialog = TestApplication.currentBottomDialog
                    .shouldBeInstanceOf<MessageDialog>()

                MessageDialog.showMessage("second")

                displayedDialog.message shouldBe "first"
                displayedDialog.onBeforeSubmit() shouldBe true
                displayedDialog.close()
                firstResult.await()
            }
        }
    }

    /**
     * Failing to display the same object twice must not disconnect the first
     * caller from the dismissal that it is already awaiting.
     */
    @Test
    fun `preserve the first request after the same instance fails to reopen`() {
        runBlocking {
            withTimeout(5_000) {
                val dialog = MessageDialog()
                val firstResult = async {
                    dialog.showMessage()
                }
                yield()

                shouldThrow<IllegalStateException> {
                    runBlocking {
                        dialog.showMessage()
                    }
                }
                dialog.onBeforeSubmit() shouldBe true
                dialog.close()

                firstResult.await()
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
