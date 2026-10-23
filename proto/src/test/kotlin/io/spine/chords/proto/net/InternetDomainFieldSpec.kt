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

package io.spine.chords.proto.net

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions.OnClick
import androidx.compose.ui.semantics.SemanticsProperties.EditableText
import androidx.compose.ui.semantics.SemanticsProperties.Focused
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.spine.chords.proto.TestApplication
import io.spine.chords.proto.testing.inScene
import io.spine.chords.proto.testing.onUiThread
import io.spine.net.InternetDomain
import io.spine.net.InternetDomains
import java.awt.event.InputEvent.SHIFT_DOWN_MASK
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_TAB
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Verifies domain editing and supporting content through the public component API.
 */
@DisplayName("`InternetDomainField` should")
internal class InternetDomainFieldSpec {

    /**
     * Supplies the application defaults required by class-based components.
     */
    @BeforeEach
    fun installApplication() {
        TestApplication.install()
    }

    /**
     * Customization must leave domain formatting, revision, and default feedback intact.
     */
    @Test
    fun `retain default validation and domain editing`() {
        val field = InternetDomainField()
            .apply { value = mutableStateOf(InternetDomains.valueOf("example.com")) }

        inScene({ field.Content() }) { scene ->
            scene.inputText() shouldBe "example.com"
            scene.displayedText() shouldBe emptySet()

            scene.enterText("-")

            field.valid.value shouldBe false
            field.value.value shouldBe null
            scene.displayedText() shouldBe setOf("Enter a valid domain name.")

            scene.enterText(" EXAMPLE . ORG ")

            field.valid.value shouldBe true
            field.value.value shouldBe InternetDomains.valueOf("example.org")
            scene.inputText() shouldBe "example.org"
            scene.displayedText() shouldBe emptySet()
        }
    }

    /**
     * Both renderers receive the active error, with local validation taking precedence.
     */
    @ParameterizedTest(name = "custom content = {0}")
    @ValueSource(booleans = [false, true])
    fun `follow parsing custom and external validation updates`(customContent: Boolean) {
        val externalMessage = mutableStateOf<String?>(null)
        val prefix = if (customContent) "Custom: " else ""
        val field = InternetDomainField()
            .apply {
                externalValidationMessage = externalMessage
                onValidate = { if (it.value == "example.com") "Choose another domain." else null }
                if (customContent) {
                    supportingText = { Text("$prefix$it") }
                }
            }

        inScene({ field.Content() }) { scene ->
            onUiThread { externalMessage.value = "A domain is required." }
            scene.render()
            scene.displayedText() shouldBe setOf("${prefix}A domain is required.")

            scene.enterText("-")
            scene.displayedText() shouldBe setOf("${prefix}Enter a valid domain name.")

            scene.enterText("example.com")
            field.valid.value shouldBe false
            scene.displayedText() shouldBe setOf("${prefix}Choose another domain.")

            scene.enterText("example.org")
            field.valid.value shouldBe true
            scene.displayedText() shouldBe setOf("${prefix}A domain is required.")

            onUiThread { externalMessage.value = null }
            scene.render()
            scene.displayedText() shouldBe if (customContent) setOf(prefix) else emptySet()
        }
    }

    /**
     * The existing compact and reserved layouts must still respond to validation changes.
     */
    @ParameterizedTest(name = "reserveValidationSpace = {0}")
    @ValueSource(booleans = [false, true])
    fun `retain the default supporting layout`(reserveSpace: Boolean) {
        val validation = mutableStateOf<String?>(null)
        val field = InternetDomainField()
            .apply {
                reserveValidationSpace = reserveSpace
                externalValidationMessage = validation
            }

        inScene({ field.Content() }) { scene ->
            val initialSize = scene.contentSize

            onUiThread { validation.value = "Choose another domain." }
            scene.render()

            if (reserveSpace) {
                scene.contentSize shouldBe initialSize
            } else {
                scene.contentSize.height shouldBeGreaterThan initialSize.height
            }
            val errorSize = scene.contentSize

            onUiThread { field.supportingText = { Text(it) } }
            scene.render()
            scene.contentSize shouldBe errorSize
            scene.displayedText() shouldBe setOf("Choose another domain.")

            onUiThread { validation.value = null }
            scene.render()
            scene.contentSize shouldBe initialSize
            scene.displayedText() shouldBe emptySet()
        }
    }

    /**
     * A caller can replace the renderer on an existing component and restore its default.
     */
    @Test
    fun `recompose when supporting content is replaced`() {
        val field = InternetDomainField()
            .apply { externalValidationMessage = mutableStateOf("Choose another domain.") }
        val defaultContent = field.supportingText

        inScene({ field.Content() }) { scene ->
            val defaultSize = scene.contentSize

            onUiThread { field.supportingText = { Text("Custom: $it") } }
            scene.render()
            scene.displayedText() shouldBe setOf("Custom: Choose another domain.")
            scene.contentSize shouldBe defaultSize

            onUiThread { field.supportingText = defaultContent }
            scene.render()
            scene.displayedText() shouldBe setOf("Choose another domain.")
            scene.contentSize shouldBe defaultSize
        }
    }

    /**
     * A link remains a separate accessible action below the editor and works with the keyboard.
     */
    @Test
    fun `focus and activate a supporting link without changing the domain`() {
        var opened = 0
        val domain = mutableStateOf<InternetDomain?>(InternetDomains.valueOf("example.com"))
        val validation = mutableStateOf("Choose another domain.")

        inScene({
            InternetDomainField {
                value = domain
                externalValidationMessage = validation
                supportingText = { message ->
                    Row {
                        Text(message)
                        Text(
                            text = AnnotatedString("Domain help"),
                            modifier = Modifier.clickable { opened++ },
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }
            }
        }) { scene ->
            scene.displayedText() shouldBe setOf("Choose another domain.", "Domain help")
            scene.pressKey(VK_TAB)
            scene.semanticsNodes()
                .single { it.config.getOrNull(EditableText) != null }
                .config
                .getOrNull(Focused) shouldBe true

            scene.pressKey(VK_TAB)
            val link = scene.semanticsNodes()
                .single {
                    it.config.getOrNull(OnClick) != null &&
                            it.config.getOrNull(EditableText) == null
                }
            link.config.getOrNull(Focused) shouldBe true

            scene.pressKey(VK_ENTER)
            opened shouldBe 1
            domain.value shouldBe InternetDomains.valueOf("example.com")
            scene.inputText() shouldBe "example.com"

            scene.pressKey(VK_TAB, SHIFT_DOWN_MASK)
            scene.semanticsNodes()
                .single { it.config.getOrNull(EditableText) != null }
                .config
                .getOrNull(Focused) shouldBe true
        }
    }
}
