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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.application
import io.spine.chords.core.layout.Dialog
import io.spine.chords.core.layout.DialogSetup
import io.spine.chords.core.writeOnce
import java.awt.Dimension

/**
 * An application's instance running in this JVM.
 *
 * This property automatically obtains a reference to the application
 * when the [Application]'s [run][Application.run] method is invoked.
 */
public var app: Application by writeOnce()

/**
 * A desktop client application.
 *
 * It can be constructed according to the actual application's needs, and
 * provides a top-level application's API available throughout
 * the application's implementation.
 *
 * UI-wise the application consists of a main window, where the user can switch
 * between one or more application views. The purpose of having multiple
 * application's views is to separate the overall application's functionality,
 * which can be arbitrarily big, into smaller pieces of UI, which are displayed
 * one at a time independent of the other ones. Each of such pieces
 * (application's views) can typically represent a logically-coherent piece of
 * application's functionality, and can be displayed by the user via the
 * application's navigation menu.
 *
 * In order to run the application, the actual application runner's
 * implementation has to do the following:
 * - create an instance of this class (or its subclass), configured according to
 *   the application's needs;
 * - invoke its [run] method, which makes the application's window to appear.
 *
 * Before the user has an ability to see and interact with the application's
 * views, the configurable sign-in screen can optionally be displayed. To do
 * this, the [signInScreenContent] method has to be implemented to render
 * the respective composable content, and invoke the sign-in callback as needed.
 *
 * @param name An application's name, which is in particular displayed in
 *   the application window's title.
 * @param views The list of application's views.
 * @param initialView Allows to specify a view from the list of [views], if any view other
 *   than the first one has to be displayed when the application starts.
 * @param minWindowSize The minimal size of the application window.
 */
public open class Application(
    public val name: String,
    private val views: List<AppView>,
    private val initialView: AppView? = null,
    private val minWindowSize: Dimension
) {

    /**
     *  An application's icon, which is in particular displayed in
     *  the application's window's titlebar.
     */
    public open val icon: Painter? @Composable get() = null

    /**
     * A top-level application's API that concerns UI related functionality or
     * characteristics.
     */
    public val ui: ApplicationUI
        get() {
            require(_ui != null) {
                "Application hasn't been run yet."
            }
            return _ui!!
        }

    private var _ui: ApplicationUI? = null

    /**
     * Runs the application by displaying the main application's window.
     *
     * This method blocks the current thread, and waits until the user closes
     * the window. If [exitProcessOnClose] is `True` (as is by default), the
     * process exits when the window is closed. Otherwise, this method just
     * returns after the window is closed.
     *
     * @param exitProcessOnClose
     *         specifies whether the process should automatically exit when
     *         the application's window is closed.
     */
    public fun run(exitProcessOnClose: Boolean = true) {
        check(_ui == null) {
            "Application.run() cannot be invoked more than once."
        }
        app = this

        application(exitProcessOnExit = exitProcessOnClose) {
            val appWindow = remember {
                val appWindow = createAppWindow(::exitApplication)
                _ui = ApplicationUI(appWindow)
                appWindow
            }
            appWindowContent(appWindow)
        }
    }

    private fun createAppWindow(onCloseRequest: () -> Unit): AppWindow {
        return AppWindow(
            {
                this.signInScreenContent(it)
            },
            views,
            initialView,
            onCloseRequest,
            minWindowSize
        )
    }

    /**
     * Renders the given [AppWindow] instance.
     */
    @Composable
    protected open fun appWindowContent(appWindow: AppWindow) {
        appWindow.content()
    }

    /**
     * A composable function, which defines the application's sign-in
     * screen content.
     *
     * The sign-in screen's implementation defined by this method is responsible
     * for invoking the [onAuthenticationSuccess] callback when authentication
     * was performed successfully. Doing so bypasses the sign-in screen and
     * displays the application's main screen, where the user can actually
     * see and switch between the application's views.
     */
    @Composable
    protected open fun signInScreenContent(onAuthenticationSuccess: () -> Unit) {
        SideEffect(onAuthenticationSuccess)
    }
}

/**
 * A top-level API that concerns the application's UI.
 *
 * @param appWindow
 *         main application's window.
 */
public class ApplicationUI(private val appWindow: AppWindow) {

    /**
     * Displays a modal screen.
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
     *         to indicate the illegal state when another modal screen
     *         is already displayed.
     */
    public fun openModalScreen(screen: @Composable () -> Unit) {
        appWindow.openModalScreen(screen)
    }

    /**
     * Closes the currently visible modal screen.
     *
     * @throws IllegalStateException
     *         to indicate the illegal state when no modal screen to close.
     */
    public fun closeCurrentModalScreen() {
        appWindow.closeCurrentModalScreen()
    }

    /**
     * Displays the given [Dialog] instance.
     *
     * When the modal window is shown, no other components from other screens
     * will be interactable, focusing user interaction on the modal content.
     *
     * It is designed to be used only as an internal low-level API, for dialog
     * implementation to be able to display themselves. In regular application
     * code though, the [Dialog]'s API should be used instead. For example,
     * when needed to display a specific dialog `SomeDialog` in the application,
     * the proper way to do this is like this:
     *
     * ```
     *    SomeDialog.open()
     *
     *    // or like this if dialog' properties need to be specified as well:
     *
     *    SomeDialog.open {
     *        prop1 = prop1Value
     *        prop2 = prop2Value
     *      ...
     *    }
     *
     *    // or if you already have a dialog instance and need to display it:
     *
     *    val dialog: SomeDialog = ...
     *    dialog.open()
     * ```
     *
     * @param dialog The [Dialog] instance, which needs to be displayed.
     *
     * @see Dialog
     * @see DialogSetup
     * @see closeCurrentDialog
     */
    internal fun openDialog(dialog: Dialog) {
        appWindow.openDialog(dialog)
    }

    /**
     * Closes the currently displayed dialog window while ignoring any data
     * that might have been entered in it.
     *
     * On a par with [openDialog], this is a part of an internal API for
     * [Dialog]s to be able to control their display lifecycle.
     *
     * @see openDialog
     */
    internal fun closeCurrentDialog() {
        appWindow.closeCurrentDialog()
    }
}
