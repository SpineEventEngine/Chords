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

package io.spine.chords.core.layout.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.spine.chords.core.layout.ConfirmationDialog
import io.spine.chords.core.layout.InputTextDialog
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.layout.WindowType.LightweightWindow
import java.awt.EventQueue.invokeAndWait

/**
 * Drives the text input and cancellation lifecycle in the shared off-screen scene.
 */
internal class InputTextDialogScene(private val dialog: InputTextDialog) : AutoCloseable {

    init {
        dialog.windowType = LightweightWindow()
        dialog.width = 480.dp
    }

    /**
     * Renders the production dialog, including its standard buttons.
     */
    private val scene = TestScene { dialog.Content() }

    init {
        render()
    }

    /**
     * Reads the currently displayed text instead of the dialog's internal value.
     */
    val text: String
        get() = inputNode().config[SemanticsProperties.EditableText].text

    /**
     * Edits the production field through its accessibility callback.
     */
    fun enterText(value: String) {
        invokeAndWait {
            checkNotNull(inputNode().config[SemanticsActions.SetText].action)
                .invoke(AnnotatedString(value))
        }
        render()
    }

    /**
     * Activates the standard cancel button.
     */
    fun cancel() {
        clickButton(dialog.cancelButtonText)
    }

    /**
     * Activates the standard submit button.
     */
    fun submit() {
        clickButton(dialog.okButtonText)
    }

    /**
     * Answers the nested discard confirmation through its visible buttons.
     */
    fun confirmDiscard(accept: Boolean) {
        val confirmation = dialog.nestedDialog as ConfirmationDialog
        clickButton(if (accept) confirmation.yesButtonText else confirmation.noButtonText)
    }

    /**
     * Applies pending input and dialog changes.
     */
    fun render() {
        repeat(4) {
            scene.render()
        }
    }

    /**
     * Releases the off-screen rendering resources.
     */
    override fun close() {
        scene.close()
    }

    /**
     * Finds the dialog's single editable field.
     */
    private fun inputNode() = scene.semanticsNodes()
        .single { it.config.getOrNull(SemanticsActions.SetText) != null }

    /**
     * Finds a button using its configured label and activates it with pointer input.
     */
    private fun clickButton(label: String) {
        val button = scene.semanticsNodes()
            .single { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                        node.config.getOrNull(SemanticsProperties.Text)
                            .orEmpty()
                            .any { it.text == label }
            }
        scene.click(button.boundsInRoot.center)
        render()
    }
}
