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

import androidx.compose.ui.semantics.SemanticsProperties.Text
import androidx.compose.ui.semantics.getOrNull
import io.spine.chords.core.appshell.AppWindow
import io.spine.chords.core.appshell.Application
import io.spine.chords.core.appshell.testing.StatefulNavigationView
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsTheme
import io.spine.chords.core.styling.chordsLightColorScheme
import java.awt.Dimension

/**
 * Initializes a real application shell without creating a native window.
 */
internal object MainScreenSpecEnv {

    /**
     * Applies either the unmodified application default or a subclass's explicit opt-out.
     */
    fun scene(view: StatefulNavigationView, showTopBar: Boolean): TestScene {
        val application = if (showTopBar) {
            Application(
                name = "Default header",
                views = listOf(view),
                minWindowSize = Dimension(1, 1)
            )
        } else {
            object : Application(
                name = "No header",
                views = listOf(view),
                minWindowSize = Dimension(1, 1)
            ) {
                /**
                 * Makes all of the main screen's height available to navigation and content.
                 */
                override val showTopBar: Boolean = false
            }
        }
        val window = AppWindow(
            signInScreenContent = {},
            views = listOf(view),
            initialView = view,
            onCloseRequest = {},
            minWindowSize = Dimension(1, 1)
        )
        application.initializeUi(window)
        return TestScene {
            ChordsTheme(colorScheme = chordsLightColorScheme()) {
                window.mainScreen.Content()
            }
        }
    }

    /**
     * Reads rendered labels so hidden headers cannot pass by retaining their content off-screen.
     */
    fun text(scene: TestScene): List<String> = scene.semanticsNodes()
        .flatMap { it.config.getOrNull(Text).orEmpty() }
        .map { it.text }
}
