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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import io.spine.chords.core.TestApplication
import java.awt.event.KeyEvent.ALT_DOWN_MASK
import java.awt.event.KeyEvent.CTRL_DOWN_MASK
import java.awt.event.KeyEvent.VK_A
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_LEFT
import java.awt.event.KeyEvent.VK_RIGHT
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the way that a wizard is cancelled, and the way that it blocks its
 * page while its submission is in progress.
 */
@DisplayName("`Wizard` should")
internal class WizardSpec {

    /**
     * A cancellation that the guard permits closes the wizard.
     */
    @Test
    fun `close the wizard when the cancellation guard allows it`() {
        val wizard = TestWizard()

        runBlocking {
            wizard.requestCancel() shouldBe true
        }

        wizard.closeRequests shouldBe 1
    }

    /**
     * A guard that rejects the cancellation keeps the wizard open along with
     * the data that has been entered in it.
     */
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

    /**
     * The guard suspends while a confirmation is displayed, and the wizard
     * cannot be closed before that confirmation is answered.
     */
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

    /**
     * A successful submission closes the wizard directly, so the confirmation
     * that guards the cancellation is not displayed after it.
     */
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
     * The page of a wizard that is not submitting anything is navigated and
     * operated as usual, which is what the cases below observe the absence of
     * while the wizard is submitting.
     */
    @Test
    fun `let its page be operated when it is not submitting`() {
        val wizard = TestWizard()

        wizardScene(wizard).use { scene ->
            scene.releaseKey(VK_RIGHT, ALT_DOWN_MASK)
            scene.render()

            wizard.pageIndex shouldBe 1

            scene.releaseKey(VK_LEFT, ALT_DOWN_MASK)
            scene.render()

            wizard.pageIndex shouldBe 0

            scene.click(wizard.clickTarget)

            wizard.pageClicks shouldBe 1
        }
    }

    /**
     * A key event must not reach the covered page while the wizard is
     * submitting, so that the page's editors cannot be operated with
     * the keyboard.
     */
    @Test
    fun `prevent its page from receiving key events while it is submitting`() {
        val wizard = TestWizard()

        wizardScene(wizard).use { scene ->
            wizard.submittingInternal = true
            scene.render()
            scene.pressKey(VK_A)

            wizard.pageKeyEvents shouldBe 0

            wizard.submittingInternal = false
            scene.render()
            scene.pressKey(VK_A)

            wizard.pageKeyEvents shouldBe 1
        }
    }

    /**
     * The overlay that a submitting wizard displays above its page has to
     * prevent that page from being edited with a pointing device, and the page
     * has to become operable again once the submission completes.
     */
    @Test
    fun `prevent its page from being clicked while it is submitting`() {
        val wizard = TestWizard()

        wizardScene(wizard).use { scene ->
            wizard.submittingInternal = true
            scene.render()
            scene.click(wizard.clickTarget)

            wizard.pageClicks shouldBe 0

            wizard.submittingInternal = false
            scene.render()
            scene.click(wizard.clickTarget)

            wizard.pageClicks shouldBe 1
        }
    }

    /**
     * Navigating away from the page whose submission is in progress would
     * display a page that the wizard is not submitting, so the navigation
     * shortcuts have to be inactive while the wizard is submitting.
     */
    @Test
    fun `not navigate its pages while it is submitting`() {
        val wizard = TestWizard()

        wizardScene(wizard).use { scene ->
            scene.releaseKey(VK_RIGHT, ALT_DOWN_MASK)
            scene.render()
            wizard.submittingInternal = true
            scene.render()

            scene.releaseKey(VK_LEFT, ALT_DOWN_MASK)
            scene.render()

            wizard.pageIndex shouldBe 1

            scene.releaseKey(VK_RIGHT, ALT_DOWN_MASK)
            scene.render()

            wizard.pageIndex shouldBe 1
        }
    }

    /**
     * The submission shortcut is what could otherwise submit a wizard whose
     * submission is already in progress.
     */
    @Test
    fun `not submit its page again while it is submitting`() {
        val wizard = TestWizard()

        wizardScene(wizard).use { scene ->
            scene.releaseKey(VK_RIGHT, ALT_DOWN_MASK)
            scene.render()
            wizard.submittingInternal = true
            scene.render()

            scene.releaseKey(VK_ENTER, CTRL_DOWN_MASK)
            scene.render()

            wizard.submissions shouldBe 0

            wizard.submittingInternal = false
            scene.render()
            scene.releaseKey(VK_ENTER, CTRL_DOWN_MASK)
            scene.render()

            wizard.submissions shouldBe 1
        }
    }

