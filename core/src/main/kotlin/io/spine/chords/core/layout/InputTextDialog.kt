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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 * The text field receives focus when the dialog opens. Later recompositions
 * preserve the user's chosen focus.
 *
 * The [dirty] property reports changes from the initial text. Applications
 * can use it in [onBeforeCancel] to confirm discarding input. Returning
 * `false` keeps the dialog open and leaves its suspending caller waiting.
 *
 * ```kotlin
 * InputTextDialog.inputText {
 *     onBeforeCancel = {
 *         !dirty || ConfirmationDialog.showConfirmation {
 *             message = "Discard the entered text?"
 *         }
 *     }
 * }
 * ```
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
         * A confirmation can also require a reason with a separate label and hint:
         * ```
         *     val rejectionReason = InputTextDialog.inputText {
         *         title = "Confirm rejection"
         *         message = "You are about to reject this purchase request."
         *         description = "Please confirm or cancel if you are not sure."
         *         okButtonText = "Reject"
         *         textFieldLabel = "Rejection reason"
         *         textFieldHint = "This value is required"
         *         textRequired = true
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
         *   the input. Also returns `null` immediately when another input text
         *   dialog is displayed. The displayed dialog keeps its original
         *   properties, and only its caller receives the eventual input.
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
     * A label identifying the input. It appears above the field when
     * [textFieldHint] is supplied, or inside the field otherwise.
     */
    public var textFieldLabel: String = ""

    /**
     * An optional hint displayed as the field's floating label, with [textFieldLabel]
     * shown separately above it. A `null` value keeps the label inside the field.
     * This is presentation text and does not add validation rules.
     */
    public var textFieldHint: String? = null

    /**
     * Whether submission requires at least one non-whitespace character.
     * Invalid input stays editable and receives an inline error after submission is attempted.
     * The default is `false`; [textFieldHint] remains independently configurable.
     */
    public var textRequired: Boolean by mutableStateOf(false)

    /**
     * A [MutableState] that holds the entered text value.
     */
    private val text: MutableState<String?> = mutableStateOf("")

    /**
     * Reveals required-input feedback only after the user attempts submission.
     */
    private val validationRequested = mutableStateOf(false)

    /**
     * Keeps required-input feedback current as the user edits the field after a failed submission.
     */
    private val textValidationMessage = derivedStateOf {
        if (validationRequested.value && textRequired && text.value.isNullOrBlank()) {
            "Enter a value."
        } else null
    }

    /**
     * The text captured when the field is first displayed.
     */
    private var initialText: String = ""

    /**
     * The entered text on submission, or `null` when the dialog closes without submitting.
     */
    private val result = CompletableDeferred<String?>()

    /**
     * Whether the current input differs from the initially displayed text.
     * Restoring that text clears the flag; whitespace edits still count as changes.
     */
    public val dirty: Boolean
        get() = text.value.orEmpty() != initialText

    /**
     * The initial value of the input text component. Changes after the first
     * composition do not replace the user's input.
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
    }

    /**
     * Applies the configured starting value once, preserving edits across recompositions.
     */
    override fun initialize() {
        super.initialize()
        initialText = defaultText
        text.value = initialText
    }

    /**
     * Creates the content of the dialog.
     */
    @Composable
    protected override fun contentSection() {
        val textStyle = typography.bodyLarge
        Column {
            DialogHeading {
                Column(
                    modifier = Modifier.preferUnwrappedWidth()
                ) {
                    Text(
                        text = message,
                        style = textStyle
                    )
                    if (description.isNotBlank()) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = description,
                                style = textStyle
                            )
                        }
                    }
                }
            }
            if (textFieldHint != null && textFieldLabel.isNotBlank()) {
                SubheaderText(textFieldLabel)
            }
            Row {
                val field = StringField {
                    label = textFieldHint ?: textFieldLabel
                    multiline = noOfTextLines > 1
                    minLines = noOfTextLines
                    maxLines = noOfTextLines
                    modifier = Modifier.fillMaxWidth()
                    value = text
                    externalValidationMessage = textValidationMessage
                }
                LaunchedEffect(field) {
                    field.focus()
                }
            }
        }
    }

    /**
     * Returns valid text after the configured submission check has allowed closing.
     * Missing required text keeps the dialog open and reveals the field's error.
     */
    protected override suspend fun submitContent() {
        validationRequested.value = true
        if (textValidationMessage.value != null) {
            return
        }
        super.close()
        result.complete(text.value.orEmpty())
    }

    /**
     * Resolves an accepted cancellation or an external close without returning input.
     * A refused [onBeforeCancel] check never reaches this method.
     */
    public override fun close() {
        super.close()
        result.complete(null)
    }

    /**
     * Displays the input text dialog.
     *
     * Returns an entered text value, if the user closes the dialog by
     * pressing the submit button, or `null`, if the user cancels the input.
     * Returns `null` immediately if another [InputTextDialog] is displayed,
     * without sharing that dialog's eventual input.
     */
    private suspend fun show(): String? {
        val displayedDialog = openOrGetDisplayed()
        if (displayedDialog !== this) {
            return null
        }
        return result.await()
    }
}
