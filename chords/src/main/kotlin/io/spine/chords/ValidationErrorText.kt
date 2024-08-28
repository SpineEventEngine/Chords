/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * A validation error text.
 *
 * @param validationError
 *         a [MutableState] that holds the validation error text. If either
 *         the text or the [MutableState] reference itself is `null`, nothing
 *         is added to the composition.
 */
@Composable
public fun ValidationErrorText(validationError: State<String?>? = null) {
    val validationErrorText = validationError?.value
    if (validationErrorText != null) {
        Text(
            validationErrorText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
