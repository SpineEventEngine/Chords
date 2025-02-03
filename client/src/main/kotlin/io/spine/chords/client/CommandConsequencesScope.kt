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

package io.spine.chords.client

import com.google.protobuf.Message
import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.base.EventMessageField
import io.spine.chords.client.appshell.client
import io.spine.chords.core.appshell.app
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Defines a DSL for registering handlers of command consequencesю
 */
public interface CommandConsequencesScope<C: CommandMessage> {

    /**
     * The command whose outcomes are being specified in this scope.
     */
    public val command: C

    public fun onBeforePost(handler: suspend (C) -> Unit)
    public fun onPostStreamingError(handler: suspend (C, StreamingError) -> Unit)
    public fun onPostServerError(handler: suspend (C, ServerError) -> Unit)
    public fun onAcknowledge(handler: suspend (C) -> Unit)


    /**
     * Subscribes to an event of type [eventType], which has its [field] equal
     * to [fieldValue], and registers it as one of the possible outcomes.
     *
     * @param eventType A type of event that should be subscribed to.
     * @param field A field whose value should identify an event being
     *   subscribed to.
     * @param fieldValue A value of event's [field] that identifies an event
     *   being subscribed to.
     */
    public fun onEvent(
        eventType: Class<out EventMessage>,
        field: EventMessageField,
        fieldValue: Message,
        timeout: Duration = 20.seconds,
        timeoutHandler: (suspend (C) -> Unit)? = null,
        eventHandler: suspend (EventMessage) -> Unit
    )
}

internal class CommandConsequencesScopeImpl<C: CommandMessage>(
    override val command: C,
    private val coroutineScope: CoroutineScope
) : CommandConsequencesScope<C> {

    var beforePostHandlers: List<suspend (C) -> Unit> = ArrayList()
    var postStreamingErrorHandlers: List<suspend (C, StreamingError) -> Unit> =
        ArrayList()
    var postServerErrorHandlers: List<suspend (C, ServerError) -> Unit> = ArrayList()
    var acknowledgeHandlers: List<suspend (C) -> Unit> = ArrayList()

    override fun onBeforePost(handler: suspend (C) -> Unit) {
        beforePostHandlers += handler
    }

    override fun onPostStreamingError(handler: suspend (C, StreamingError) -> Unit) {
        postStreamingErrorHandlers += handler
    }

    override fun onPostServerError(handler: suspend (C, ServerError) -> Unit) {
        postServerErrorHandlers += handler
    }

    override fun onAcknowledge(handler: suspend (C) -> Unit) {
        acknowledgeHandlers += handler
    }

    override fun onEvent(
        eventType: Class<out EventMessage>,
        field: EventMessageField,
        fieldValue: Message,
        timeout: Duration,
        timeoutHandler: (suspend (C) -> Unit)?,
        eventHandler: suspend (EventMessage) -> Unit
    ) {
        app.client.subscribeToEvent(eventType, field, fieldValue, { eventMessage ->
            coroutineScope.launch { eventHandler(eventMessage) }
        }, {
            coroutineScope.launch { timeoutHandler?.invoke(command) }
        }, timeout)
    }
}
