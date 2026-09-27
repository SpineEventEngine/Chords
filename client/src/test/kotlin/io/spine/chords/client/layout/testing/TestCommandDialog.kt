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

package io.spine.chords.client.layout.testing

import androidx.compose.runtime.Composable
import io.spine.base.CommandMessage
import io.spine.chords.client.layout.CommandDialog
import io.spine.chords.client.layout.ModalCommandConsequencesScope
import io.spine.chords.proto.form.FormPartScope
import io.spine.protobuf.ValidatingBuilder

/**
 * Exposes submission eligibility without rendering a form or posting commands.
 */
internal class TestCommandDialog :
    CommandDialog<CommandMessage, ValidatingBuilder<CommandMessage>>() {

    /**
     * Identifies the fixture if it is inspected during a failed test.
     */
    override val title: String = "Command editor"

    /**
     * Reports the eligibility used by all submission entry points.
     */
    val submissionEnabled: Boolean
        get() = submitEnabled

    /**
     * Fails if a property-only test unexpectedly tries to build a command.
     */
    override fun createCommandBuilder(): ValidatingBuilder<CommandMessage> =
        error("This fixture does not render a command form.")

    /**
     * Supplies no editors because these tests do not compose the dialog.
     */
    @Composable
    override fun FormPartScope<CommandMessage>.content() = Unit

    /**
     * Registers no subscriptions because these tests do not post commands.
     */
    override fun ModalCommandConsequencesScope<CommandMessage>.commandConsequences() = Unit
}
