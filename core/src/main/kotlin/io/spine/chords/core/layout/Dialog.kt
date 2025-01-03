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

package io.spine.chords.core.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement.End
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.Bottom
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.input.key.Key.Companion.Enter
import androidx.compose.ui.input.key.Key.Companion.Escape
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntOffset.Companion.Zero
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.spine.chords.core.AbstractComponentSetup
import io.spine.chords.core.Component
import io.spine.chords.core.ComponentProps
import io.spine.chords.core.appshell.app
import io.spine.chords.core.keyboard.KeyModifiers.Companion.Ctrl
import io.spine.chords.core.keyboard.key
import io.spine.chords.core.keyboard.matches
import io.spine.chords.core.layout.DialogDisplayMode.Companion.DesktopWindow
import io.spine.chords.core.writeOnce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val cancelShortcutKey = Escape.key
private val submitShortcutKey = Ctrl(Enter.key)

/**
 * A base class for creating modal dialog windows, e.g. ones for displaying,
 * entering or editing some information, making decisions, etc.
 *
 * ## Implementing dialogs
 *
 * Note that an implementation of this class has to add a companion object of
 * type [DialogSetup]. Here's an example of how a custom dialog can be created:
 *
 * ```
 * public class MyDialog : Dialog() {
 *     public companion object : DialogSetup<MyDialog>({ MyDialog() })
 *
 *     init {
 *         // Set up own dialog's properties if/as needed.
 *         dialogWidth = 650.dp
 *         dialogHeight = 400.dp
 *
 *         // Include the Submit/Cancel buttons
 *         submitAvailable = true
 *         cancelAvailable = true
 *     }
 *
 *     override val title: String = "My Dialog"
 *
 *     @Composable
 *     override fun contentSection() {
 *         // Arbitrary composable dialog's content can be included here.
 *     }
 *
 *     override suspend fun submitContent(): Boolean {
 *         // Invoked when the dialog is "submitted" by the user by pressing the
 *         // OK button. Inspect any data entered in the dialog, and perform
 *         // the respective dialog's action that it was designed for here.
 *
 *         // Return `true`, if the dialog has performed the action it was made
 *         // for, and can be closed now.
 *         return true
 *     }
 * }
 * ```
 *
 * ## Using dialogs
 *
 * Once a dialog class like the one in the section above been implemented, it is
 * possible to display the respective dialog like this (if no dialog property
 * customizations are needed):
 *
 * ```
 *     MyDialog.open()
 * ```
 *
 * Alternatively, if you need to specify any properties for the dialog that
 * should be displayed with this call, it can be done like this:
 * ```
 *     MyDialog.open {
 *        dialogProperty1 = value1
 *        dialogProperty2 = value2
 *     }
 * ```
 *
 * The dialog will be closed automatically, when the "Cancel" button is pressed,
 * or when the "OK" button is pressed (and [submitContent] returns `true`).
 *
 * ## Predefined dialog actions
 *
 * The [Dialog] base class provides two built-in dialog-related actions, which
 * can be useful for many dialogs, and basically represent two ways of
 * completing the dialog's usage:
 *
 * - Submitting the dialog. This typically implies that a user has entered
 *   some data in the dialog, according to its purpose, and is ready to proceed
 *   with the actual function that the dialog was created for (e.g., adding or
 *   editing some data, etc.). Setting the [submitAvailable] property to `true`
 *   adds the Submit button to the dialog (labeled "OK" by default).
 *
 *   Pressing the Submit button invokes the [submitContent] method, which can be
 *   implemented by the actual dialog's subclass to perform the required action.
 *   If [submitContent] completes with a return value of `true`, the dialog is
 *   then closed.
 *
 *   If [submitAvailable] is `true`, the user also has an ability to trigger
 *   form submission by pressing the Ctrl+Enter key combination.
 *
 * - Cancelling the dialog. This corresponds to the usage scenario when the user
 *   has opened the dialog by mistake and wants to close it without performing
 *   any actions. This basically just closes the dialog. Setting the
 *   [cancelAvailable] property to `true` adds the respective Cancel button.
 *
 *   If [cancelAvailable] is `true`, the user can also cancel the dialog by
 *   pressing the Escape key.
 *
 * ## Customizing dialog's buttons
 *
 * The text for the optional Submit and Cancel buttons, which can be displayed
 * as described in the "Predefined dialog actions" above, can be customized
 * using the [submitButtonText] and [cancelButtonText] properties.
 *
 * The set of buttons within the buttons section can be customized by overriding
 * the [buttons] method.
 *
 * You can in particular choose to implement custom Submit and Cancel buttons.
 * In this case, to ensure that these buttons perform the same actions as the
 * original ones, make sure that they invoke the dialog's [submit] and [cancel]
 * methods respectively.
 *
 * ## Display modes
 *
 * The way that the dialog is displayed can be customized using the
 * [displayMode] property. By default, a dialog is displayed as a separate
 * desktop window (with a [DesktopWindow] value of [displayMode]), and it is
 * possible to make the dialog to be displayed in a "lightweight" mode, where
 * it is displayed as a modal popup within the existing desktop window, with
 * any currently displayed window's content covered with an overlay to ensure
 * that nothing except the dialog's popup can be interacted within this window.
 */
