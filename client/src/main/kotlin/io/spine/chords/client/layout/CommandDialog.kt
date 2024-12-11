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

package io.spine.chords.client.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.chords.client.EventSubscription
import io.spine.chords.client.form.CommandMessageForm
import io.spine.chords.core.layout.Dialog
import io.spine.chords.core.layout.SubmitOrCancelDialog
import io.spine.chords.proto.form.FormFieldsScope
import io.spine.chords.proto.form.FormPartScope
import io.spine.chords.proto.form.ValidationDisplayMode.MANUAL
import io.spine.protobuf.ValidatingBuilder

/**
 * A [Dialog] designed to create or modify command messages,
 * and post the respective commands upon finish.
 *
 * @param C A type of the command message constructed in the dialog.
 * @param B A type of the command message builder.
 */
public abstract class CommandDialog<C : CommandMessage, B : ValidatingBuilder<C>>
    : SubmitOrCancelDialog() {

    /**
     * The [CommandMessageForm] used as a container for the message
     * field editors.
     */
    private lateinit var commandMessageForm: CommandMessageForm<C>

    /**
     * Creates and renders the [commandMessageForm], and then delegates the
     * rendering of the actual form's content to the [content] method.
     */
    @Composable
    protected final override fun contentSection() {
        commandMessageForm = CommandMessageForm(
            ::createCommandBuilder,
            onBeforeBuild = ::beforeBuild,
            props = {
                validationDisplayMode = MANUAL
                eventSubscription = ::subscribeToEvent
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = CenterHorizontally
            ) {
                content()
            }
        }

        LaunchedEffect(Unit) {
            commandMessageForm.focus()
        }
    }

    /**
     * The composable content that should include the fields
     * ([Field][FormFieldsScope.Field] declarations) for editing
     * the command message of type [C].
     */
    @Composable
    protected abstract fun FormPartScope<C>.content()

    /**
     * Creates a builder for the command message of type [C].
     *
     * May pre-fill the builder with the default values,
     * or those required in terms of the domain language,
     * but not available for modification within the dialog UI.
     */
    protected abstract fun createCommandBuilder(): B

    /**
     * A function, which, given a command message that is about to be posted,
     * should subscribe to a respective event that is expected to arrive in
     * response to handling that command.
     *
     * @param command A command, which is going to be posted.
     * @return A subscription to the event that is expected to arrive in
     *   response to handling [command].
     */
    protected abstract fun subscribeToEvent(command: C):
            EventSubscription<out EventMessage>

    /**
     * Allows to programmatically amend the command message builder before
     * the command is built.
     *
     * This function is invoked upon every attempt to build the command edited
     * in the dialog. When this function is invoked, the command builder's
     * fields have already been set from all form's field editors,
     * which currently have valid values.
     */
    protected open fun beforeBuild(builder: B) {}

    /**
     * Posts the command message [C] created in this dialog.
     */
    protected override suspend fun submitContent(): Boolean {
        return commandMessageForm.postCommand()
    }
}
