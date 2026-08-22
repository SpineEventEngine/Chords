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

package io.spine.chords.core.layout.given

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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.spine.chords.core.layout.Dialog
import io.spine.chords.core.layout.DialogContentHeightMode
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.layout.WindowType.LightweightWindow

/**
 * Supplies the dialog and rendered scenes used by `DialogSpec`.
 */
internal object DialogSpecEnv {

    /**
     * The width of the content section of the dialog under test.
     */
    val ContentWidth: Dp = 300.dp

    /**
     * The height of each control in the dialog content section.
     */
    val ControlHeight: Dp = 100.dp

    /**
     * Displays the dialog through [Dialog.Content] so submission has a coroutine scope.
     *
     * @param dialog The dialog to display.
     * @return The scene that displays the dialog.
     */
    fun submittableDialogScene(dialog: TestDialog): TestScene = TestScene {
        dialog.Content()
    }

    /**
     * Displays the window content without creating a desktop window.
     *
     * @param dialog The dialog whose content to display.
     * @return The scene that displays the dialog content.
     */
    fun dialogScene(dialog: TestDialog): TestScene = TestScene {
        Column {
            dialog.windowContentInternal(DialogContentHeightMode.Natural)
        }
    }

    /**
     * A dialog whose content records the interactions that reach it.
     *
     * @param submitAvailable Whether submission is available.
     * @param cancelAvailable Whether cancellation is available.
     */
    class TestDialog(
        submitAvailable: Boolean = false,
        cancelAvailable: Boolean = false
    ) : Dialog() {

        /**
         * The number of clicks received by the dialog content.
         */
        var contentClicks: Int = 0
            private set

        /**
         * The number of key events received by the dialog content.
         */
        var contentKeyEvents: Int = 0
            private set

        /**
         * The number of times the dialog content has been submitted.
         */
        var submissions: Int = 0
            private set

        /**
         * The title used when this fixture is rendered.
         */
        override val title: String = "Test"

        init {
            this.submitAvailable = submitAvailable
            this.cancelAvailable = cancelAvailable

            // Renders within the test scene instead of opening a desktop window.
            windowType = LightweightWindow()
        }

        /**
         * Renders controls that record pointer and keyboard interactions.
         */
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

        /**
         * Records a submission without closing the dialog.
         */
        override suspend fun submitContent() {
            submissions += 1
        }
    }
}
