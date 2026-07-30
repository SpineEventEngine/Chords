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

package io.spine.chords.client.layout

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.protobuf.Message
import io.spine.base.CommandMessage
import io.spine.base.EventMessage
import io.spine.base.EventMessageField
import io.spine.chords.client.Client
import io.spine.chords.client.CommandConsequences
import io.spine.chords.client.CommandConsequencesScope
import io.spine.chords.client.CommandConsequencesScopeImpl
import io.spine.chords.client.EventSubscription
import io.spine.chords.client.ServerCommunicationException
import io.spine.chords.core.appshell.Application
import io.spine.chords.core.layout.MessageDialog.Companion.showMessage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

/**
 * A specialized type of [CommandConsequences] that simplifies specifying
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
 * By default, a network communication error clears the posting state, displays
 * [networkErrorMessage], and keeps the modal UI open. Chords does not retry the
 * command automatically because the server may have accepted it before the
 * connection failed. Applications should verify the operation status before
 * allowing a manual retry.
 *
 * Network error handling can be customized for all
 * `ModalCommandConsequences` instances using the [Application]'s
 * [shared defaults][Application.sharedDefaults] feature:
 *
 * ```
 *     override fun SharedDefaultsScope.sharedDefaults() {
 *         ModalCommandConsequences::class defaultsTo {
 *             networkErrorMessage = "The server is temporarily unavailable."
 *             networkErrorPresentation = { error ->
 *                 showMessage(
 *                     if (error.acknowledgementReceived) {
 *                         "Connection lost while waiting for the operation result."
 *                     } else {
 *                         networkErrorMessage
 *                     }
 *                 )
 *             }
 *         }
 *     }
 * ```
 *
 * The same properties can be supplied for one [CommandDialog] or
 * [CommandWizard] through its `commandConsequencesProps`.
 *
 * Replacing [predefinedConsequences] remains available for broader
 * customization. Doing so opts out of the focused network error properties,
 * acknowledgement tracking, and Chords' duplicate presentation guard because
 * the replacement defines the complete predefined behavior.
 *
 * @param C A type of commands whose consequences are specified.
 *
 * @param postingState A "posting" state of the UI that backs this object.
 * @param close A lambda that closes the UI that backs this object.
 * @param consequences A lambda, which uses the [ModalCommandConsequencesScope]
 *   API to define consequences that are possible as a result of posting
 *   commands of type [C] along with respective handlers.
 */
