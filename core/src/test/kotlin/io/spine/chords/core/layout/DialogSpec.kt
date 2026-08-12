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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import io.spine.chords.core.TestApplication
import io.spine.chords.core.layout.WindowType.LightweightWindow
import io.spine.chords.core.styling.ChordsDimensions
import io.spine.chords.core.styling.ChordsTheme
import java.awt.event.KeyEvent.CTRL_DOWN_MASK
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.KeyEvent.VK_A
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the dialog's sizing properties, and the way that a dialog blocks its
 * content while its submission is in progress.
 */
@DisplayName("`Dialog` should")
internal class DialogSpec {

    /**
     * A dialog that was not given a size detects it from its content.
     */
    @Test
    fun `determine dimensions from content by default`() {
        val dialog = TestDialog()

        dialog.width shouldBe Dp.Unspecified
        dialog.height shouldBe Dp.Unspecified
    }

    /**
     * The dialogs that the library provides are sized from their content as
     * well, since their content is what defines how big they have to be.
     */
    @Test
    fun `determine dimensions of built-in dialogs from content`() {
        val dialogs = listOf(
            ConfirmationDialog(),
            InputTextDialog(),
            MessageDialog()
        )

        dialogs.forEach { dialog ->
            dialog.width shouldBe Dp.Unspecified
            dialog.height shouldBe Dp.Unspecified
        }
    }

    /**
     * A size that a dialog was given explicitly is the one that it keeps.
     */
    @Test
    fun `allow specifying dimensions explicitly`() {
        val dialog = TestDialog().apply {
            width = 600.dp
            height = 400.dp
        }

        dialog.width shouldBe 600.dp
        dialog.height shouldBe 400.dp
    }

    /**
     * Customizing one look value must not detach the remaining default values
     * from the active theme.
     */
    @Test
    fun `resolve default look values from the theme independently`() {
        val dialog = TestDialog().apply {
            look = Dialog.Look(buttonsSpacing = 5.dp)
        }
        lateinit var resolvedLook: Dialog.Look

        TestScene {
            ChordsTheme(
                dimensions = ChordsDimensions(
                    spacingMedium = 14.dp,
                    spacingLarge = 18.dp,
                    spacingXLarge = 30.dp
                )
            ) {
                resolvedLook = dialog.resolvedLook()
            }
        }.use { }

        resolvedLook.padding.calculateLeftPadding(LayoutDirection.Ltr) shouldBe 30.dp
        resolvedLook.titlePadding.calculateBottomPadding() shouldBe 18.dp
        resolvedLook.buttonsPanelPadding.calculateTopPadding() shouldBe 30.dp
        resolvedLook.buttonsSpacing shouldBe 5.dp
    }

    /**
     * The content of a dialog that is not submitting anything is operated
     * as usual, which is what the cases below observe the absence of while
     * the dialog is submitting.
     */
    @Test
    fun `let its content be operated when it is not submitting`() {
        val dialog = TestDialog(submitAvailable = true)

        dialogScene(dialog).use { scene ->
            // The key event is sent before the click, since clicking moves
            // the focus onto the control that has been clicked.
            scene.pressKey(VK_A)
            scene.click(ContentWidth / 2, ControlHeight / 2)

            dialog.contentKeyEvents shouldBe 1
            dialog.contentClicks shouldBe 1
        }
    }

    /**
     * The overlay that a submitting dialog displays above its content has to
     * prevent that content from being edited with a pointing device, and the
     * content has to become operable again once the submission completes.
     */
    @Test
    fun `prevent its content from being clicked while it is submitting`() {
        val dialog = TestDialog(submitAvailable = true)

        dialogScene(dialog).use { scene ->
            dialog.submitting = true
            scene.render()
            scene.click(ContentWidth / 2, ControlHeight / 2)

            dialog.contentClicks shouldBe 0

            dialog.submitting = false
            scene.render()
            scene.click(ContentWidth / 2, ControlHeight / 2)

            dialog.contentClicks shouldBe 1
        }
    }

    /**
     * A key event must not reach the covered content while the dialog is
     * submitting, so that neither the focused editor, nor a keyboard shortcut
     * declared within the content can bypass the overlay.
     */
    @Test
    fun `prevent its content from receiving key events while it is submitting`() {
        val dialog = TestDialog(submitAvailable = true)

        dialogScene(dialog).use { scene ->
            dialog.submitting = true
            scene.render()
            scene.pressKey(VK_A)
            scene.releaseKey(VK_ENTER, CTRL_DOWN_MASK)
            scene.pressKey(VK_ESCAPE)

            dialog.contentKeyEvents shouldBe 0

            dialog.submitting = false
            scene.render()
            scene.pressKey(VK_A)

            dialog.contentKeyEvents shouldBe 1
        }
    }

    /**
     * The submission shortcut is what could otherwise submit a dialog that is
     * already being submitted, while its cancellation has to remain available
     * at any time.
     */
    @Test
    fun `disable the submission shortcut while it is submitting`() {
        val dialog = TestDialog(submitAvailable = true, cancelAvailable = true)

        dialog.submitShortcutEnabled shouldBe true

        dialog.submitting = true

        dialog.submitShortcutEnabled shouldBe false
        dialog.cancelAvailableInternal shouldBe true
    }

