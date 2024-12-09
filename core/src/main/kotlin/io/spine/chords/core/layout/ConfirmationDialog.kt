/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
import io.spine.chords.core.ComponentProps
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.future.await

/**
 * A dialog that prompts the user either to make a boolean decision
 * (e.g. approve or deny some action).
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
         *         message = "Are you sure that you want to continue?"
         *         description = "This action is irreversible."
         *         submitButtonText = "Proceed"
         *         cancelButtonText = "Cancel"
         *     }
         * ```
         *
         * You can also import this function specifically and make its usages
         * more concise, like this:
         * ```
         *     val confirmed = showConfirmation {
         *         message = "Are you sure that you want to continue?"
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
            props: ComponentProps<ConfirmationDialog>? = null
        ): Boolean {
            val dialog = create(config = props)
            return dialog.showConfirmation()
        }
    }

    /**
     * Initializes the `confirmButtonText` and the size of the dialog.
     */
    init {
        submitButtonText = "Yes"
        cancelButtonText = "No"
        dialogWidth = 430.dp
        dialogHeight = 210.dp
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
     * Creates the content of the dialog.
     */
    @Composable
    protected override fun formContent() {
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
    protected override suspend fun submitForm(): Boolean {
        return true
    }

    /**
     * Displays the confirmation dialog, and waits until the user
     * makes a decision.
     */
    public suspend fun showConfirmation(): Boolean {
        var confirmed = false
        val dialogClosure = CompletableFuture<Unit>()
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
