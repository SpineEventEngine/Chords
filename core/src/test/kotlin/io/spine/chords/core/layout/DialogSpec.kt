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

package io.spine.chords.core.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class DialogSpec {

    @Test
    fun `determine dimensions from content by default`() {
        val dialog = TestDialog()

        dialog.width shouldBe Dp.Unspecified
        dialog.height shouldBe Dp.Unspecified
    }

    @Test
    fun `determine dimensions of built-in dialogs from content`() {
        val dialogs = listOf(
            ConfirmationDialog(),
            InputTextDialog(),
            MessageDialog()
        )

        dialogs.forEach { dialog ->
            dialog.width shouldBe Dp.Unspecified
            dialog.height shouldBe Dp.Unspecified
        }
    }

    @Test
    fun `allow specifying dimensions explicitly`() {
        val dialog = TestDialog().apply {
            width = 600.dp
            height = 400.dp
        }

        dialog.width shouldBe 600.dp
        dialog.height shouldBe 400.dp
    }

    private class TestDialog : Dialog() {

        override val title: String = "Test"

        @Composable
        override fun contentSection(): Unit = Unit

        override suspend fun submitContent(): Unit = Unit
    }
}
