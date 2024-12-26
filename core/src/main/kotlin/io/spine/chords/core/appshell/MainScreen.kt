/*
 * Copyright 2024, TeamDev. All rights reserved.
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

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import io.spine.chords.runtime.safeCast

/**
 * Represents the main screen in the application.
 */
public class MainScreen(
    private val appViews: List<AppView>,
    private val initialView: AppView?
) : Screen {

    /**
     * The instance of the view [Navigator] that is initialized
     * during the rendering of the screen.
     */
    private lateinit var viewNavigator: Navigator

    @Composable
    public override fun Content() {
        Navigator(initialView ?: appViews[0]) {
            viewNavigator = it
            Scaffold(
                topBar = { TopBar() }
            ) {
                val topPadding = it.calculateTopPadding()
                NavigationDrawer(appViews, topPadding)
            }
        }
    }

    /**
     * Selects the given [appView].
     */
    internal fun select(appView: AppView) {
        check(appViews.contains(appView)) {
            "The given view has not been added to the main screen `$appView`."
        }
        viewNavigator.push(appView)
    }

    /**
     * Returns the currently selected view.
     */
    internal val currentView: AppView
        get() {
            return viewNavigator.lastItem.safeCast<AppView>()
        }
}
