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

package io.spine.chords.client.layout

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.protobuf.Message
import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.base.EventMessageField
import io.spine.chords.core.appshell.Application
import io.spine.chords.client.CommandConsequences
import io.spine.chords.client.CommandConsequencesScope
import io.spine.chords.client.CommandConsequencesScopeImpl
import io.spine.chords.client.EventSubscription
import io.spine.chords.core.layout.MessageDialog.Companion.showMessage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A specialized type of [CommandConsequences] which simplify specifying
 * command consequences for commands posted by model UIs (such as dialogs
 * or wizards).
 *
 * Unlike [CommandConsequences], `ModalCommandConsequences` adds the following:
 *  - Contains a set of predefined consequences that disable the form while the
 *    command is being posted, and specify default error handlers.
 *    See the implementation of default value for the [predefinedConsequences]
 *    property.
 *
 *    You can change these default command consequences by customizing the
 *    [predefinedConsequences] property.
 *
 *  - Extends the API for declaring consequences (see
 *    [ModalCommandConsequences]) to include such things as an ability to close
 *    the modal UI (e.g., dialog or wizard), change the "posting" state, which
 *    typically affects how the posting status is displayed in the modal UI
 *    (e.g. disables the respective form), and add a commonly useful
 *    [closeOnEvent] function.
 *
 *  - Adds the default timeout of periods equalling [defaultTimeout] for all
 *    event subscriptions automatically. They still can be customized using the
 *    [withTimeout][ModalCommandConsequencesScope.withTimeout] function
 *    if needed.
 *
 * This greatly simplifies how typical consequences are defined for modal UIs,
 * while implicitly supporting all edge cases such as displaying posting
 * progress, and closing of the UI as needed.
 *
 * A typical implementation of command consequences (e.g. for [CommandDialog]
 * and [CommandWizard] components) can be as simple as this:
 * ```
 *     override fun ModalCommandConsequencesScope<ImportItem>.commandConsequences() {
 *         closeOnEvent(
 *             ItemImported::class.java,
 *             ItemImported.Field.itemId(),
 *             command.itemId
 *         )
 *     }
 * ```
 *
 * Technically, the final configuration of command consequences passed to the
 * [Client.postCommand][io.spine.chords.client.Client.postCommand] function will
 * consist of first invoking [predefinedConsequences] followed by
 * invoking the [consequences] lambda.
 *
 * If you need to change [predefinedConsequences] for all
 * [ModalCommandConsequences] instances in the application (and thus change the
 * way how errors and posting progress display is implemented in all modal UIs),
 * you can do this using the [Application]'s
 * [shared defaults][Application.sharedDefaults] feature like this:
 *
 * ```
 *     override fun SharedDefaultsScope.sharedDefaults() {
 *         ModalCommandConsequences::class defaultsTo {
 *             predefinedConsequences = {
 *                 onBeforePost {
 *                     // Custom `onBeforePost` handler.
 *                     posting = true
 *                 }
 *                 onPostServerError {
 *                     // Custom `onPostServerError` handler.
 *                     showMessage("Unexpected server error has occurred.")
 *                     close()
 *                 }
 *                 onNetworkError {
 *                     // Custom `onNetworkError` handler.
 *                     showMessage("Server connection failed.")
 *                     close()
 *                 }
 *                 onDefaultTimeout {
 *                     // Custom `onDefaultTimeout` handler.
 *                     showMessage("The operation takes unexpectedly long to process. " +
 *                             "Please check the status of its execution later.")
 *                     close()
 *                 }
 *             }
 *         }
 *     }
 * ```
 * Note that this way you replace the whole [predefinedConsequences] handler for
 * predefined consequences for all [ModalCommandConsequences] in the
 * application, so a new set of consequences should be sufficient to replace the
 * default one.
 *
 * @param C A type of commands whose consequences are specified.
 *
 * @param consequences A lambda, which uses the [ModalCommandConsequencesScope]
 *   API to define consequences which are possible as a result of posting
 *   commands of tyhpe [C] along with respective handlers.
 * @param postingState A "posting" state of the UI that backs this object.
 * @param close A lambda that closes the UI that backs this object.
 */
