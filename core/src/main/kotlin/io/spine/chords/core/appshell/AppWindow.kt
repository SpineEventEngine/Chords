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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import io.spine.chords.core.layout.Dialog
import java.awt.Dimension

/**
 * The main application window that displays the required screen
 * using the entire area of the window, e.g. [SignInScreen] or [MainScreen].
 *
 * Powered by Voyager, provides two levels of navigation:
 *
 * 1.The screen-level navigation. Allows selecting a screen
 * to fill the entire main window.
 *
 * Currently, there is no public API available to display a custom screen.
 * Instead, two screens are predefined: [SignInScreen] and [MainScreen].
 * Please refer to their description for more detail.
 * In the future, more screen implementations may be created.
 *
 * 2. The view-level navigation. Allows selecting an [AppView] to be displayed
 * on the `MainScreen`.
 * Please refer to [ApplicationUI.select] for details.
 *
 * @param signInScreenContent A content for the [SignInScreen].
 * @param views The list of [AppView]s that will be selectively displayed
 * on the [MainScreen] screen.
 * @param initialView Allows specifying a view from the list of `views`, if any
 *   view other than the first one has to be displayed when
 *   the application starts.
 * @param onCloseRequest An action that should be performed on window closing.
 * @param minWindowSize The minimal size of the application window.
 * @param windowOverlay Content displayed over the current screen and under
 *   application dialogs.
 */
public class AppWindow(
    private val signInScreenContent: @Composable (onSuccessAuthentication: () -> Unit) -> Unit,
    views: List<AppView>,
    initialView: AppView?,
    private val onCloseRequest: () -> Unit,
    private val minWindowSize: Dimension,
    private val windowOverlay: @Composable BoxScope.() -> Unit = {}
) {

    /**
     * The main screen of the application.
     */
    internal val mainScreen: MainScreen = MainScreen(views, initialView)

    /**
     * The sign-in screen of the application.
     */
    private val signInScreen: SignInScreen = SignInScreen(signInScreenContent) {
        screenNavigator.pop()
        screenNavigator.push(mainScreen)
    }

    /**
     * The bottom-most dialog in the current dialog display stack, or `null` if
     * no dialogs are displayed currently.
     *
     * Dialogs are modal windows, so only the top dialog in the stack can receive
     * user interaction. Displaying another dialog while one is open nests it on
     * top of the first dialog (see
     * [DialogSetup][io.spine.chords.core.layout.DialogSetup.open]).
     *
     * This property holds the first dialog displayed, at the bottom of the
     * dialog stack.
     */
    private var bottomDialog by mutableStateOf<Dialog?>(null)

    /**
     * The bottom dialog in the current stack, or `null` when the stack is empty.
     */
    internal val currentBottomDialog: Dialog?
        get() = bottomDialog

    /**
     * An instance of the screen [Navigator] that will be initialized during
     * the rendering of the main window.
     */
    private lateinit var screenNavigator: Navigator

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
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Navigator(signInScreen) {
                    screenNavigator = it
                    CurrentScreen()
                }
                windowOverlay()
            }
            bottomDialog?.Content()
        }
    }

    /**
     * Navigates the application to the sign-in screen.
     *
     * This function removes the previously displayed screen
     * from the navigation stack, preventing users from navigating back
     * and continuing using the app without authentication.
     */
    internal fun switchToSignIn() {
        screenNavigator.pop()
        screenNavigator.push(signInScreen)
    }

    /**
     * Selects the given [appView].
     */
    internal fun select(appView: AppView) {
        mainScreen.select(appView)
    }

    /**
     * Returns the currently selected view.
     */
    internal val currentView: AppView get() = mainScreen.currentView

    /**
     * Requests that a modal dialog be displayed and returns the effective instance.
     *
     * When the modal dialog is shown, no other components from other screens
     * will be interactable, focusing user interaction on the modal content.
     *
     * If another instance of the same concrete runtime class is already in the
     * dialog stack, this request is suppressed and that displayed instance is
     * returned. Requesting the same dialog instance again while it is in the
     * stack fails.
     *
     * @param dialog An instance of the dialog that should be displayed.
     * @return The requested instance if it was added, or the already displayed
     *   instance of the same concrete runtime class.
     * @throws IllegalStateException If [dialog] is already in the stack.
     */
    internal fun openDialog(dialog: Dialog): Dialog {
        var currentDialog = bottomDialog
        var deepestDialog: Dialog? = null
        var sameClassDialog: Dialog? = null
        while (currentDialog != null) {
            check(currentDialog !== dialog) { "This dialog is already open." }
            if (sameClassDialog == null && currentDialog.javaClass == dialog.javaClass) {
                sameClassDialog = currentDialog
            }
            deepestDialog = currentDialog
            currentDialog = currentDialog.nestedDialog
        }
        if (sameClassDialog != null) {
            return sameClassDialog
        }
        dialog.isBottomDialog = bottomDialog == null
        if (dialog.isBottomDialog) {
            bottomDialog = dialog
        } else {
            checkNotNull(deepestDialog).nestedDialog = dialog
        }
        return dialog
    }

    /**
     * Closes the specified dialog.
     *
     * This is a part of an internal dialog management API.
     *
     * A request to close a dialog instance that is not in the current stack is
     * a no-op. Dialogs in the stack are matched by reference identity.
     *
     * @param dialog The dialog that needs to be closed.
     * @throws IllegalStateException If the dialog cannot be closed due to a
     *   nested modal dialog that is currently open.
     */
    internal fun closeDialog(dialog: Dialog) {
        val bottom = bottomDialog ?: return
        if (dialog === bottom) {
            val nestedDialog = bottom.nestedDialog
            if (nestedDialog != null) {
                if (nestedDialog.dismissibleWithParent) {
                    nestedDialog.close()
                } else {
                    error("Cannot close a dialog ${dialog.javaClass.simpleName} while it has a " +
                            "nested dialog open: ${nestedDialog.javaClass.simpleName}.")
                }
            }
            bottomDialog = null
        } else {
            bottom.closeNestedDialog(dialog)
        }
    }
}

/**
 * A sign-in screen of the application.
 *
 * @param content The composable content displayed by the screen.
 * @param onSuccessAuthentication Invoked after successful authentication.
 */
private class SignInScreen(
    private val content: @Composable (onSuccessAuthentication: () -> Unit) -> Unit,
    private val onSuccessAuthentication: () -> Unit
) : Screen {
    @Composable
    override fun Content() {
        content {
            onSuccessAuthentication()
        }
    }
}