@Suppress(
    // All functions are apparently appropriate in the class.
    "TooManyFunctions"
)
public abstract class Dialog : Component() {

    /**
     * The text to be the title of the dialog.
     */
    public abstract val title: String

    /**
     * The label for the dialog's Submit button.
     *
     * The default value is `OK`.
     */
    protected open var submitButtonText: String = "OK"

    /**
     * The label for the dialog's Cancel button.
     *
     * The default value is `Cancel`.
     */
    protected open var cancelButtonText: String = "Cancel"

    /**
     * The width of the dialog.
     *
     * The default value is `700.dp`.
     */
    public var dialogWidth: Dp = 700.dp

    /**
     * The height of the dialog.
     *
     * The default value is `450.dp`.
     */
    public var dialogHeight: Dp = 450.dp

    /**
     * Specifies appearance-related parameters.
     */
    public var look: Look = Look()

    /**
     * An object allowing adjustments of visual appearance parameters.
     *
     * @param padding The padding applied to the entire content of the dialog.
     * @param titlePadding The padding applied to the title of the dialog.
     * @param buttonsPanelPadding The padding applied to the buttons panel of
     *   the dialog.
     * @param buttonsSpacing The space between the buttons of the dialog.
     */
    public data class Look(
        public var padding: PaddingValues = PaddingValues(24.dp),
        public var titlePadding: PaddingValues = PaddingValues(bottom = 16.dp),
        public var buttonsPanelPadding: PaddingValues = PaddingValues(top = 24.dp),
        public var buttonsSpacing: Dp = 12.dp
    )

    /**
     * Specifies the way that the dialog is displayed.
     *
     * The following two display modes are supported:
     * - [DesktopWindow][DialogDisplayMode.DesktopWindow] — a dialog is
     *   displayed in a separate desktop window.
     * - [Lightweight][DialogDisplayMode.Lightweight] — a dialog is displayed
     *   as a lightweight modal popup within the current desktop window.
     */
    public var displayMode: DialogDisplayMode = DesktopWindow

    /**
     * A suspending callback, which is invoked upon the dialog's Cancel button
     * click before the dialog is closed.
     *
     * The callback should return `true` in order for the dialog to proceed with
     * closing, and `false` to prevent the dialog from being closed.
     *
     * The default implementation just returns `true`, and one of the typical
     * usage scenarios would be to display the confirmation dialog.
     *
     * For example, in order for the custom `MyDialog` implementation to display
     * a confirmation before the dialog is closed upon pressing Cancel, the
     * following can be done:
     * ```
     * public class MyDialog : Dialog() {
     *     public companion object : DialogSetup<MyDialog>({ MyDialog() })
     *
     *     init {
     *         onBeforeCancel = {
     *             ConfirmationDialog.showConfirmation {
     *                 message = "Are you sure you want to cancel the dialog?"
     *             }
     *         }
     *         ...
     *     }
     * ```
     */
    public var onBeforeCancel: suspend () -> Boolean = { true }

    /**
     * A suspending callback, which is invoked upon the dialog's Submit button
     * click before the [submitContent] function is called.
     *
     * The callback should return `true` in order for the dialog to proceed with
     * submission. Returning `false` prevents submission from happening and
     * retains the dialog open in its current state.
     *
     * The default implementation just returns `true`, and one of the typical
     * usage scenarios would be to display the confirmation dialog, which might
     * be required when the dialog's submission leads to a critical
     * irreversible modification.
     *
     * For example, here's an example of a custom `MyDialog` implementation
     * displaying a confirmation upon pressing the Submit button, right before
     * invoking the [submitContent] method:
     *
     * ```
     * public class MyDialog : Dialog() {
     *     public companion object : DialogSetup<MyDialog>({ MyDialog() })
     *
     *     init {
     *         onBeforeSubmit = {
     *             ConfirmationDialog.showConfirmation {
     *                 message = "Are you sure you want to proceed?"
     *                 description = "This action is irreversible!"
     *             }
     *         }
     *         ...
     *     }
     * ```
     */
    public var onBeforeSubmit: suspend () -> Boolean = { true }

