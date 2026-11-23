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

package io.spine.chords.core.appshell

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement.Floating
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowPosition.PlatformDefault
import androidx.compose.ui.window.WindowState
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.chords.core.appshell.given.AppWindowFixtures.createWindow
import io.spine.chords.core.appshell.given.ApplicationSpecEnv.applicationOpening
import io.spine.chords.core.appshell.given.ApplicationSpecEnv.defaultApplication
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests how [Application] configures its main window.
 */
@DisplayName("`Application` should")
internal class ApplicationSpec {

    /**
     * The window must receive the application's own state object, so the application observes
     * the bounds the user gives the window afterward.
     *
     * This covers only the `AppWindow.windowState` field that `AppWindow.content()` reads.
     * Native windows cannot open in headless tests, so passing that state to the native window
     * is checked by hand.
     */
    @Test
    fun `hand its main window the state it supplies`() {
        val savedState = WindowState(
            position = WindowPosition(120.dp, 80.dp),
            size = DpSize(1600.dp, 900.dp)
        )
        val application = applicationOpening(savedState)
        val window = createWindow()

        application.initializeUi(window)

        window.windowState shouldBeSameInstanceAs savedState
    }

    /**
     * Applications that restore no bounds keep the window defaults that the shell used before
     * the state became configurable.
     */
    @Test
    fun `give its main window the default state when it supplies none`() {
        val window = createWindow()

        defaultApplication().initializeUi(window)

        val state = window.windowState
        state.placement shouldBe Floating
        state.isMinimized shouldBe false
        state.position shouldBe PlatformDefault
        state.size shouldBe DpSize(800.dp, 600.dp)
    }
}
