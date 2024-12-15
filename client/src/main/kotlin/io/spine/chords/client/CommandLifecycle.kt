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
import io.spine.base.RejectionMessage
import io.spine.chords.client.appshell.client
import io.spine.chords.core.appshell.app
import io.spine.chords.core.layout.MessageDialog.Companion.showMessage
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout

/**
 * An object, which is capable of handling different kinds of outcomes that can
 * follow as a result of posting a command.
 */
public open class CommandLifecycle<C : CommandMessage>(private val command: C) {
    private val eventSubscriptions: MutableMap<
            Class<EventMessage>,
            Pair<EventSubscription<*>, suspend (EventMessage) -> Unit>
            > = HashMap()
    private var onTimeout: suspend () -> Unit = ::showTimeoutMessage

    private var timeoutMessage: (C) -> String = { command ->
        "Timed out waiting for an event in response to " +
                "the command ${command.javaClass.simpleName}"
    }

    private var postingErrorMessage: (CommandPostingError) -> String = { error ->
        "An error has occurred when posting or acknowledging " +
                "the command ${error.message}"
    }

    protected fun <E : EventMessage> on(
        event: Class<E>,
        field: EventMessageField,
        fieldValue: Message,
        handler: suspend (E) -> Unit
    ) {
        val eventSubscription = app.client.subscribeToEvent(
            event, field, fieldValue
        ) as EventSubscription<EventMessage>
        val subscription = eventSubscription
        eventSubscriptions[event as Class<EventMessage>] =
            Pair(subscription, handler as suspend (EventMessage) -> Unit)
    }

    protected open fun makeSubscriptions() {}

    public suspend fun post(timeout: Duration = 15.seconds): Boolean {
        val eventReceival = CompletableFuture<EventMessage>()
        for (entry in eventSubscriptions.entries) {
            val subscription = entry.value.first
            subscription.onEvent = { event ->
                eventReceival.complete(event)
            }
        }
        return try {
            app.client.command(command)
            val event = withTimeout(timeout) { eventReceival.await() }
            val eventHandler = eventSubscriptions[event.javaClass]
            check(eventHandler != null)
            eventHandler.second(event)
            event !is RejectionMessage
        } catch (e: CommandPostingError) {
            showPostingError(e)
            false
        } catch (
            @Suppress(
                // A timeout condition is handled by `outcomeHandler`.
                "SwallowedException"
            )
            e: TimeoutCancellationException
        ) {
            onTimeout()
            false
        }
    }

    protected open suspend fun showTimeoutMessage() {
        showMessage(timeoutMessage(command))
    }

    protected open suspend fun showPostingError(error: CommandPostingError) {
        showMessage(postingErrorMessage(error))
    }
}
