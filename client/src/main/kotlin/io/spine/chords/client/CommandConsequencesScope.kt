/*
 * Copyright 2025, TeamDev. All rights reserved.
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
 * Defines a DSL for registering handlers of command consequences.
 */
public interface CommandConsequencesScope<out C: CommandMessage> {

    /**
     * The command whose consequences are being specified and processed in
     * this scope.
     */
    public val command: C

    /**
     * Registers the callback, which is invoked before the command is posted.
     *
     * @param handler A callback, to be invoked before the command is posted.
     */
    public fun onBeforePost(handler: suspend () -> Unit)

    /**
     * Registers the callback, which is invoked if a network communication
     * failure occurs when posting the command.
     *
     * The fact of invoking this callback doesn't signify whether the command
     * has been acknowledged or not, and the handler should assume that either
     * of these cases could have happened.
     *
     * @param handler A callback to be invoked, whose [ServerCommunicationException] parameter
     *   holds the exception that has signaled the failure.
     */
    public fun onNetworkError(handler: suspend (ServerCommunicationException) -> Unit)

    /**
     * Registers the callback, which is invoked if an error occurred on the
     * server before the command has been acknowledged.
     *
     * @param handler A callback to be invoked, whose [ServerError] parameter
     *   holds the exception that has signaled the failure.
     */
    public fun onPostServerError(handler: suspend (ServerError) -> Unit)

    /**
     * Registers the callback, which is invoked when the server has acknowledged
     * the command.
     *
     * @param handler A callback to be invoked, whose [ServerCommunicationException] parameter
     *   holds the exception that has signaled the failure.
     */
    public fun onAcknowledge(handler: suspend () -> Unit)

    /**
     * Subscribes to an event of type [eventType], which has its [field] equal
     * to [fieldValue].
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
        eventHandler: suspend (EventMessage) -> Unit
    ): EventSubscription<out EventMessage>

    /**
     * Limits the time of waiting for the event to [timeout].
     *
     * If the event is not emitted during [timeout] since this method is invoked
     * then [timeoutHandler] is invoked, and the event ceases to be waited for.
     *
     * @param timeout A maximum period of time that the event is waited since
     *   invoking this function.
     * @param timeoutHandler A callback, which should be invoked if an event
     *   is not emitted within [timeout] after invoking this method.
     */
    public fun EventSubscription<out EventMessage>.withTimeout(
        timeout: Duration = 20.seconds,
        timeoutHandler: suspend () -> Unit)
}

/**
 * An implementation of [CommandConsequencesScope].
 *
 * @param command A command whose consequences are being configured.
 * @param coroutineScope [CoroutineScope] that should be used for launching
 *   suspending event handlers.
 */
internal class CommandConsequencesScopeImpl<out C: CommandMessage>(
    override val command: C,
    private val coroutineScope: CoroutineScope
) : CommandConsequencesScope<C> {

    /**
     * Allows to manage subscriptions made in this scope.
     */
    val subscriptions: EventSubscriptions = object : EventSubscriptions {
        override fun cancelAll() {
            eventSubscriptions.forEach { it.cancel() }
        }
    }

    private val eventSubscriptions: MutableList<EventSubscription<out EventMessage>> = ArrayList()
    private var beforePostHandlers: List<suspend () -> Unit> = ArrayList()
    private var postNetworkErrorHandlers: List<suspend (ServerCommunicationException) -> Unit> =
        ArrayList()
    private var postServerErrorHandlers: List<suspend (ServerError) -> Unit> = ArrayList()
    private var acknowledgeHandlers: List<suspend () -> Unit> = ArrayList()

    override fun onBeforePost(handler: suspend () -> Unit) {
        beforePostHandlers += handler
    }

    override fun onNetworkError(handler: suspend (ServerCommunicationException) -> Unit) {
        postNetworkErrorHandlers += handler
    }

    override fun onPostServerError(handler: suspend (ServerError) -> Unit) {
        postServerErrorHandlers += handler
    }

    override fun onAcknowledge(handler: suspend () -> Unit) {
        acknowledgeHandlers += handler
    }

    override fun onEvent(
        eventType: Class<out EventMessage>,
        field: EventMessageField,
        fieldValue: Message,
        eventHandler: suspend (EventMessage) -> Unit
    ): EventSubscription<out EventMessage> {
        val subscription = app.client.onEvent(
            eventType, field, fieldValue, {
                coroutineScope.launch {
                    triggerNetworkErrorHandlers(ServerCommunicationException(it))
                }
            }, {
                coroutineScope.launch { eventHandler(it) }
            })
        eventSubscriptions += subscription
        return subscription
    }

    override fun EventSubscription<out EventMessage>.withTimeout(
        timeout: Duration,
        timeoutHandler: suspend () -> Unit
    ) = withTimeout(timeout, coroutineScope, timeoutHandler)

    internal suspend fun triggerBeforePostHandlers() {
        beforePostHandlers.forEach { it() }
    }

    internal val allActive: Boolean get() = eventSubscriptions.all { it.active }

    internal suspend fun triggerAcknowledgeHandlers() {
        acknowledgeHandlers.forEach { it() }
    }

    internal suspend fun triggerNetworkErrorHandlers(e: ServerCommunicationException) {
        subscriptions.cancelAll()
        if (postNetworkErrorHandlers.isEmpty()) {
            throw IllegalStateException(
                "No `onNetworkError` handlers are registered for command: " +
                        command.javaClass.simpleName,
                e
            )
        }
        postNetworkErrorHandlers.forEach { it(e) }
    }

    internal suspend fun triggerServerErrorHandlers(e: ServerError) {
        subscriptions.cancelAll()
        if (postServerErrorHandlers.isEmpty()) {
            throw IllegalStateException(
                "No `onPostServerError` handlers are registered for command: " +
                        command.javaClass.simpleName,
                e
            )
        }
        postServerErrorHandlers.forEach { it(e) }
    }
}
