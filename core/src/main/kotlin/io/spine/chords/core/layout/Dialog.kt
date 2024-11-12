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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key.Companion.Enter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyDown
import androidx.compose.ui.input.key.type
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
import io.spine.chords.core.Component
import io.spine.chords.core.keyboard.KeyModifiers.Companion.Ctrl
import io.spine.chords.core.keyboard.key
import io.spine.chords.core.keyboard.on
import java.awt.event.KeyEvent.VK_ESCAPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The base class for creating a dialog box for editing and submitting data.
 *
 * Note that an [onCloseRequest] callback is triggered when the user has
 * finished using the dialog, and it needs to be closed. The container where
 * the dialog is placed is responsible for hiding the dialog (excluding it from
 * the composition) upon this event.
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
     */
    public var onCloseRequest: (() -> Unit)? = null

    /**
     * A callback used to confirm that the user wants to cancel the dialog.
     *
     * This callback is triggered when the user closes the dialog
     * by pressing the cancel button.
     */
    public var onCancelConfirmation: (() -> Unit)? = null

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

    /**
     * Creates the form content the dialog consists.
     */
    @Composable
    protected abstract fun formContent()

    /**
     * Submits the dialog's form.
     *
     * This action is executed when the user submits the dialog
     * by pressing the confirmation button.
     *
     * `onCloseRequest` is triggerred right after the `submitForm` action,
     * so it is not needed to configure it manually.
     */
    protected abstract suspend fun submitForm(): Boolean

    public var cancelConfirmationDialog: CancelConfirmationDialog? = { onConfirm, onCancel ->
        ConfirmCancellationDialog {
            onCloseRequest = onConfirm
            onCancelConfirmation = onCancel
        }.Content()
    }

    public var onCancel: () -> Unit = {}

    /**
     * Creates the dialog content composition.
     */
    @Composable
    protected override fun content() {
        val cancelConfirmationShown = remember { mutableStateOf(false) }
        Popup(
            popupPositionProvider = centerWindowPositionProvider,
            onDismissRequest = onCancel,
            properties = PopupProperties(focusable = true),
            onPreviewKeyEvent = { false },
            onKeyEvent = cancelOnEscape {
                if (cancelConfirmationDialog != null) {
                    cancelConfirmationShown.value = true
                } else {
                    onCancel()
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(onCancel) {
                        detectTapGestures(onPress = {
                            if (cancelConfirmationDialog == null) {
                                onCancel()
                            }
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.pointerInput(onCancel) {
                        detectTapGestures(onPress = {})
                    }
                ) {
                    onCancelConfirmation = {
                        cancelConfirmationShown.value = true
                    }
                    dialogContent()
                }
            }
            if (cancelConfirmationShown.value && cancelConfirmationDialog != null) {
                CancelConfirmationDialogContainer(
                    onCancel = { cancelConfirmationShown.value = false },
                    onConfirm = { onCancel() },
                    content = cancelConfirmationDialog!!
                )
            }
        }
    }

    @Composable
    private fun dialogContent() {
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .size(dialogWidth, dialogHeight)
                .background(MaterialTheme.colorScheme.background),
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
                            submit(coroutineScope)
                        }
                ) {
                    formContent()
                }
                DialogButtons(
                    confirmButtonText, { submit(coroutineScope) },
                    cancelButtonText, { cancel() },
                    config.buttonsPanelPadding,
                    config.buttonsSpacing
                )
            }
        }
    }

    /**
     * Cancels the dialog.
     *
     * Invokes [onCancelConfirmation] if specified for the dialog,
     * or [onCloseRequest] otherwise.
     */
    private fun cancel() {
        if (onCancelConfirmation != null) {
            onCancelConfirmation?.invoke()
        } else {
            onCloseRequest?.invoke()
        }
    }

    /**
     * Submits the dialog form and invokes [onCloseRequest]
     * if the form was successfully submitted.
     */
    private fun submit(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            if (submitForm()) {
                onCloseRequest?.invoke()
            }
        }
    }
}

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
 * Returns a function that executes a provided `onCancel` callback
 * whenever the `Escape` keyboard button is pressed.
 */
private fun cancelOnEscape(onCancel: () -> Unit): ((KeyEvent) -> Boolean) =
    {
        if (it.type == KeyDown && it.awtEventOrNull?.keyCode == VK_ESCAPE) {
            onCancel()
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

/**
 * The container for the cancel confirmation dialog of the [ModalWindow] component.
 *
 * This dialog confirms or denies the intention of the user to close the main modal window.
 *
 * @param onCancel The callback triggered on the dialog cancellation.
 * @param onConfirm The callback triggered on the confirmation to cancel the main modal window.
 * @param content The content to display as a dialog.
 */
@Composable
private fun CancelConfirmationDialogContainer(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    content: CancelConfirmationDialog
) {
    Popup(
        popupPositionProvider = centerWindowPositionProvider,
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { false }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Box {
                content(onConfirm, onCancel)
            }
        }
    }
}

/**
 * A type of the cancel confirmation dialog.
 *
 * The cancel confirmation dialog prompts the user to confirm whether they want
 * to close the modal. It receives two parameters:
 * - `onConfirm`: A callback triggered when the user confirms the cancellation.
 * - `onCancel`: A callback triggered when the user cancels the cancellation
 *   (i.e., decides not to close the modal).
 */
public typealias CancelConfirmationDialog =
        @Composable BoxScope.(onConfirm: () -> Unit, onCancel: () -> Unit) -> Unit