@Suppress("UNCHECKED_CAST")
public open class ModalCommandConsequences<C : CommandMessage>(
    consequences: ModalCommandConsequencesScope<C>.() -> Unit,
    private val postingState: MutableState<Boolean>,
    public val close: () -> Unit,
) : CommandConsequences<C>(
    consequences as CommandConsequencesScope<C>.() -> Unit
) {
    override fun createConsequencesScope(command: C): ModalCommandConsequencesScope<C> =
        ModalCommandConsequencesScope(command, postingState, close)

    public var predefinedConsequences: ModalCommandConsequencesScope<C>.() -> Unit = {
        onBeforePost {
            posting = true
        }
        onPostServerError {
            showMessage("Unexpected server error has occurred.")
            close()
        }
        onNetworkError {
            showMessage("Server connection failed.")
            close()
        }
        onDefaultTimeout {
            showMessage("The operation takes unexpectedly long to process. " +
                    "Please check the status of its execution later.")
            close()
        }
    }

    init {
        setDefaultProps()
    }

    override fun registerConsequences(consequencesScope: CommandConsequencesScope<C>) {
        predefinedConsequences(consequencesScope as ModalCommandConsequencesScope<C>)
        super.registerConsequences(consequencesScope)
    }
}

/**
 * A [CommandConsequencesScope] variant, which provides an extended API to
 * simplify implementing typical scenarios of handling consequences
 * of commands posted by modal UIs (such as a dialog or a wizard).
 *
 * Differences from [CommandConsequences]:
 *  - Exposes additional attributes and operations pertaining to the
 *    modal UIs:
 *    - [close] — closes the modal UI that has initiated posting of the command.
 *    - [posting] — controls the modal UI's "posting" flag (which, can
 *      mean disabling the respective form or displaying a graphic
 *      "posting" indicator).
 *  - Adds a [closeOnEvent] function, which addresses a common scenario in modal
 *    UIs where they should be closed upon emitting of a certain event.
 *  - All event subscriptions (created with [onEvent] or [closeOnEvent]) have
 *    timeout periods set up by default with a duration of [defaultTimeout].
 *
 * @param C A type of command whose consequences being specified within
 *   this scope.
 *
 * @param command A command whose consequences are being specified and handled.
 * @param postingState A [MutableState] that will back the [posting] property.
 * @property close A lambda that closes the modal UI (e.g. a dialog or
 *   a wizard).
 * @property defaultTimeout A default event waiting timeout that will be used if
 *   the respective parameter of [withTimeout] is omitted.
 */
public open class ModalCommandConsequencesScope<C : CommandMessage>(
    command: C,
    postingState: MutableState<Boolean>,
    public val close: () -> Unit,
    public override val defaultTimeout: Duration = 30.seconds
) : CommandConsequencesScopeImpl<C>(command, defaultTimeout) {

    /**
     * Controls the "posting" state of the modal component behind this scope.
     *
     * Depending on how the respective [ModalCommandConsequences] is integrated
     * within the modal component, and how the modal component is implemented
     * this can affect whether the modal component's form is enabled or whether
     * some graphic "posting" progress is displayed.
     */
    public var posting: Boolean by postingState

    private var defaultTimeoutHandlers: List<suspend () -> Unit> = ArrayList()

    /**
     * Adds a handler that will be invoked when the default event waiting period
     * [defaultTimeout] expires for any event declared in this scope.
     *
     * This handler is not invoked for event subscriptions that have custom
     * timeout handlers specified using the [withTimeout] function.
     */
    public fun onDefaultTimeout(handler: suspend () -> Unit) {
        defaultTimeoutHandlers += handler
    }

    override fun <E : EventMessage> onEvent(
        eventType: Class<out E>,
        field: EventMessageField,
        fieldValue: Message,
        eventHandler: suspend (E) -> Unit
    ): EventSubscription {
        val subscription = super.onEvent(eventType, field, fieldValue, eventHandler)
        subscription.withTimeout {
            triggerDefaultTimeoutHandlers()
        }
        return subscription
    }

    private suspend fun triggerDefaultTimeoutHandlers() {
        defaultTimeoutHandlers.forEach { it() }
    }

    /**
     * Same as [onEvent] function, which ensures a common scenario of calling
     * the [close] function upon emitting the respective event.
     *
     * The [eventHandler] parameter is optional, and if specified, it is invoked
     * before [close] is called.
     */
    public fun <E : EventMessage> closeOnEvent(
        eventType: Class<out E>,
        field: EventMessageField,
        fieldValue: Message,
        eventHandler: suspend (E) -> Unit = {}
    ): EventSubscription = onEvent(eventType, field, fieldValue) {
        eventHandler(it)
        close()
    }
}