    /**
     * Specifies whether the dialog is currently in progress of submission
     * (an asynchronous process upon pressing the "Submit" button has been
     * initiated but not completed yet).
     */
    internal var submissionInProgress: Boolean by mutableStateOf(false)

    /**
     * This property is automatically set to `true` by the application, if
     * this dialog is a bottom-most one (the first dialog that was invoked in
     * the sequence of nested dialogs display).
     *
     * If it's a nested dialog of another dialog, this property is set
     * to `false`.
     */
    internal var isBottomDialog: Boolean = true

    /**
     * A dialog nested in this one (the one that was displayed while this one
     * was open).
     */
    internal var nestedDialog by mutableStateOf<Dialog?>(null)

    /**
     * Coroutine scope for launching asynchronous execution of actions initiated
     * by the dialog's controls.
     */
    private var coroutineScope: CoroutineScope by writeOnce()

    /**
     * Specifies whether the "Submit" button should be displayed.
     */
    protected var submitAvailable: Boolean = false
    internal var submitAvailableInternal: Boolean
        get() = submitAvailable
        set(value) { submitAvailable = value }

    /**
     * Specifies whether the "Cancel" button should be displayed.
     */
    protected var cancelAvailable: Boolean = false
    internal var cancelAvailableInternal: Boolean
        get() = cancelAvailable
        set(value) { cancelAvailable = value }

    /**
     * Displays the modal dialog.
     *
     * NOTE: this method is mainly useful only in cases when you need to
     * instantiate a dialog's instance separately from displaying it for
     * some reason.
     *
     * In most cases the most convenient way to open a dialog would be using its
     * companion object's [open][DialogSetup.open] method instead, like this:
     *
     * ```
     *     MyDialog.open()
     * ```
     */
    public fun open() {
        app.ui.openDialog(this)
    }

    /**
     * Closes the dialog while ignoring any data that might have been
     * possibly entered in the dialog currently.
     */
    internal fun close() {
        app.ui.closeDialog(this)
    }

    /**
     * Submits the dialog and closes it upon a successful submission, which is
     * equivalent to pressing the "Submit" button.
     *
     * This in particular involves invoking the [onBeforeSubmit] callback before
     * performing the submission, and then invoking the [submitContent] method,
     * which actually performs a dialog-specific submission procedure.
     */
    public fun submit() {
        coroutineScope.launch {
            if (onBeforeSubmit()) {

                submissionInProgress = true
                val submittedSuccessfully = try {
                    submitContent()
                } finally {
                    submissionInProgress = false
                }

                if (submittedSuccessfully) {
                    close()
                }
            }
        }
    }

    /**
     * Cancels the dialog, which is equivalent to pressing the "Cancel" button.
     *
     * This means invoking the [onBeforeCancel] callback, and closing the dialog
     * if the callback didn't prevent closing.
     */
    public fun cancel() {
        coroutineScope.launch {
            if (onBeforeCancel()) {
                close()
            }
        }
    }

    /**
     * Renders the dialog window according to the current [displayMode].
     *
     * Custom dialog implementations should implement the [contentSection] method
     * in order to specify the content that needs to be displayed inside
     * the dialog window.
     */
    @Composable
    protected final override fun content() {
        coroutineScope = rememberCoroutineScope()
        displayMode.dialogWindow(this)
    }

    /**
     * Renders the dialog window's content, which is everything in the dialog
     * except the window's frame and the window's title (which are rendered
     * by [DialogDisplayMode]).
     *
     * By default, this renders the content section (see [contentSection]),
     * and the buttons section below it (see [buttonsSection]).
     *
     * @see contentSection
     * @see buttonsSection
     */
    @Composable
    protected open fun ColumnScope.windowContent() {
        Column(Modifier.weight(1F)) {
            contentSection()
        }
        buttonsSection()
    }

    /**
     * Exposes the [windowContent] within an `internal` visibility scope.
     */
    context(ColumnScope)
    @Composable
    internal fun windowContentInternal() {
        windowContent()
    }

    /**
     * Renders the dialog window's content.
     */
    @Composable
    protected abstract fun contentSection()

