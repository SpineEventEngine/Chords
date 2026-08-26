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

package io.spine.chords.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.chords.core.layout.TestScene
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies the configuration lifecycle of class-based components.
 */
@DisplayName("`Component` should")
internal class ComponentSpec {

    /**
     * Protects property updates captured from a recomposing parent scope.
     */
    @Test
    fun `apply updated properties to a remembered instance`() {
        var configuredValue by mutableStateOf("first")
        lateinit var component: PropertyComponent
        TestScene {
            val currentValue = configuredValue
            component = PropertyComponent {
                value = currentValue
            }
        }.use { scene ->
            val initialComponent = component
            component.renderedValue shouldBe "first"

            configuredValue = "second"
            scene.render()

            component shouldBeSameInstanceAs initialComponent
            component.renderedValue shouldBe "second"
        }
    }

    /**
     * Installs the application that supplies shared component defaults.
     */
    private companion object {

        /**
         * Initializes the application required by the component lifecycle.
         */
        @JvmStatic
        @BeforeAll
        fun setUpApplication() {
            TestApplication.install()
        }
    }
}

/**
 * Records the property value observed during its latest composition.
 */
private class PropertyComponent : Component() {

    /**
     * Declares remembered instances of this component.
     */
    companion object : ComponentSetup<PropertyComponent>({ PropertyComponent() })

    /**
     * The value supplied by the current component declaration.
     */
    var value: String by mutableStateOf("")

    /**
     * The value observed during the latest composition.
     */
    var renderedValue: String = ""
        private set

    /**
     * Records the configured value rendered by this component.
     */
    @Composable
    override fun content() {
        renderedValue = value
    }
}