@Suppress("UNCHECKED_CAST")
public open class ModalCommandConsequences<C : CommandMessage>(
    private val postingState: MutableState<Boolean>,
    public val close: () -> Unit,
    consequences: ModalCommandConsequencesScope<C>.() -> Unit
) : CommandConsequences<C>(
    consequences as CommandConsequencesScope<C>.() -> Unit
) {
    override fun createConsequencesScope(command: C): ModalCommandConsequencesScope<C> =
        ModalCommandConsequencesScope(command, postingState, close, defaultTimeout)

    /**
     * Specifies whether a network communication error closes the modal UI
     * after [networkErrorPresentation] finishes.
     *
     * The default is `false`, so the UI remains open and preserves its form
     * data. Setting this property to `true` restores the previous close
     * request. A [CommandWizard] closes only when its host handles
     * [onCloseRequest][io.spine.chords.core.layout.Wizard.onCloseRequest].
     *
     * When a custom [networkErrorPresentation] opens nested modal UI, it must
     * not return until that UI is dismissed. Otherwise, closing a
     * [CommandDialog] while its nested dialog is open can fail.
     */
    public var closeOnNetworkError: Boolean = false

    /**
     * The text displayed by the default [networkErrorPresentation].
     *
     * The message warns against immediately repeating a command because a
     * communication failure can leave the operation status unknown.
     */
    public var networkErrorMessage: String =
        "Server connection failed. Please try again when the connection is restored."

    /**
     * Presents a network communication error to the user.
     *
     * Chords resets the posting state before invoking this callback and applies
     * [closeOnNetworkError] after it returns. The callback customizes
     * presentation only and cannot bypass either lifecycle operation.
     *
     * If this callback opens nested modal UI while [closeOnNetworkError] is
     * `true`, it must suspend until that UI is dismissed.
     */
    public var networkErrorPresentation: suspend (ModalCommandNetworkError) -> Unit = {
        showMessage(networkErrorMessage)
    }

    /**
     * Defines the common consequences registered before component-specific
     * consequences.
     *
     * Replacing this property defines the complete predefined behavior and
     * therefore opts out of [closeOnNetworkError], [networkErrorMessage],
     * [networkErrorPresentation], acknowledgement tracking, and duplicate
     * presentation and close handling by Chords.
     */
    public var predefinedConsequences: ModalCommandConsequencesScope<C>.() -> Unit = {
        val acknowledgementReceived = AtomicBoolean(false)
        val networkErrorHandled = AtomicBoolean(false)

        onBeforePost {
            posting = true
        }
        onAcknowledge {
            acknowledgementReceived.set(true)
        }
        onServerError {
            showMessage("Unexpected server error has occurred.")
            close()
        }
        onNetworkError { error ->
            if (!networkErrorHandled.compareAndSet(false, true)) {
                return@onNetworkError
            }
            posting = false
            networkErrorPresentation(
                ModalCommandNetworkError(error, acknowledgementReceived.get())
            )
            if (closeOnNetworkError) {
                close()
            }
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

    public companion object {

        /**
         * A shortcut for the [ModalCommandConsequences] constructor, which can
         * be used to make [app.client.postCommand][Client.postCommand] calls
         * (or other cases when [ModalCommandConsequences] should be
         * instantiated) to be more concise.
         *
         * As a result `postCommand` usage can look like this:
         * ```
         *     app.client.postCommand(command, consequences(postingState, close) {
         *         onEvent(
         *             ItemImported::class.java,
         *             ItemImported.Field.itemId(),
         *             command.itemId
         *         ) {
         *             showMessage("Item imported")
         *         }
         *     })
         * ```
         *
         * @param C A type of commands whose consequences are specified.
         *
         * @param postingState A "posting" state of the UI that backs
         *   this object.
         * @param close A lambda that closes the UI that backs this object.
         * @param consequences A lambda, which uses the
         *   [ModalCommandConsequencesScope] API to define consequences that
         *   are possible as a result of posting commands of type [C] along with
         *   respective handlers.
         * @return The [ModalCommandConsequences] instance that was created.
         */
        public fun <C: CommandMessage> consequences(
            postingState: MutableState<Boolean>,
            close: () -> Unit,
            consequences: ModalCommandConsequencesScope<C>.() -> Unit,
        ): ModalCommandConsequences<C> {
            return ModalCommandConsequences(postingState, close, consequences)
        }
    }
}

/**
 * Describes a network communication error encountered while processing a
 * command posted by a modal UI.
 *
 * @property exception The exception that reported the communication failure.
 * @property acknowledgementReceived Whether Chords received the server
 *   acknowledgement before the failure. A value of `false` does not prove that
 *   the server rejected or never accepted the command.
 */
public class ModalCommandNetworkError(
    public val exception: ServerCommunicationException,
    public val acknowledgementReceived: Boolean
)

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
    public override val defaultTimeout: Duration
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

    private fun triggerDefaultTimeoutHandlers() = callbacks {
        defaultTimeoutHandlers.forEach { it() }
    }

    /**
     * Same as [onEvent] function, which ensures a common scenario of calling
     * the [close] function upon emitting the respective event.
     *
     * The [eventHandler] parameter is optional, and if specified, it is invoked
     * before [close] is called.
     *
     * @param E A type of event being subscribed to.
     *
     * @param eventType A type of event that should be subscribed to.
     * @param field A field whose value should identify an event being
     *   subscribed to.
     * @param fieldValue A value of event's [field] that identifies an event
     *   being subscribed to.
     * @return An [EventSubscription] instance, which can be used to manage this
     *   subscription, e.g. customize its timeout using the [withTimeout]
     *   function, or cancel the subscription using the
     *   [cancel][EventSubscription.cancel] function.
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
