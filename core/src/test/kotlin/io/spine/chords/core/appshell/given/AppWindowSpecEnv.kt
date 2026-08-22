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

package io.spine.chords.core.appshell.given

import androidx.compose.runtime.Composable
import io.spine.chords.core.appshell.AppWindow
import io.spine.chords.core.layout.Dialog
import java.awt.Dimension

/**
 * Supplies the unrendered window and dialog types used by `AppWindowSpec`.
 */
internal object AppWindowSpecEnv {

    /**
     * Creates a window whose dialog stack can be exercised without opening an AWT window.
     */
    fun createWindow(): AppWindow = AppWindow(
        signInScreenContent = {},
        views = emptyList(),
        initialView = null,
        onCloseRequest = {},
        minWindowSize = Dimension(1, 1)
    )

    /**
     * A minimal dialog that needs no rendering for stack tests.
     */
    abstract class FixtureDialog : Dialog() {

        /**
         * The title is unused because the fixture is never rendered.
         */
        override val title: String = "Fixture"

        /**
         * Emits no content because the cases exercise only stack state.
         */
        @Composable
        override fun contentSection(): Unit = Unit

        /**
         * Performs no work because the cases never submit a dialog.
         */
        override suspend fun submitContent(): Unit = Unit
    }

    /**
     * The first distinct dialog type in stack tests.
     */
    class FirstDialog : FixtureDialog()

    /**
     * The second distinct dialog type in stack tests.
     */
    class SecondDialog : FixtureDialog()

    /**
     * The third distinct dialog type in stack tests.
     */
    class ThirdDialog : FixtureDialog()

    /**
     * A dialog that treats every other instance of its class as equal.
     */
    class EqualDialog : FixtureDialog() {

        /**
         * Makes equality deliberately broader than display-stack identity.
         */
        override fun equals(other: Any?): Boolean = other is EqualDialog

        /**
         * Keeps the equality contract consistent for all fixture instances.
         */
        override fun hashCode(): Int = EqualDialog::class.hashCode()
    }
}
