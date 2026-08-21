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
 * Tests how [ConfirmationDialog] resolves repeated suspending requests.
 */
@DisplayName("`ConfirmationDialog` should")
internal class ConfirmationDialogSpec {

    /**
     * One positive user decision must authorize only the request whose dialog
     * was displayed, while a suppressed request receives no decision.
     */
    @Test
    fun `return false immediately for a suppressed confirmation`() {
        runBlocking {
            withTimeout(5_000) {
                val firstResult = async {
                    ConfirmationDialog.showConfirmation()
                }
                yield()
                val displayedDialog = TestApplication.currentBottomDialog
                    .shouldBeInstanceOf<ConfirmationDialog>()

                val repeatedResult = ConfirmationDialog.showConfirmation()

                repeatedResult shouldBe false
                displayedDialog.onBeforeSubmit() shouldBe true
                displayedDialog.close()
                firstResult.await() shouldBe true
            }
        }
    }

    /**
     * A failed request for the displayed object must leave the callbacks of
     * its first request connected to the result that caller is awaiting.
     */
    @Test
    fun `preserve the first request after the same instance fails to reopen`() {
        runBlocking {
            withTimeout(5_000) {
                val dialog = ConfirmationDialog()
                val firstResult = async {
                    dialog.showConfirmation()
                }
                yield()

                shouldThrow<IllegalStateException> {
                    runBlocking {
                        dialog.showConfirmation()
                    }
                }
                dialog.onBeforeSubmit() shouldBe true
                dialog.close()

                firstResult.await() shouldBe true
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
