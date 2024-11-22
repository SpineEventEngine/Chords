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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import io.spine.chords.core.layout.Dialog
import java.awt.Dimension

/**
 * Represents the main application window and provides API to display
 * a modal screens using the entire area of the application window.
 * This API should be used to change the current visible screen
 * of the application, for example for wizards, full screen dialogs, etc.
 *
 * @param signInScreenContent A content for the sign-in screen.
 * @param views The list of application's views.
 * @param initialView Allows to specify a view from the list of `views`, if any
 *   view other than the first one has to be displayed when
 *   the application starts.
 * @param onCloseRequest An action that should be performed on window closing.
 * @param minWindowSize The minimal size of the application window.
 */
public class AppWindow(
    private val signInScreenContent: @Composable (onSuccessAuthentication: () -> Unit) -> Unit,
    views: List<AppView>,
    initialView: AppView?,
    private val onCloseRequest: () -> Unit,
    private val minWindowSize: Dimension
) {

    /**
     * The main screen of the application.
     */
    private val mainScreen: @Composable () -> Unit = {
        MainScreen(views, initialView)
    }

    /**
     * The sign-in screen of the application.
     */
    private val signInScreen: @Composable () -> Unit = {
        signInScreenContent { currentScreen = mainScreen }
    }

    /**
     * Holds the current visible screen.
     */
    private var currentScreen by mutableStateOf<@Composable () -> Unit>(signInScreen)

    /**
     * The bottom-most dialog in the current dialog display stack, or `null` if
     * no dialogs are displayed currently.
     *
     * Dialogs are modal windows, which means that there can be at most once
     * dialog that the user can interact with at a time. It's possible to
     * display nested dialogs though. That is, when some dialog is already
     * displayed, another dialog can be open (see
     * [DialogSetup][io.spine.chords.core.layout.DialogSetup.open]), which means
     * that the first dialog still remains opened, but cannot be interacted with
     * until the second one (which is displayed on top of it) is closed.
     *
     * This means that at any given moment in time there is essentially a stack
     * of dialogs (zero or more nested dialogs). This property refers to the
     * very first dialog that was displayed among all these dialogs (the bottom
     * of the dialogs stack).
     */
    private var bottomDialog by mutableStateOf<Dialog?>(null)

    /**
     * Renders the application window's content.
     */
    @Composable
    public fun content() {
        Window(
            onCloseRequest = onCloseRequest,
            title = app.name,
            icon = app.icon
        ) {
            if (window.minimumSize != minWindowSize) {
                // Prevent reassigning minimumSize to the same value, as it was seen to
                // restore the same window size on every recomposition call.
                window.minimumSize = minWindowSize
            }
            Box(
                modifier = Modifier.background(MaterialTheme.colorScheme.background)
            ) {
                currentScreen()
            }
            bottomDialog?.Content()
        }
    }

    /**
     * Makes the given screen the current visible modal screen.
     *
     * This screen will be rendered using the entire area
     * of the application window. No other components
     * from other screens will be visible or interactable,
     * so it acts like a modal screen.
     *
     * The hierarchy of modal screens is not supported,
     * so it will be an illegal state when some modal screen
     * display is requested while another screen is already displayed.
     *
     * @throws IllegalStateException
     *          to indicate the illegal state when another modal screen
     *          is already displayed.
     */
    public fun openModalScreen(screen: @Composable () -> Unit) {
        check(currentScreen == mainScreen) {
            "Another modal screen is visible already."
        }
        check(bottomDialog == null) {
            "Cannot display the modal screen when a dialog is displayed."
        }
        currentScreen = screen
    }

    /**
     * Closes the currently visible modal screen.
     *
     * @throws IllegalStateException
     *          to indicate the illegal state when no modal screen to close.
     */
    public fun closeCurrentModalScreen() {
        currentScreen = mainScreen
    }

    /**
     * Displays a modal window.
     *
     * When the modal window is shown, no other components from other screens
     * will be interactable, focusing user interaction on the modal content.
     *
     * @param dialog An instance of the dialog that should be displayed.
     */
    internal fun openDialog(dialog: Dialog) {
        check(bottomDialog != dialog) { "This dialog is already open." }

        dialog.isBottomDialog = bottomDialog == null
        if (dialog.isBottomDialog) {
            bottomDialog = dialog
        } else {
            bottomDialog!!.openNestedDialog(dialog)
        }
    }

    /**
     * Closes the specified dialog.
     *
     * This is a part of an internal dialog management API.
     *
     * @param dialog The dialog that needs to be closed.
     */
    internal fun closeDialog(dialog: Dialog) {
        checkNotNull(bottomDialog) { "No dialogs are displayed currently." }
        if (dialog == bottomDialog) {
            check(bottomDialog!!.nestedDialog == null) {
                "Cannot close a dialog while it has a nested dialog open."
            }
            bottomDialog = null
        } else {
            bottomDialog!!.closeNestedDialog(dialog)
        }
    }
}
