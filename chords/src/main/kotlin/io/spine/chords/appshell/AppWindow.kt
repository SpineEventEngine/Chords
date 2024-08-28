/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.appshell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.Window
import java.awt.Dimension

private val minWindowSize = Dimension(1100, 800)


/**
 * Represents the main application window and provides API to display
 * a modal screens using the entire area of the application window.
 * This API should be used to change the current visible screen
 * of the application, for example for wizards, full screen dialogs, etc.
 *
 * @param signInScreenContent
 *         a content for the sign-in screen.
 * @param views
 *         the list application's views.
 * @param initialView
 *         allows to specify a view from the list of [views], if any view other
 *         than the first one has to be displayed when the application starts.
 * @param onCloseRequest
 *         an action that should be performed on window closing.
 */
public class AppWindow(
    private val signInScreenContent: @Composable (onSuccessAuthentication: () -> Unit) -> Unit,
    views: List<AppView>,
    initialView: AppView?,
    private val onCloseRequest: () -> Unit
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
        signInScreenContent { currentScreen.value = mainScreen }
    }

    /**
     * Holds the current visible screen.
     */
    private val currentScreen: MutableState<@Composable () -> Unit> =
        mutableStateOf(signInScreen)

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
            currentScreen.value()
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
    public fun showModalScreen(screen: @Composable () -> Unit) {
        check(currentScreen.value == mainScreen) {
            "Another modal screen is visible already."
        }
        currentScreen.value = screen
    }

    /**
     * Closes the currently visible modal screen.
     *
     * @throws IllegalStateException
     *          to indicate the illegal state when no modal screen to close.
     */
    public fun closeCurrentModalScreen() {
        check(currentScreen.value != mainScreen) {
            "There is no modal screen to close."
        }
        currentScreen.value = mainScreen
    }
}
