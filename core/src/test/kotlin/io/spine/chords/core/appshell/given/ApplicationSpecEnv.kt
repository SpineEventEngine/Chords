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

import androidx.compose.ui.window.WindowState
import io.spine.chords.core.appshell.AppWindow
import io.spine.chords.core.appshell.Application
import java.awt.Dimension

/**
 * Initializes application windows for `ApplicationSpec` without creating native windows.
 */
internal object ApplicationSpecEnv {

    /**
     * Creates an application that opens its main window with the given state.
     */
    fun applicationOpening(state: WindowState): Application =
        object : Application(
            name = "Restored window",
            views = emptyList(),
            minWindowSize = Dimension(1, 1)
        ) {
            /**
             * Supplies the state chosen by the test, such as bounds saved by a previous run.
             */
            override val windowState: WindowState = state
        }

    /**
     * Creates a main window and lets the given application configure it.
     */
    fun initializedWindow(application: Application): AppWindow {
        val window = AppWindow(
            signInScreenContent = {},
            views = emptyList(),
            initialView = null,
            onCloseRequest = {},
            minWindowSize = Dimension(1, 1)
        )
        application.initializeUi(window)
        return window
    }
}
