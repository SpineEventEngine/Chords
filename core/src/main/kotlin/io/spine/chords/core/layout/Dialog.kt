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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.input.key.Key.Companion.Enter
import androidx.compose.ui.input.key.Key.Companion.Escape
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
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
import io.spine.chords.core.keyboard.on
import kotlinx.coroutines.launch

/**
 * The base class for creating a modal dialog window, e.g. for editing and
 * submitting data.
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
 *     }
 *
 *     override val title: String = "My Dialog"
 *
 *     @Composable
 *     override fun dialogContent() {
 *         // Arbitrary composable dialog's content can be included here.
 *     }
 *
 *     override suspend fun submitForm(): Boolean {
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
 * Once such class has been implemented, it is possible to display
 * the respective dialog like this (if no dialog property customizations
 * are needed):
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
 * or when the "OK" button is pressed (and [submitForm] returns `true`).
 */
public abstract class Dialog : Component() {

    /**
     * The text to be the title of the dialog.
     */
    public abstract val title: String

    /**
     * The label for the dialog's confirmation button.
     *
     * The default value is `OK`.
     */
    public var confirmButtonText: String = "OK"

    /**
     * The label for the dialog's cancel button.
     *
     * The default value is `Cancel`.
     */
    public var cancelButtonText: String = "Cancel"

    /**
     * A callback that should be handled to close the dialog (exclude it from
     * the composition).
     *
     * This callback is triggered when the user closes the dialog or after
     * successful submission.
     *
     * // TODO:2024-11-12:dmitry.pikhulya: Remove this property after
     *      introducing native dialogs instead of popup-based ones.
     */
    internal var onCloseRequest: (() -> Unit)? = { close() }

//    /**
//     * A callback used to confirm that the user wants to cancel the dialog.
//     *
//     * This callback is triggered when the user closes the dialog
//     * by pressing the cancel button.
//     */
//    private var onCancelConfirmation: (() -> Unit)? = null

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
     * The [DialogConfig] property that allows adjustments
     * to visual appearance settings.
     */
    public var config: DialogConfig = DialogConfig()

    internal var isBottomDialog: Boolean = true

