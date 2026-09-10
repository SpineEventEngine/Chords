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

package io.spine.chords.client

import androidx.compose.runtime.mutableStateOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.chords.client.given.EntityChooserSpecEnv.AccountChooser
import io.spine.chords.client.given.EntityChooserSpecEnv.AccountEntity
import io.spine.chords.client.given.EntityChooserSpecEnv.EntitySource
import io.spine.chords.client.given.EntityChooserSpecEnv.inScene
import io.spine.chords.proto.form.MessageForm
import io.spine.chords.proto.form.ValidationDisplayMode.MANUAL
import io.spine.chords.proto.form.invoke
import io.spine.chords.proto.value.money.BankAccount
import io.spine.chords.proto.value.money.BankAccountDef
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies subscription ownership while a real field-bound chooser is hidden and restored.
 */
@DisplayName("`EntityChooser` should")
internal class EntityChooserSpec {

    /**
     * Installs shared defaults and resets the controllable client before each test.
     */
    @BeforeEach
    fun setUp() {
        TestApplication.install()
        TestClient.observe = null
    }

    /**
     * Removes the test-specific observation factory from the shared application.
     */
    @AfterEach
    fun tearDown() {
        TestClient.observe = null
    }

    /**
     * Returning to a retained editor must start a new feed after cancelling the previous one.
     */
    @Test
    fun `resume live updates when its form part returns`() {
        val source = EntitySource()
        TestClient.observe = { entityClass ->
            entityClass shouldBe AccountEntity::class.java
            source.observe()
        }
        val form = MessageForm.create(mutableStateOf<BankAccount?>(null), BankAccount::newBuilder) {
            validationDisplayMode = MANUAL
        }
        val shown = mutableStateOf(true)
        lateinit var chooser: AccountChooser

        inScene({
            form.Content {
                if (shown.value) {
                    chooser = AccountChooser(BankAccountDef.number)
                }
            }
        }) { scene ->
            val firstChooser = chooser
            source.emit("123")
            scene.render()
            chooser.availableItems shouldBe listOf("123")

            shown.value = false
            scene.render()
            source.observations.single().status.value shouldBe DataObservationStatus.Cancelled
            source.emit("456")
            shown.value = true
            scene.render()

            chooser shouldBeSameInstanceAs firstChooser
            source.observations.size shouldBe 2
            chooser.availableItems shouldBe listOf("456")
            source.emit("789")
            scene.render()
            chooser.availableItems shouldBe listOf("789")
        }
        source.observations.last().status.value shouldBe DataObservationStatus.Cancelled
    }
}