    /**
     * The Submit button is disabled while the dialog is submitting, but that
     * only takes effect in the next composition, so the button that the
     * previous frame rendered can still invoke [Dialog.submit]. A custom
     * buttons section can call that method from a control of its own as well,
     * which stays outside the overlay and can remain enabled for the whole
     * submission. The method therefore has to check the live submission state
     * itself.
     */
    @Test
    fun `not submit its content again while it is submitting`() {
        val dialog = TestDialog(submitAvailable = true, cancelAvailable = true)

        submittableDialogScene(dialog).use { scene ->
            dialog.submit()
            scene.render()

            dialog.submissions shouldBe 1

            // The state is changed without an intervening frame, so the Submit
            // button is still the enabled one that was rendered before the
            // submission started.
            dialog.submitting = true
            dialog.submit()
            scene.render()

            dialog.submissions shouldBe 1
        }
    }

    /**
     * Displays the given dialog the way that a real one is displayed, through
     * [Dialog.Content] rather than through its window content alone.
     *
     * This is what gives the dialog the coroutine scope that its submission
     * runs on, which [dialogScene] does not, since that one composes the
     * window content directly to keep the other cases free of a window.
     *
     * @param dialog The dialog to be displayed.
     * @return The scene that displays the dialog.
     */
    private fun submittableDialogScene(dialog: TestDialog): TestScene = TestScene {
        dialog.Content()
    }

    /**
     * The consumed cancellation shortcut is what the dialog cancels itself
     * upon, since letting that event propagate would expose it to the blocked
     * content, which could handle it instead of the dialog.
     */
    @Test
    fun `cancel itself on the cancellation shortcut consumed for the blocked content`() {
        var cancellations = 0

        val escapeConsumed = handleBlockedKeyEvent(
            keyEvent(KEY_PRESSED, VK_ESCAPE)
        ) { cancellations += 1 }

        escapeConsumed shouldBe true
        cancellations shouldBe 1
    }

    /**
     * Every key event other than the cancellation shortcut is consumed as
     * well, and none of them cancels the dialog.
     */
    @Test
    fun `consume the other key events addressed to the blocked content`() {
        var cancellations = 0
        fun consume(event: KeyEvent) = handleBlockedKeyEvent(event) { cancellations += 1 }

        consume(keyEvent(KEY_RELEASED, VK_ENTER, CTRL_DOWN_MASK)) shouldBe true
        consume(keyEvent(KEY_PRESSED, VK_A)) shouldBe true

        cancellations shouldBe 0
    }

    /**
     * Displays the window content of the given dialog the way that
     * a [WindowType] implementation does.
     *
     * @param dialog The dialog whose content is to be displayed.
     * @return The scene that displays the dialog's content.
     */
    private fun dialogScene(dialog: TestDialog): TestScene = TestScene {
        Column {
            dialog.windowContentInternal(DialogContentHeightMode.Natural)
        }
    }

    /**
     * A minimal [Dialog] implementation, whose content counts the interactions
     * that reach it.
     *
     * @param submitAvailable Whether the dialog has to display the Submit
     *   button, and support the submission shortcut.
     * @param cancelAvailable Whether the dialog has to display the Cancel
     *   button, and support the cancellation shortcut.
     */
    private class TestDialog(
        submitAvailable: Boolean = false,
        cancelAvailable: Boolean = false
    ) : Dialog() {

        /**
         * The number of clicks that the dialog's content has received.
         */
        var contentClicks: Int = 0
            private set

        /**
         * The number of key events that the dialog's content has received.
         */
        var contentKeyEvents: Int = 0
            private set

        /**
         * The number of times this dialog's content has been submitted.
         */
        var submissions: Int = 0
            private set

        override val title: String = "Test"

        init {
            this.submitAvailable = submitAvailable
            this.cancelAvailable = cancelAvailable

            // Renders within the test scene, unlike the default desktop
            // window, which would require a display.
            windowType = LightweightWindow()
        }

        @Composable
        override fun contentSection() {
            val focusRequester = remember { FocusRequester() }
            Column {
                Box(
                    Modifier
                        .size(ContentWidth, ControlHeight)
                        .clickable { contentClicks += 1 }
                )
                Box(
                    Modifier
                        .size(ContentWidth, ControlHeight)
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent {
                            contentKeyEvents += 1
                            false
                        }
                )
            }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }

        override suspend fun submitContent() {
            submissions += 1
        }
    }

    /**
     * The dimensions of the content section of the dialog under test, and the
     * environment that composing that dialog requires.
     */
    private companion object {

        /**
         * The width of the content section of the dialog under test.
         */
        val ContentWidth: Dp = 300.dp

        /**
         * The height of each of the two controls that the content section of
         * the dialog under test consists of.
         */
        val ControlHeight: Dp = 100.dp

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
