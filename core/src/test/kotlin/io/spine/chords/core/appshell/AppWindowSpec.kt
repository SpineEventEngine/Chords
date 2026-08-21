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

package io.spine.chords.core.appshell

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.chords.core.appshell.given.AppWindowSpecEnv.EqualDialog
import io.spine.chords.core.appshell.given.AppWindowSpecEnv.FirstDialog
import io.spine.chords.core.appshell.given.AppWindowSpecEnv.SecondDialog
import io.spine.chords.core.appshell.given.AppWindowSpecEnv.ThirdDialog
import io.spine.chords.core.appshell.given.AppWindowSpecEnv.createWindow
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests how [AppWindow] maintains the stack of displayed dialogs.
 */
@DisplayName("`AppWindow` should")
internal class AppWindowSpec {

    /**
     * Immediate repeated requests must observe the first synchronous stack
     * mutation even when no composition occurs between them.
     */
    @Test
    fun `suppress repeated requests for the same dialog class`() {
        val window = createWindow()
        val firstDialog = FirstDialog()

        val firstOpenResult = window.openDialog(firstDialog)
        val repeatedOpenResult = window.openDialog(FirstDialog())

        firstOpenResult shouldBeSameInstanceAs firstDialog
        repeatedOpenResult shouldBeSameInstanceAs firstDialog
        window.currentBottomDialog shouldBeSameInstanceAs firstDialog
        firstDialog.nestedDialog shouldBe null
    }

    /**
     * Removing the displayed instance must clear class-based suppression for
     * the next request.
     */
    @Test
    fun `display the same dialog class again after close`() {
        val window = createWindow()
        val firstDialog = FirstDialog()
        window.openDialog(firstDialog)
        window.closeDialog(firstDialog)
        val reopenedDialog = FirstDialog()

        val reopenedResult = window.openDialog(reopenedDialog)

        reopenedResult shouldBeSameInstanceAs reopenedDialog
        window.currentBottomDialog shouldBeSameInstanceAs reopenedDialog
    }

    /**
     * Suppression searches the complete stack without affecting arbitrary
     * nesting of distinct concrete classes.
     */
    @Test
    fun `nest distinct dialog classes and suppress matches throughout the stack`() {
        val window = createWindow()
        val firstDialog = FirstDialog()
        val secondDialog = SecondDialog()
        val thirdDialog = ThirdDialog()

        window.openDialog(firstDialog)
        window.openDialog(secondDialog)
        window.openDialog(thirdDialog)

        firstDialog.nestedDialog shouldBeSameInstanceAs secondDialog
        secondDialog.nestedDialog shouldBeSameInstanceAs thirdDialog
        window.openDialog(FirstDialog()) shouldBeSameInstanceAs firstDialog
        window.openDialog(ThirdDialog()) shouldBeSameInstanceAs thirdDialog
        firstDialog.nestedDialog shouldBeSameInstanceAs secondDialog
        secondDialog.nestedDialog shouldBeSameInstanceAs thirdDialog
        thirdDialog.nestedDialog shouldBe null
    }

    /**
     * Suppression of another instance must not weaken the fail-fast contract
     * for the exact bottom instance.
     */
    @Test
    fun `fail when the requested bottom instance is already displayed`() {
        val window = createWindow()
        val firstDialog = FirstDialog()
        window.openDialog(firstDialog)

        shouldThrow<IllegalStateException> {
            window.openDialog(firstDialog)
        }
    }

    /**
     * The fail-fast identity check must apply to an instance at any stack depth.
     */
    @Test
    fun `fail when the requested nested instance is already displayed`() {
        val window = createWindow()
        val firstDialog = FirstDialog()
        val secondDialog = SecondDialog()
        window.openDialog(firstDialog)
        window.openDialog(secondDialog)

        shouldThrow<IllegalStateException> {
            window.openDialog(secondDialog)
        }
    }

    /**
     * Custom equality must not let a separate instance remove the displayed
     * dialog.
     */
    @Test
    fun `close dialogs by reference identity`() {
        val window = createWindow()
        val displayedDialog = EqualDialog()
        window.openDialog(displayedDialog)

        window.closeDialog(EqualDialog())

        window.currentBottomDialog shouldBeSameInstanceAs displayedDialog
    }

    /**
     * Nested-dialog closing must use identity even when another instance is
     * equal according to the displayed instance's custom implementation.
     */
    @Test
    fun `ignore an equals-equal substitute for a nested dialog`() {
        val window = createWindow()
        val parentDialog = FirstDialog()
        val displayedDialog = EqualDialog()
        window.openDialog(parentDialog)
        window.openDialog(displayedDialog)

        window.closeDialog(EqualDialog())

        parentDialog.nestedDialog shouldBeSameInstanceAs displayedDialog
    }
}
