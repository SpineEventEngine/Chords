/*
 * Copyright 2025, TeamDev. All rights reserved.
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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.spine.chords.core.AbstractComponentSetup
import io.spine.chords.core.appshell.Props
import io.spine.chords.core.primitive.StringField
import kotlinx.coroutines.CompletableDeferred

/**
 * The default number of lines for the input text component.
 */
private const val InputComponentNoOfLines = 3

/**
 * A dialog that allows input of a text value.
 *
 * By default, the dialog is configured to allow multiline text input. However,
 * if [InputTextDialog.noOfTextLines] is set to `1`, it restricts input
 * to a single line.
 *
 * See the [InputTextDialog.inputText] function on how to use the dialog.
 */
public class InputTextDialog : Dialog() {
    public companion object : AbstractComponentSetup({ InputTextDialog() }) {

        /**
         * Displays the input text dialog.
         *
         * Here's a usage example:
         * ```
         *     val profileDescription = InputTextDialog.inputText {
         *         title = "Profile description"
         *         message = "Please enter a few lines about yourself."
         *         description = "This could be a fun fact from your bio," +
         *             " movie preferences, hobbies, etc."
         *         textFieldLabel = "Express yourself in a few lines"
         *     }
         *     if (profileDescription != null) {
         *         // Use `profileDescription` value.
         *     }
         * ```
         *
         * Here is an example demonstrating a single-line input mode:
         * ```
         *     val projectName = InputTextDialog.inputText {
         *         title = "Project name"
         *         message = "Please enter a project name."
         *         noOfTextLines = 1
         *     }
         *     if (projectName != null) {
         *         // Use `projectName` value.
         *     }
         * ```
         *
         * And one more example demonstrating another usage scenario where
         * the dialog functions as a confirmation with the required text input:
         * ```
         *     val rejectionReason = InputTextDialog.inputText {
         *         title = "Confirm rejection"
         *         message = "You are about to reject this purchase request."
         *         description = "Please confirm or cancel if you are not sure."
         *         okButtonText = "Reject"
         *         textFieldLabel = "Rejection reason"
         *     }
         *     if (rejectionReason != null) {
         *         // Use `rejectionReason` value.
         *     }
         * ```
         *
         * @param props A lambda, which configures the input text
         *   dialog's properties.
         * @return An entered text value, if the user closes the dialog by
         *   pressing the submit button, or `null`, if the user cancels
         *   the input.
         */
        public suspend fun inputText(
            props: Props<InputTextDialog>? = null
        ): String? {
            val dialog = create(config = props)
            return dialog.show()
        }
    }

    /**
     * A title of the dialog.
     */
    public override var title: String = "Input text"

    /**
     * A message of the dialog that explains the expected text value
     * to be entered by the user.
     */
    public var message: String = "Please enter a text."

    /**
     * An optional auxiliary text displayed below the [message].
     */
    public var description: String = ""

    /**
     * A label displayed by the input text component that explains
     * the input details, if any.
     */
    public var textFieldLabel: String = ""

    /**
     * A [MutableState] that holds the entered text value.
     */
    private val text: MutableState<String?> = mutableStateOf("")

    /**
     * The initial value of the input text component.
     */
    public var defaultText: String = ""

    /**
     * A number of lines of the input text component.
     *
     * By default, the dialog allows multiline text input. However,
     * if `noOfTextLines` is set to `1`, it restricts input to a single line.
     */
    public var noOfTextLines: Int = InputComponentNoOfLines

    /**
     * The label for the dialog's submit button.
     *
     * The default value is `OK`.
     */
    public var okButtonText: String
        get() = submitButtonText
        set(value) {
            submitButtonText = value
        }

    /**
     * The label for the dialog's cancel button.
     *
     * The default value is `Cancel`.
     */
    public override var cancelButtonText: String
        get() = super.cancelButtonText
        set(value) {
            super.cancelButtonText = value
        }

    init {
        submitAvailable = true
        cancelAvailable = true
        width = 430.dp
        height = 300.dp
    }

    /**
     * Creates the content of the dialog.
     */
    @Composable
    protected override fun contentSection() {
        val textStyle = typography.bodyLarge
        Column {
            Row(
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = message,
                    style = textStyle
                )
            }
            Row(
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = description,
                    style = textStyle
                )
            }
            Row {
                text.value = defaultText
                StringField {
                    label = textFieldLabel
                    multiline = noOfTextLines > 1
                    minLines = noOfTextLines
                    maxLines = noOfTextLines
                    modifier = Modifier.fillMaxSize()
                        .padding(end = 8.dp)
                    value = text
                }
            }
        }
    }

    /**
     * Just closes the dialog since there is no data to submit.
     */
    protected override suspend fun submitContent() {
        close()
    }

    /**
     * Displays the input text dialog.
     *
     * Returns an entered text value, if the user closes the dialog by
     * pressing the submit button, or `null`, if the user cancels the input.
     */
    private suspend fun show(): String? {
        var dialogCancelled = false
        val dialogClosure = CompletableDeferred<Unit>()
        onBeforeSubmit = {
            dialogClosure.complete(Unit)
            true
        }
        onBeforeCancel = {
            dialogCancelled = true
            dialogClosure.complete(Unit)
            true
        }
        open()
        dialogClosure.await()
        return if (dialogCancelled) null
        else text.value
    }
}