    /**
     * Submits the dialog's content, which means performing whatever action is
     * appropriate for the actual dialog's implementation, based on the content
     * entered by the user in the dialog.
     *
     * This action is executed when the user completes the dialog
     * by pressing the Submit button.
     *
     * If this method returns `true` the dialog will be closed. As an example,
     * this would be a typical case when the method has identified that the data
     * entered in the dialog is complete and valid, and the respective dialog's
     * action that it was designed for was performed successfully.
     *
     * On the other hand, this method can return `false` to prevent closing the
     * dialog, which can in particular be useful when the user has entered
     * incomplete or invalid data.
     *
     * @return `true` if the dialog should be closed after this method returns,
     *   and `false` if it has to remain open.
     */
    protected abstract suspend fun submitContent(): Boolean

    /**
     * Renders the dialog's buttons section, which is displayed
     * below [contentSection].
     */
    @Composable
    protected open fun buttonsSection() {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(look.buttonsPanelPadding),
            horizontalArrangement = End,
            verticalAlignment = Bottom
        ) {
            Row(
                horizontalArrangement = spacedBy(look.buttonsSpacing)
            ) {
                buttons()
            }
        }
    }

    /**
     * Exposes [buttonsSection] within the `internal` visibility scope.
     */
    @Composable
    internal fun buttonsSectionInternal() {
        buttonsSection()
    }

    /**
     * Renders the buttons, which need to be displayed in the dialog's
     * buttons section.
     *
     * This method can be overridden to provide a custom set of buttons,
     * which are needed for a particular dialog's implementation.
     */
    @Composable
    protected fun buttons() {
        if (cancelAvailable) {
            DialogButton(cancelButtonText) {
                cancel()
            }
        }
        if (submitAvailable) {
            DialogButton(submitButtonText, !submissionInProgress) {
                submit()
            }
        }
    }

    /**
     * A part of internal application's machinery for displaying nested dialogs.
     *
     * Technically, displays an inner dialog inside of this one. Should not be
     * invoked directly as it's invoked automatically by
     * [AppWindow.openDialog][io.spine.chords.core.appshell.AppWindow.openDialog],
     * which in turn is invoked by the [open] method.
     *
     * @see closeNestedDialog
     * @see open
     */
    internal fun openNestedDialog(dialog: Dialog) {
        check(nestedDialog != dialog) { "This dialog is already open." }
        if (nestedDialog == null) {
            nestedDialog = dialog
        } else {
            nestedDialog!!.openNestedDialog(dialog)
        }
    }

    /**
     * A part of internal application's machinery for displaying nested dialogs.
     *
     * This method complements [openNestedDialog] for this purpose. See its
     * description for details.
     *
     * @see openNestedDialog
     */
    internal fun closeNestedDialog(dialog: Dialog) {
        checkNotNull(nestedDialog) { "This dialog is not displayed currently." }
        if (dialog == nestedDialog) {
            check(dialog.nestedDialog == null) {
                "Cannot close a dialog while it has a nested dialog open."
            }
            nestedDialog = null
        } else {
            nestedDialog!!.closeNestedDialog(dialog)
        }
    }
}

/**
 * Defines the way that a dialog is displayed on the screen (e.g. as a separate
 * desktop window, or as a lightweight modal popup).
 */
public abstract class DialogDisplayMode {

    /**
     * Renders the dialog window according to the display mode defined by this
     * object.
     *
     * @param dialog The [Dialog] that is being displayed.
     */
    @Composable
    public abstract fun dialogWindow(dialog: Dialog)

    public companion object {

        /**
         * A dialog display mode, which makes each dialog to be displayed
         * in its own desktop window.
         */
        public val DesktopWindow: DialogDisplayMode = DesktopWindowDisplayMode()

        /**
         * A dialog display platform that makes the dialogs to be displayed
         * in a "lightweight" way — without displaying a new desktop window,
         * as a modal popup inside the current desktop window.
         */
        public val Lightweight: DialogDisplayMode = LightweightDisplayMode()
    }
}

/**
 * A [DialogDisplayMode] implementation, which ensures displaying a dialog
 * as a separate desktop window.
 */
internal class DesktopWindowDisplayMode(
    private val resizable: Boolean = false
) : DialogDisplayMode() {

    @Composable
    override fun dialogWindow(dialog: Dialog) {
        DialogWindow(
            title = dialog.title,
            resizable = resizable,
            state = DialogState(
                size = DpSize(dialog.dialogWidth, dialog.dialogHeight)
            ),
            onCloseRequest = { dialog.close() },
            onKeyEvent = { event ->
                if (dialog.cancelAvailableInternal && event matches cancelShortcutKey.down) {
                    dialog.cancel()
                }
                if (dialog.submitAvailableInternal && event matches submitShortcutKey.up) {
                    dialog.submit()
                }
                false
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dialog.look.padding),
                ) {
                    dialog.windowContentInternal()
                    dialog.nestedDialog?.Content()
                }
            }
        }
    }
}

