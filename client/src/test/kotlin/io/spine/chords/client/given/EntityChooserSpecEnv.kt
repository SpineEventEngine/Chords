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

package io.spine.chords.client.given

import androidx.compose.runtime.Composable
import com.google.protobuf.Message
import io.spine.base.EntityState
import io.spine.chords.client.ConnectionStatus
import io.spine.chords.client.DataObservation
import io.spine.chords.client.EntityChooser
import io.spine.chords.client.ObservationSubscription
import io.spine.chords.client.createDataObservation
import io.spine.chords.client.testing.CompositionScene
import io.spine.chords.core.ComponentSetup
import io.spine.chords.proto.value.money.BankAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Supplies a real chooser and a deterministic observation source for its composition lifecycle.
 */
internal object EntityChooserSpecEnv {

    /**
     * Adapts a real message to the entity marker needed by the chooser.
     */
    class AccountEntity(
        val account: BankAccount
    ) : EntityState, Message by account

    /**
     * Renders the production dropdown while selecting a string-valued account number.
     */
    class AccountChooser : EntityChooser<AccountEntity, String>() {

        /**
         * Provides the field-bound declaration used by message forms.
         */
        companion object : ComponentSetup<AccountChooser>({ AccountChooser() })

        /**
         * Exposes the IDs supplied to the production dropdown.
         */
        val availableItems: List<String> get() = items.value.toList()

        override fun entityId(entityState: AccountEntity): String = entityState.account.number

        override fun itemText(entityId: String, entityState: AccountEntity?): String = entityId
    }

    /**
     * Delivers updates only to the current, uncancelled observation.
     */
    class EntitySource {

        /**
         * Keeps observation handles so tests can assert cancellation and replacement.
         */
        val observations = mutableListOf<DataObservation<List<AccountEntity>>>()

        /**
         * The complete value returned by the next read.
         */
        private var entities = emptyList<AccountEntity>()

        /**
         * Receives live updates until its subscription is cancelled.
         */
        private var onUpdate: ((List<AccountEntity>) -> Unit)? = null

        /**
         * Creates and activates an observation without network calls or background jobs.
         */
        fun observe(): DataObservation<List<AccountEntity>> {
            val observation = createDataObservation(
                initialValue = emptyList<AccountEntity>(),
                read = { entities },
                subscribe = { update: (List<AccountEntity>) -> Unit, _ ->
                    onUpdate = update
                    ObservationSubscription { onUpdate = null }
                },
                applyUpdate = { _, update -> update },
                connectionStatus = { ConnectionStatus.CONNECTED },
                onCancelled = {},
                requestContext = Dispatchers.Unconfined
            )
            observations.add(observation)
            runBlocking { observation.refresh() }
            return observation
        }

        /**
         * Replaces server data and forwards it through the live subscription.
         */
        fun emit(number: String) {
            entities = listOf(AccountEntity(BankAccount.newBuilder().setNumber(number).build()))
            onUpdate?.invoke(entities)
        }
    }

    /**
     * Runs a chooser scenario without a visible window.
     */
    fun inScene(content: @Composable () -> Unit, test: (CompositionScene) -> Unit) {
        CompositionScene(content).use(test)
    }

}
