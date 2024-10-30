/*
 * Copyright 2024, TeamDev. All rights reserved.
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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key.Companion.Enter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.spine.chords.core.Component
import io.spine.chords.core.keyboard.KeyModifiers.Companion.Ctrl
import io.spine.chords.core.keyboard.key
import io.spine.chords.core.keyboard.on
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The base class for creating a dialog box for editing and submitting data.
 *
 * Note that an [onCloseRequest] callback is triggered when the user has
 * finished using the dialog, and it needs to be closed. The container where
 * the dialog is placed is responsible for hiding the dialog (excluding it from
 * the composition) upon this event.
 */
public abstract class Dialog : Component() {

    /**
     * The text to be the title of the dialog.
     */
    public abstract val title: String

    /**
     * The label for the dialog's confirmation button.
     *
     * The default value is `OK`.
     */
    public open var confirmButtonText: String = "OK"

    /**
     * The label for the dialog's cancel button.
     *
     * The default value is `Cancel`.
     */
    public open var cancelButtonText: String = "Cancel"

    /**
     * A callback that should be handled to close the dialog (exclude it from
     * the composition).
     *
     * This callback is triggered when the user closes the dialog or after
     * successful submission.
     */
    public open var onCloseRequest: (() -> Unit)? = null

    /**
     * A callback used to confirm that the user wants to cancel the dialog.
     *
     * This callback is triggered when the user closes the dialog
     * by pressing the cancel button.
     */
    public open var onCancelConfirmation: (() -> Unit)? = null

    /**
     * The width of the dialog.
     *
     * The default value is `700.dp`.
     */
    public open var dialogWidth: Dp = 700.dp

    /**
     * The height of the dialog.
     *
     * The default value is `450.dp`.
     */
    public open var dialogHeight: Dp = 450.dp

    /**
     * Creates the form content the dialog consists.
     */
    @Composable
    protected abstract fun formContent()

    /**
     * Submits the dialog's form.
     *
     * This action is executed when the user submits the dialog
     * by pressing the confirmation button.
     *
     * `onCloseRequest` is triggerred right after the `submitForm` action,
     * so it is not needed to configure it manually.
     */
    protected abstract suspend fun submitForm(): Boolean

    /**
     * Creates the dialog content composition.
     */
    @Composable
    override fun content() {
        val coroutineScope = rememberCoroutineScope()
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .size(dialogWidth, dialogHeight)
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(all = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DialogTitle(title)
                Column(
                    Modifier
                        .weight(1F)
                        .on(Ctrl(Enter.key).up) {
                            submit(coroutineScope)
                        }
                ) {
                    formContent()
                }
                Buttons(coroutineScope)
            }
        }
    }

    /**
     * The panel with control buttons of the dialog.
     */
    @Composable
    private fun Buttons(coroutineScope: CoroutineScope) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            Row(
                modifier = Modifier.padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DialogButton(cancelButtonText) {
                    if (onCancelConfirmation != null) {
                        onCancelConfirmation?.invoke()
                    } else {
                        onCloseRequest?.invoke()
                    }
                }
                DialogButton(confirmButtonText)
                {
                    submit(coroutineScope)
                }
            }
        }
    }

    /**
     * Submits the dialog form and invokes [onCloseRequest]
     * if the form was successfully submitted.
     */
    private fun submit(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            if (submitForm()) {
                onCloseRequest?.invoke()
            }
        }
    }
}

/**
 * The title of the dialog.
 *
 * @param text The text to be title.
 */
@Composable
private fun DialogTitle(
    text: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge
    )
}

/**
 * The action button of the dialog.
 *
 * @param label The label of the button.
 * @param onClick The callback triggered on the button click.
 */
@Composable
private fun DialogButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label)
        }
    }
}