    internal var nestedDialog by mutableStateOf<Dialog?>(null)

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
     *         confirmCancellation = ConfirmationDialog.askAndAwait {
     *             message = "Are you sure you want to close the dialog?"
     *             description = "Any entered data will be lost in this case."
     *             confirmButtonText = "Discard changes"
     *             cancelButtonText = "Continue editing"
     *         }
     *
     *         // Other dialog's properties can be set if needed as well.
     *         ...
     *     }
     * ```
     */
    public var confirmCancellation: suspend () -> Boolean = { true }

    /**
     * A suspending callback, which is invoked upon the dialog's Submit button
     * click before the dialog is closed.
     *
     * The callback should return `true` in order for the dialog to proceed with
     * submission, and `false` to prevent the submission.
     */
    public var confirmSubmission: suspend () -> Boolean = { true }

//    private val cancelConfirmationDialog: CancelConfirmationDialog? = { onConfirm, onCancel ->
//        onCloseRequest = onConfirm
//        onCancelConfirmation = onCancel
//        ConfirmCancellationDialog().Content()
//    }

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
    private fun close() {
        app.ui.closeDialog(this)
    }

    /**
     * Renders the form content the dialog consists.
     */
    @Composable
    protected abstract fun formContent()

    /**
     * Submits the dialog's form.
     *
     * This action is executed when the user submits the dialog
     * by pressing the confirmation button.
     *
     * If this method returns `true` the dialog will be closed. As an example
     * this would be a typical case when the method has identified that the data
     * entered in the dialog is complete and valid, and the respective dialog's
     * action that it was designed for was performed successfully. On the other
     * hand, this method can return `false` to prevent closing the dialog, which
     * can in particular be useful when the user has entered incomplete or
     * invalid data.
     *
     * @return `true` if the dialog should be closed after this method returns,
     *   and `false` if it has to remain open.
     */
    protected abstract suspend fun submitForm(): Boolean

    /**
     * Creates the dialog content composition.
     */
    @Composable
    protected override fun content() {
        lightweightPlatform(::close, { dialogFrame() }, nestedDialog, isBottomDialog)
    }

    internal fun openNestedDialog(dialog: Dialog) {
        check(nestedDialog != dialog) { "This dialog is already open." }
        if (nestedDialog == null) {
            nestedDialog = dialog
        } else {
            nestedDialog!!.openNestedDialog(dialog)
        }
    }

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


//    private val cancelConfirmationShown = remember { mutableStateOf(false) }
//
//    private fun showCancelConfirmation() {
//        cancelConfirmationShown.value = true
//    }
//
//    private fun hideCancelConfirmation() {
//        cancelConfirmationShown.value = false
//    }

    /**
     * Renders the dialog's frame, which makes up main elements that are common
     * for all dialogs, including dialog's title and buttons, while delegating
     * the rendering of the dialog's content to the actual dialog's
     * implementation (via the [formContent] method).
     */
    @Composable
    private fun dialogFrame() {
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .size(dialogWidth, dialogHeight)
                .background(colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(config.padding),
            ) {
                val coroutineScope = rememberCoroutineScope()
                DialogTitle(title, config.titlePadding)
                Column(
                    Modifier.weight(1F)
                        .on(Ctrl(Enter.key).up) {
                            coroutineScope.launch { handleSubmitClick() }
                        }
                ) {
                    formContent()
                }
                DialogButtons(
                    confirmButtonText, { coroutineScope.launch { handleSubmitClick() } },
                    cancelButtonText, { coroutineScope.launch { handleCancelClick() } },
                    config.buttonsPanelPadding,
                    config.buttonsSpacing
                )
            }
        }
    }

    protected open suspend fun handleSubmitClick() {
        if (submitForm()) {
            close()
        }
    }

    protected open suspend fun handleCancelClick() {
        if (confirmCancellation()) {
            close()
        }
    }

    private suspend fun confirmCancellation2(): Boolean {
        return ConfirmationDialog.askAndAwait {
            message = "Are you sure you want to close the dialog?"
            description = "Any entered data will be lost in this case."
            confirmButtonText = "Discard changes"
            cancelButtonText = "Continue editing"
        }
    }
}

@Composable
private fun lightweightPlatform(
    close: () -> Unit,
    dialogContent: @Composable () -> Unit,
    nestedDialog: Dialog?,
    isBottomDialog: Boolean
) {
    if (isBottomDialog) {
        Popup(
            popupPositionProvider = centerWindowPositionProvider,
            onDismissRequest = close,
            properties = PopupProperties(focusable = true),
            onPreviewKeyEvent = { false },
            onKeyEvent = escapePressHandler {
                //                if (cancelConfirmationDialog != null) {
                //                    showCancelConfirmation()
                //                } else {
                close()
                //                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black.copy(alpha = 0.5f))
                //                    .pointerInput(::close) {
                //                        detectTapGestures(onPress = {
                //                            if (cancelConfirmationDialog == null) {
                //                                close()
                //                            }
                //                        })
                //                    }
                ,
                contentAlignment = Center
            ) {
                Box(
                    modifier = Modifier.pointerInput(close) {
                        detectTapGestures(onPress = {})
                    }
                ) {
                    //                    onCancelConfirmation = {
                    //                        showCancelConfirmation()
                    //                    }
                    dialogContent()
                }
            }
        }
    } else {
        Popup(
            popupPositionProvider = centerWindowPositionProvider,
            properties = PopupProperties(focusable = true),
            onPreviewKeyEvent = { false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Black.copy(alpha = 0.5f)),
                contentAlignment = Center
            ) {
                Box {
                    dialogContent()
                }
            }
        }
    }

    nestedDialog ?.Content()

//            if (nestedDialog != null) {
//
//                CancelConfirmationDialogContainer(
//                    onCancel = { hideCancelConfirmation() },
//                    onConfirm = { close() },
//                    content = cancelConfirmationDialog
//                )
//            }

//            if (cancelConfirmationShown.value && cancelConfirmationDialog != null) {
//                CancelConfirmationDialogContainer(
//                    onCancel = { hideCancelConfirmation() },
//                    onConfirm = { close() },
//                    content = cancelConfirmationDialog
//                )
//            }
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


///**
// * A type of the cancel confirmation dialog.
// *
// * The cancel confirmation dialog prompts the user to confirm whether they want
// * to close the modal. It receives two parameters:
// * - `onConfirm`: A callback triggered when the user confirms the cancellation.
// * - `onCancel`: A callback triggered when the user cancels the cancellation
// *   (i.e., decides not to close the modal).
// */
//private typealias CancelConfirmationDialog =
//        @Composable BoxScope.(onConfirm: () -> Unit, onCancel: () -> Unit) -> Unit

/**
 * Configuration of the dialog, allowing adjustments
 * to visual appearance settings.
 *
 * @param padding The padding applied to the entire content of the dialog.
 * @param titlePadding The padding applied to the title of the dialog.
 * @param buttonsPanelPadding The padding applied to the buttons panel of the dialog.
 * @param buttonsSpacing The space between the buttons of the dialog.
 */
public data class DialogConfig(
    public var padding: PaddingValues = PaddingValues(24.dp),
    public var titlePadding: PaddingValues = PaddingValues(bottom = 16.dp),
    public var buttonsPanelPadding: PaddingValues = PaddingValues(top = 24.dp),
    public var buttonsSpacing: Dp = 12.dp
)


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
        style = MaterialTheme.typography.headlineLarge
    )
}

/**
 * The panel with control buttons of the dialog.
 */
@Composable
@Suppress("LongParameterList")
private fun DialogButtons(
    confirmButtonText: String,
    onConfirm: () -> Unit,
    cancelButtonText: String,
    onCancel: () -> Unit,
    padding: PaddingValues,
    buttonsSpacing: Dp
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(padding),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(buttonsSpacing)
        ) {
            DialogButton(cancelButtonText) {
                onCancel.invoke()
            }
            DialogButton(confirmButtonText)
            {
                onConfirm.invoke()
            }
        }
    }
}

/**
 * The action button of the dialog.
 *
 * @param label The label of the button.
 * @param onClick The callback triggered on the button click.
 */
@Composable
private fun DialogButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label)
        }
    }
}


/**
 * Creates a key event handler function that executes a provided [escHandler]
 * callback whenever the `Escape` key is pressed.
 */
private fun escapePressHandler(escHandler: () -> Unit): ((KeyEvent) -> Boolean) = { event ->
    if (event matches Escape.key.down) {
        escHandler()
        true
    } else {
        false
    }
}


/**
 * Provides a modal window setting that forces it
 * to appear at the center of the screen.
 */
private val centerWindowPositionProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset.Zero
}

///**
// * The container for the cancel confirmation dialog of the [Dialog] component.
// *
// * This dialog confirms or denies the intention of the user to close the main modal window.
// *
// * @param onCancel The callback triggered on the dialog cancellation.
// * @param onConfirm The callback triggered on the confirmation to cancel the main modal window.
// * @param content The content to display as a dialog.
// */
//@Composable
//private fun CancelConfirmationDialogContainer(
//    onCancel: () -> Unit,
//    onConfirm: () -> Unit,
//    content: CancelConfirmationDialog
//) {
//    Popup(
//        popupPositionProvider = centerWindowPositionProvider,
//        properties = PopupProperties(focusable = true),
//        onPreviewKeyEvent = { false }
//    ) {
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .background(Black.copy(alpha = 0.5f)),
//            contentAlignment = Center
//        ) {
//            Box {
//                content(onConfirm, onCancel)
//            }
//        }
//    }
//}

//
//@Composable
//private fun NestedDialogContainer(
//    onCancel: () -> Unit,
//    onConfirm: () -> Unit,
//    dialog: Dialog
//) {
//    Popup(
//        popupPositionProvider = centerWindowPositionProvider,
//        properties = PopupProperties(focusable = true),
//        onPreviewKeyEvent = { false }
//    ) {
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .background(Black.copy(alpha = 0.5f)),
//            contentAlignment = Center
//        ) {
//            Box {
////                content(onConfirm, onCancel)
//            }
//        }
//    }
//}
