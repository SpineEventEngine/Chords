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

import androidx.compose.runtime.Composable
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test

internal class WizardSpec {

    @Test
    fun `close the wizard when the cancellation guard allows it`() {
        val wizard = TestWizard()

        runBlocking {
            wizard.requestCancel() shouldBe true
        }

        wizard.closeRequests shouldBe 1
    }

    @Test
    fun `keep the wizard open when the cancellation guard rejects cancellation`() {
        val wizard = TestWizard().apply {
            onBeforeCancel = { false }
        }

        runBlocking {
            wizard.requestCancel() shouldBe false
        }

        wizard.closeRequests shouldBe 0
    }

    @Test
    fun `keep the wizard open while the cancellation guard is pending`() {
        val confirmation = CompletableDeferred<Boolean>()
        val wizard = TestWizard().apply {
            onBeforeCancel = { confirmation.await() }
        }

        runBlocking {
            val cancellation = async { wizard.requestCancel() }
            yield()

            wizard.closeRequests shouldBe 0

            confirmation.complete(true)

            cancellation.await() shouldBe true
        }

        wizard.closeRequests shouldBe 1
    }

    @Test
    fun `close the wizard without consulting the cancellation guard on submission`() {
        var guardInvoked = false
        val wizard = TestWizard().apply {
            onBeforeCancel = {
                guardInvoked = true
                true
            }
        }

        wizard.close()

        wizard.closeRequests shouldBe 1
        guardInvoked shouldBe false
    }

    /**
     * A minimal [Wizard] implementation, which counts the close requests it
     * has made.
     */
    private class TestWizard : Wizard() {

        /**
         * The number of times this wizard has requested its host to close it.
         */
        var closeRequests: Int = 0
            private set

        override val title: String = "Test"

        init {
            onCloseRequest = { closeRequests += 1 }
        }

        override fun createPages(): List<WizardPage> = listOf(TestPage(this))

        override suspend fun submit(): Unit = Unit
    }

    /**
     * A wizard page with no content, which is valid at all times.
     */
    private class TestPage(wizard: Wizard) : AbstractWizardPage(wizard) {

        @Composable
        override fun content(): Unit = Unit

        override fun validate(): Boolean = true
    }
}
