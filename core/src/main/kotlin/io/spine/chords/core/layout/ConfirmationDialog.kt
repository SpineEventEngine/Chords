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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.spine.chords.core.AbstractComponentSetup
import io.spine.chords.core.appshell.Props
import kotlinx.coroutines.CompletableDeferred

/**
 * A dialog that prompts the user either to make a boolean decision
 * (e.g. approve or deny some action).
 *
 * Use the [ConfirmationDialog.showConfirmation] function to display the
 * confirmation dialog and wait for the user's decision:
 *
 * ```
 *     val confirmed = ConfirmationDialog.showConfirmation {
 *         message = "Are you sure you want to continue?"
 *         description = "This action is irreversible."
 *     }
 * ```
 *
 * You can also import this function specifically and make its usages
 * more concise, like this:
 * ```
 *     val confirmed = showConfirmation {
 *         message = "Are you sure you want to continue?"
 *     }
 * ```
 */
public class ConfirmationDialog : Dialog() {
    public companion object : AbstractComponentSetup({ ConfirmationDialog() }) {

        /**
         * Displays the confirmation dialog, and waits until the user
         * makes a decision.
         *
         * Here's a usage example:
         * ```
         *     val confirmed = ConfirmationDialog.showConfirmation {
         *         message = "Are you sure you want to continue?"
         *         description = "This action is irreversible."
         *         yesButtonText = "Proceed"
         *         noButtonText = "Cancel"
         *     }
         * ```
         *
         * You can also import this function specifically and make its usages
         * more concise, like this:
         * ```
         *     val confirmed = showConfirmation {
         *         message = "Are you sure you want to continue?"
         *     }
         * ```
         *
         * @param props A lambda, which configures the confirmation
         *   dialog's properties.
         * @return `true`, if the user makes a positive decision (presses the
         *   Submit button), and `false`, if the user makes a negative decision
         *   (presses the Cancel button).
         */
        public suspend fun showConfirmation(
            props: Props<ConfirmationDialog>? = null
        ): Boolean {
            val dialog = create(config = props)
            return dialog.showConfirmation()
        }
    }

    /**
     * The title of the dialog.
     */
    public override var title: String = "Confirm"

    /**
     * The main message of the confirmation dialog, which is usually expected
     * to contain the question presented to the user.
     */
    public var message: String = "Are you sure?"

    /**
     * An optional auxiliary text displayed below the message, which can provide
     * an extra context about the question being asked.
     */
    public var description: String = ""

    /**
     * The label for the dialog's Yes button.
     *
     * The default value is `Yes`.
     */
    public var yesButtonText: String
        get() = submitButtonText
        set(value) { submitButtonText = value }

    /**
     * The label for the dialog's No button.
     *
     * The default value is `No`.
     */
    public var noButtonText: String
        get() = cancelButtonText
        set(value) { cancelButtonText = value }

    init {
        submitAvailable = true
        cancelAvailable = true

        yesButtonText = "Yes"
        noButtonText = "No"
        width = 430.dp
        height = 210.dp
    }

    /**
     * Creates the content of the dialog.
     */
    @Composable
    protected override fun contentSection() {
        val textStyle = typography.bodyLarge

        Column {
            Row(
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = message,
                    style = textStyle
                )
            }
            Row {
                Text(
                    text = description,
                    style = textStyle
                )
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
     * Displays the confirmation dialog, and waits until the user
     * makes a decision.
     */
    public suspend fun showConfirmation(): Boolean {
        var confirmed = false
        val dialogClosure = CompletableDeferred<Unit>()
        onBeforeSubmit = {
            confirmed = true
            dialogClosure.complete(Unit)
            true
        }
        onBeforeCancel = {
            dialogClosure.complete(Unit)
            true
        }

        open()
        dialogClosure.await()
        return confirmed
    }
}