/**
 * A [PopupPositionProvider], which makes a lightweight popup to appear at
 * the window's center.
 */
private val centerWindowPositionProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = Zero
}

/**
 * A [DialogDisplayMode] implementation, which ensures displaying a dialog
 * as a lightweight modal popup inside the current desktop window.
 *
 * @param backdropColor The color of the surface that covers the entire content
 *   of the current desktop window behind the dialog's modal popup displayed in
 *   this window.
 */
internal class LightweightDisplayMode(
    private val backdropColor: Color = Black.copy(alpha = 0.5f)
) : DialogDisplayMode() {

    @Composable
    override fun dialogWindow(dialog: Dialog) {
        Popup(
            popupPositionProvider = centerWindowPositionProvider,
            properties = PopupProperties(focusable = true),
            onPreviewKeyEvent = { false },
            onKeyEvent = cancelShortcutHandler {
                if (dialog.cancelAvailableInternal) {
                    dialog.cancel()
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backdropColor),
                contentAlignment = Center
            ) {
                val modifier = if (dialog.isBottomDialog) {
                    Modifier.pointerInput(dialog) {
                        detectTapGestures(onPress = {})
                    }
                } else {
                    Modifier
                }
                Box(modifier = modifier) {
                    dialogFrame(dialog)
                }
            }
        }
    }

    @Composable
    private fun dialogFrame(dialog: Dialog) {
        Column(
            modifier = Modifier
                .clip(shapes.large)
                .size(dialog.dialogWidth, dialog.dialogHeight)
                .background(colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dialog.look.padding),
            ) {
                DialogTitle(dialog.title, dialog.look.titlePadding)
                dialog.windowContentInternal()
                dialog.nestedDialog ?.Content()
            }
        }
    }

    /**
     * Creates a key event handler function that executes a provided [cancelHandler]
     * callback whenever the `Escape` key is pressed.
     */
    private fun cancelShortcutHandler(cancelHandler: () -> Unit): (KeyEvent) -> Boolean = { event ->
        if (event matches cancelShortcutKey.down) {
            cancelHandler()
            true
        } else {
            false
        }
    }
}

/**
 * A class that should be used for creating companion objects of
 * [Dialog] implementations.
 *
 * Adding a companion object of this class ensures that the respective custom
 * dialog (say `MyDialog`) can be displayed with a simple call like this:
 *
 * ```
 *     MyDialog.open()
 * ```
 *
 * Under the hood, such an expression technically means creating an instance of
 * dialog [D] (`MyDialog` in this case), and then invoking the
 * [open][Dialog.open] method on that instance (note that it's a separate method
 * from this one).
 *
 * See the [Dialog]'s documentation for a usage example in context of
 * a dialog implementation.
 *
 * @constructor Creates an object to serve as a companion object.
 * @param createInstance A lambda that should create an instance of [D].
 *
 * @see Dialog
 */
public open class DialogSetup<D: Dialog>(
    createInstance: () -> D
) : AbstractComponentSetup(createInstance) {

    /**
     * Displays the modal dialog [D].
     *
     * Here's an example:
     * ```
     *    MyDialog.open()
     *
     *    // or...
     *
     *    MyDialog.open {
     *        dialogProp1 = value1
     *        dialogProp2 = value2
     *    }
     * ```
     *
     * @param props A lambda, which configures (assigns)
     *   the dialog's properties.
     */
    public fun open(props: ComponentProps<D>? = null): D {
        val dialog = create(config = props)
        dialog.open()
        return dialog
    }
}


/**
 * The title of the dialog.
 *
 * @param text The text to be title.
 */
@Composable
private fun DialogTitle(
    text: String,
    padding: PaddingValues
) {
    Text(
        modifier = Modifier.padding(padding),
        text = text,
        style = typography.headlineLarge
    )
}

/**
 * The action button of the dialog.
 *
 * @param label The label of the button.
 * @param enabled Specifies whether the button should appear and behave as
 *   an enabled one.
 * @param onClick The callback triggered on the button click.
 */
@Composable
private fun DialogButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(onClick = onClick, enabled = enabled) {
        Row(
            verticalAlignment = CenterVertically
        ) {
            Text(label)
        }
    }
}
