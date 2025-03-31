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
 * Default value of the input component visible text lines.
 */
private const val InputTextComponentVisibleLines = 3

/**
 * A dialog that allows input of a text value.
 *
 * Use the `InputTextDialog.inputText` function to display the dialog:
 *
 * ```
 *     val enteredText: MutableState<String?> = mutableStateOf(null)
 *     val valueSubmitted = InputTextDialog.inputText {
 *         message = "Please enter a text."
 *         value = enteredText
 *     }
 *     if (valueSubmitted) {
 *         // Read `enteredText.value` to get the entered value.
 *     }
 * ```
 */
public class InputTextDialog : Dialog() {
    public companion object : AbstractComponentSetup({ InputTextDialog() }) {

        /**
         * Displays the input text dialog.
         *
         * Here's a usage example:
         * ```
         *     val enteredText: MutableState<String?> = mutableStateOf(null)
         *     val valueSubmitted = InputTextDialog.inputText {
         *         message = "Please enter a text."
         *         value = enteredText
         *     }
         *     if (valueSubmitted) {
         *         // Read `enteredText.value` to get the entered value.
         *     }
         * ```
         *
         * @param props A lambda, which configures the input text
         *   dialog's properties.
         * @return `true`, if the user closes the dialog by presses the
         *   Submit button, and `false`, if the user cancels the input.
         */
        public suspend fun inputText(
            props: Props<InputTextDialog>? = null
        ): Boolean {
            val dialog = create(config = props)
            return dialog.inputText()
        }
    }

    /**
     * The title of the dialog.
     */
    public override var title: String = "Input Text"

    /**
     * The main message of the dialog that explains
     * the expected text value from the user.
     */
    public var message: String = "Please enter a text value."

    /**
     * An optional auxiliary text displayed below the message.
     */
    public var description: String = ""

    /**
     * A text label displayed by the input component
     * that explains the input details, if any.
     */
    public var valueLabel: String = ""

    /**
     * A [MutableState] that holds the entered text value.
     *
     * A value of `null` corresponds to the empty input.
     */
    public var value: MutableState<String?> = mutableStateOf(null)

    /**
     * A value of the input component visible text lines.
     */
    public var visibleTextLines: Int = InputTextComponentVisibleLines

    /**
     * The label for the dialog's submit button.
     *
     * The default value is `OK`.
     */
    public override var submitButtonText: String
        get() = super.submitButtonText
        set(value) {
            super.submitButtonText = value
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
                StringField {
                    label = valueLabel
                    multiline = true
                    minLines = visibleTextLines
                    maxLines = visibleTextLines
                    modifier = Modifier.fillMaxSize()
                        .padding(end = 8.dp)
                    value = this@InputTextDialog.value
                }
            }
        }
    }

    /**
     * Just returns `true` on form submission since there is no data to submit.
     */
    protected override suspend fun submitContent() {
        close()
    }

    /**
     * Displays the input text dialog.
     *
     * @return `true`, if the user closes the dialog by presses the
     *   submit button, and `false`, if the user cancels the input.
     */
    public suspend fun inputText(): Boolean {
        var valueSubmitted = false
        val dialogClosure = CompletableDeferred<Unit>()
        onBeforeSubmit = {
            valueSubmitted = true
            dialogClosure.complete(Unit)
            true
        }
        onBeforeCancel = {
            dialogClosure.complete(Unit)
            true
        }
        open()
        dialogClosure.await()
        return valueSubmitted
    }
}
