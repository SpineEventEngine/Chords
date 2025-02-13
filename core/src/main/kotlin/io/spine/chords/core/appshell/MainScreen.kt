/*
 * Copyright 2025, TeamDev. All rights reserved.
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
 * The main screen of the application.
 *
 * Implements the [Screen] interface, enabling the use of navigation
 * functionality provided by the Voyager multiplatform navigation library.
 * See [AppWindow] for detail on how to display a screen.
 *
 * Provides internal API which allows selecting one of the [AppView]s
 * to be displayed on the main screen.
 *
 * @param appViews The list of [AppView]s that will be selectively displayed on the screen.
 * @param initialView The initial [AppView] to display.
 */
public class MainScreen(
    private val appViews: List<AppView>,
    private val initialView: AppView? = null
) : Screen {

    /**
     * The top app bar, as per [Material UI definition](https://m3.material.io/components/top-app-bar/overview).
     */
    internal val topBar: TopBar = TopBar()

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
                topBar = { topBar.Content() }
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
            "The given view has not been added to the main screen `${appView.name}`."
        }
        viewNavigator.push(appView)
    }

    /**
     * Returns the currently selected view.
     */
    internal val currentView: AppView
        get() = viewNavigator.lastItem.safeCast<AppView>()
}
