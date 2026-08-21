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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.chords.core.TestApplication
import io.spine.chords.core.layout.given.DialogSpecEnv.ContentWidth
import io.spine.chords.core.layout.given.DialogSpecEnv.ControlHeight
import io.spine.chords.core.layout.given.DialogSpecEnv.TestDialog
import io.spine.chords.core.layout.given.DialogSpecEnv.dialogScene
import io.spine.chords.core.layout.given.DialogSpecEnv.submittableDialogScene
import java.awt.event.KeyEvent.CTRL_DOWN_MASK
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.KeyEvent.VK_A
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests dialog sizing, interaction blocking, submission, and suppressed requests.
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
            withClue(dialog.javaClass.simpleName) {
                dialog.width shouldBe Dp.Unspecified
                dialog.height shouldBe Dp.Unspecified
            }
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
     * A separately constructed instance cannot observe that its request was
     * suppressed, so closing it must not close or disturb the displayed one.
     */
    @Test
    fun `ignore close on an instance whose display request was suppressed`() {
        val displayedDialog = TestDialog()
        displayedDialog.open()
        val suppressedDialog = TestDialog()

        suppressedDialog.open()
        suppressedDialog.close()

        TestApplication.currentBottomDialog shouldBeSameInstanceAs displayedDialog
        displayedDialog.nestedDialog shouldBe null
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

        handleBlockedKeyEvent(
            keyEvent(KEY_RELEASED, VK_ENTER, CTRL_DOWN_MASK)
        ) { cancellations += 1 } shouldBe true
        handleBlockedKeyEvent(
            keyEvent(KEY_PRESSED, VK_A)
        ) { cancellations += 1 } shouldBe true

        cancellations shouldBe 0
    }

    /**
     * Clears dialog state left by an earlier request-level case.
     */
    @BeforeEach
    fun resetDialogs() {
        TestApplication.closeDialogs()
    }

    /**
     * Installs the application environment required by composed dialog cases.
     */
    private companion object {

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
