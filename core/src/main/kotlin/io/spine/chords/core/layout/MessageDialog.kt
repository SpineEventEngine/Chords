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
 * A dialog that displays the specified text message for the user.
 */
public class MessageDialog : Dialog() {
    public companion object : AbstractComponentSetup({ MessageDialog() }) {

        /**
         * Displays the text message dialog, and waits until the user
         * dismisses it.
         *
         * Here's a usage example:
         * ```
         *     MessageDialog.showMessage("An error has occurred: ...")
         * ```
         *
         * You can make its usage more concise if you import this function:
         * ```
         *     showMessage("An error has occurred: ...")
         * ```
         */
        public suspend fun showMessage(
            message: String,
            props: ComponentProps<MessageDialog>? = null
        ) {
            val dialog = create(config = props)
            dialog.message = message
            dialog.showMessage()
        }
    }

    init {
        submitAvailable = true
        dialogWidth = 430.dp
        dialogHeight = 210.dp
    }

    /**
     * The title of the dialog.
     */
    public override var title: String = "Message"

    /**
     * The main message of the dialog, which is usually expected
     * to contain the question presented to the user.
     */
    public var message: String = ""

    init {
        cancelAvailable = false
    }

    public suspend fun showMessage() {
        val dialogClosure = CompletableFuture<Unit>()
        onBeforeSubmit = {
            dialogClosure.complete(Unit)
            true
        }

        open()
        dialogClosure.await()
    }

    override suspend fun submitContent(): Boolean {
        // No custom logic is required when the user acknowledges
        // the displayed message.
        return true
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
        }
    }

}
