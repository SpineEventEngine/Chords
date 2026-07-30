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
import androidx.compose.runtime.mutableStateOf
import io.kotest.matchers.shouldBe
import io.spine.base.CommandMessage
import io.spine.chords.client.ServerCommunicationException
import io.spine.chords.client.ServerError
import io.spine.chords.client.TestApplication
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

internal class ModalCommandConsequencesSpec {

    @Test
    fun `clear posting and keep modal open by default`() {
        runBlocking {
            val scenario = scenario()
            var presentedError: ModalCommandNetworkError? = null
            var postingDuringPresentation: Boolean? = null
            scenario.consequences.networkErrorPresentation = {
                postingDuringPresentation = scenario.posting.value
                presentedError = it
            }
            scenario.registerPredefinedConsequences()
            val exception = networkError()

            scenario.scope.failWith(exception)

            postingDuringPresentation shouldBe false
            scenario.posting.value shouldBe false
            scenario.closeCalls.get() shouldBe 0
            presentedError!!.exception shouldBe exception
            presentedError!!.acknowledgementReceived shouldBe false
        }
    }

    @Test
    fun `report acknowledgement received before network error`() {
        runBlocking {
            val scenario = scenario()
            var presentedError: ModalCommandNetworkError? = null
            scenario.consequences.networkErrorPresentation = {
                presentedError = it
            }
            scenario.registerPredefinedConsequences()

            scenario.scope.acknowledge()
            scenario.scope.failWith(networkError())

            presentedError!!.acknowledgementReceived shouldBe true
        }
    }

    @Test
    fun `close modal after presentation when configured`() {
        runBlocking {
            val actions = ArrayList<String>()
            val scenario = scenario {
                actions += "close"
            }
            scenario.consequences.closeOnNetworkError = true
            scenario.consequences.networkErrorPresentation = {
                actions += "present"
            }
            scenario.registerPredefinedConsequences()

            scenario.scope.failWith(networkError())

            actions shouldBe listOf("present", "close")
            scenario.closeCalls.get() shouldBe 1
        }
    }

    @Test
    fun `handle concurrent network errors once per posting`() {
        runBlocking {
            val scenario = scenario()
            val presentationCalls = AtomicInteger()
            scenario.consequences.closeOnNetworkError = true
            scenario.consequences.networkErrorPresentation = {
                presentationCalls.incrementAndGet()
            }
            scenario.registerPredefinedConsequences()
            val exception = networkError()

            coroutineScope {
                repeat(8) {
                    launch(Dispatchers.Default) {
                        scenario.scope.failWith(exception)
                    }
                }
            }

            presentationCalls.get() shouldBe 1
            scenario.closeCalls.get() shouldBe 1
        }
    }

    @Test
    fun `guard network errors separately for each posting`() {
        runBlocking {
            val consequences = consequences(mutableStateOf(true)) {}
            val presentationCalls = AtomicInteger()
            consequences.networkErrorPresentation = {
                presentationCalls.incrementAndGet()
            }

            repeat(2) {
                val posting = mutableStateOf(true)
                val scope = CapturingScope(posting, {})
                consequences.predefinedConsequences.invoke(scope)
                scope.failWith(networkError())
            }

            presentationCalls.get() shouldBe 2
        }
    }

    @Test
    fun `retain server-error registration`() {
        val scenario = scenario()

        scenario.registerPredefinedConsequences()

        scenario.scope.serverErrorRegistered shouldBe true
        scenario.posting.value shouldBe true
        scenario.closeCalls.get() shouldBe 0
    }

    @Test
    fun `advise retry after connection is restored in default message`() {
        val consequences = consequences(mutableStateOf(false)) {}

        consequences.networkErrorMessage shouldBe
                "Server connection failed. Please try again when the connection is restored."
    }

    private fun scenario(onClose: () -> Unit = {}): Scenario {
        val posting = mutableStateOf(true)
        val closeCalls = AtomicInteger()
        val close = {
            closeCalls.incrementAndGet()
            onClose()
        }
        val consequences = consequences(posting, close)
        consequences.networkErrorPresentation = {}
        return Scenario(
            consequences,
            CapturingScope(posting, close),
            posting,
            closeCalls
        )
    }

    private companion object {

        @JvmStatic
        @BeforeAll
        fun setUpApplication() {
            TestApplication.install()
        }

        fun consequences(
            posting: MutableState<Boolean>,
            close: () -> Unit
        ): ModalCommandConsequences<CommandMessage> =
            ModalCommandConsequences(posting, close) {}

        fun networkError(): ServerCommunicationException =
            ServerCommunicationException(IllegalStateException("Connection lost."))

        val command: CommandMessage = Proxy.newProxyInstance(
            CommandMessage::class.java.classLoader,
            arrayOf(CommandMessage::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "TestCommand"
                else -> error("Unexpected command method: ${method.name}.")
            }
        } as CommandMessage
    }

    /**
     * Captures callbacks without exercising production scope creation.
     *
     * A regular scenario passes the same posting state to the consequences
     * and this scope. The per-posting guard test intentionally supplies a
     * fresh scope state for every registration.
     */
    private class CapturingScope(
        posting: MutableState<Boolean>,
        close: () -> Unit
    ) : ModalCommandConsequencesScope<CommandMessage>(
        command,
        posting,
        close,
        Duration.ZERO
    ) {
        private lateinit var acknowledgeHandler: suspend () -> Unit
        private lateinit var networkErrorHandler:
                suspend (ServerCommunicationException) -> Unit
        var serverErrorRegistered: Boolean = false
            private set

        override fun onAcknowledge(handler: suspend () -> Unit) {
            acknowledgeHandler = handler
        }

        override fun onNetworkError(
            handler: suspend (ServerCommunicationException) -> Unit
        ) {
            networkErrorHandler = handler
        }

        override fun onServerError(handler: suspend (ServerError) -> Unit) {
            serverErrorRegistered = true
        }

        suspend fun acknowledge() {
            acknowledgeHandler()
        }

        suspend fun failWith(exception: ServerCommunicationException) {
            networkErrorHandler(exception)
        }
    }

    private data class Scenario(
        val consequences: ModalCommandConsequences<CommandMessage>,
        val scope: CapturingScope,
        val posting: MutableState<Boolean>,
        val closeCalls: AtomicInteger
    ) {
        fun registerPredefinedConsequences() {
            consequences.predefinedConsequences.invoke(scope)
        }
    }
}