    /**
     * The "Finish" button is disabled while the wizard is submitting, but that
     * only takes effect in the next composition, so the button that the
     * previous frame rendered can still invoke its handler. The handler
     * therefore has to check the live submission state itself.
     */
    @Test
    fun `not submit itself again from the navigation panel while it is submitting`() {
        val wizard = TestWizard()

        wizardScene(wizard).use { scene ->
            scene.releaseKey(VK_RIGHT, ALT_DOWN_MASK)
            scene.render()

            wizard.handleFinishClickInternal()
            scene.render()

            wizard.submissions shouldBe 1

            // The state is changed without an intervening frame, so the
            // "Finish" button is still the enabled one that was rendered
            // before the submission started.
            wizard.submittingInternal = true
            wizard.handleFinishClickInternal()
            scene.render()

            wizard.submissions shouldBe 1
        }
    }

    /**
     * Displays the given wizard the way that its host does.
     *
     * @param wizard The wizard to be displayed.
     * @return The scene that displays the wizard.
     */
    private fun wizardScene(wizard: TestWizard): TestScene = TestScene {
        wizard.Content()
    }

    /**
     * A minimal [Wizard] implementation, which counts the close requests it
     * has made, and the interactions that its pages have received.
     */
    private class TestWizard : Wizard() {

        /**
         * The number of times this wizard has requested its host to close it.
         */
        var closeRequests: Int = 0
            private set

        /**
         * The number of times this wizard has been submitted.
         */
        var submissions: Int = 0
            private set

        /**
         * The number of clicks that the currently displayed page has received.
         */
        var pageClicks: Int = 0
            private set

        /**
         * The number of key events that the currently displayed page
         * has received.
         */
        var pageKeyEvents: Int = 0
            private set

        /**
         * The position that a test has to click at in order to click the
         * clickable area of the currently displayed page.
         */
        var clickTarget: Offset = Offset.Zero
            private set

        /**
         * Exposes the [submitting] property, which is `protected` in [Wizard],
         * so that a test can imitate the submission that a real wizard's
         * implementation reports.
         */
        var submittingInternal: Boolean
            get() = submitting
            set(value) { submitting = value }

        /**
         * The zero-based index of the page that is displayed currently.
         */
        val pageIndex: Int
            get() = pages.indexOf(currentPage)

        override val title: String = "Test"

        init {
            onCloseRequest = { closeRequests += 1 }
        }

        override fun createPages(): List<WizardPage> =
            listOf(TestPage(this), TestPage(this))

        override suspend fun submit() {
            submissions += 1
        }

        /**
         * Renders the content that every page of this wizard displays, which
         * registers the clicks that it receives, and holds the focus that
         * the wizard's keyboard shortcuts require.
         */
        @Composable
        fun PageContent() {
            val focusRequester = remember { FocusRequester() }
            Column {
                Box(
                    Modifier
                        .size(PageContentWidth, PageControlHeight)
                        .onGloballyPositioned {
                            clickTarget = it.boundsInWindow().center
                        }
                        .clickable { pageClicks += 1 }
                )
                Box(
                    Modifier
                        .size(PageContentWidth, PageControlHeight)
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent {
                            pageKeyEvents += 1
                            false
                        }
                )
            }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }

    /**
     * A wizard page, which is valid at all times, and displays the content
     * that the wizard under test observes the interactions with.
     */
    private class TestPage(private val testWizard: TestWizard) : AbstractWizardPage(testWizard) {

        @Composable
        override fun content() {
            testWizard.PageContent()
        }

        override fun validate(): Boolean = true
    }

    /**
     * The dimensions of the page content of the wizard under test, and the
     * environment that composing that wizard requires.
     */
    private companion object {

        /**
         * The width of the content that every page of the wizard under test
         * displays.
         */
        val PageContentWidth: Dp = 300.dp

        /**
         * The height of each of the two controls that the content of every
         * page of the wizard under test consists of.
         */
        val PageControlHeight: Dp = 100.dp

        /**
         * Assigns the application instance that a composed component requires.
         */
        @JvmStatic
        @BeforeAll
        fun setUpApplication() {
            TestApplication.install()
        }
    }
}
