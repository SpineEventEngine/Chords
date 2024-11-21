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
 * A confirmation dialog that prompts the user to confirm or deny
 * the cancellation of some window, typically used for modal ones.
 */
public class ConfirmationDialog : Dialog() {
    public companion object : AbstractComponentSetup({ ConfirmationDialog() }) {

        /**
         * Displays the confirmation dialog, and waits until the user either
         * accepts or rejects the confirmation.
         */
        public suspend fun askAndAwait(props: ComponentProps<ConfirmationDialog>? = null): Boolean {
            val dialog = create(config = props)
            return dialog.askAndWait()
        }
    }

    /**
     * Initializes the `confirmButtonText` and the size of the dialog.
     */
    init {
        confirmButtonText = "Yes"
        cancelButtonText = "No"
        dialogWidth = 420.dp
        dialogHeight = 230.dp
    }

    /**
     * The title of the dialog.
     */
    public override var title: String = "Confirm"

    public var message: String = "Are you sure?"
    public var description: String = ""

    internal var onConfirm: (() -> Unit)? = null
    internal var onCancel: (() -> Unit)? = null

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

    override suspend fun handleCancelClick() {
        onCancel?.invoke()
        super.handleCancelClick()
    }

    override suspend fun  handleSubmitClick() {
        onConfirm?.invoke()
        super.handleSubmitClick()
    }

    /**
     * Just returns `true` on form submission since there is no data to submit.
     */
    protected override suspend fun submitForm(): Boolean {
        return true
    }

    /**
     * Displays the confirmation dialog, and waits until the user either
     * accepts or rejects the confirmation.
     */
    public suspend fun askAndWait(): Boolean {
        var confirmed = false
        val dialogClosure = CompletableFuture<Unit>()
        onConfirm = {
            confirmed = true
            dialogClosure.complete(Unit)
        }
        onCancel = {
            dialogClosure.complete(Unit)
        }

        open()
        dialogClosure.await()

        return confirmed
    }
}
