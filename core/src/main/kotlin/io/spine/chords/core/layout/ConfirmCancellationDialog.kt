/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.core.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.spine.chords.core.ComponentSetup
import io.spine.chords.core.layout.Dialog

/**
 * A confirmation dialog that prompts the user to confirm or not
 * the cancellation of some window, typically used for modal ones.
 */
public class ConfirmCancellationDialog : Dialog() {
    public companion object : ComponentSetup<ConfirmCancellationDialog>(
        { ConfirmCancellationDialog() }
    )

    /**
     * Initializes the `confirmButtonText` and the size of the dialog.
     */
    init {
        confirmButtonText = "Discard Changes"
        cancelButtonText = "Continue Editing"
        dialogWidth = 420.dp
        dialogHeight = 230.dp
    }

    /**
     * The title of the dialog.
     */
    public override val title: String = confirmButtonText

    /**
     * Creates the content of the dialog.
     */
    @Composable
    protected override fun formContent() {
        val textStyle = MaterialTheme.typography.bodyLarge
        Column {
            Row(
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = "Are you sure you want to close the dialog?",
                    style = textStyle
                )
            }
            Row {
                Text(
                    text = "Any entered data will be lost in this case.",
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
}
