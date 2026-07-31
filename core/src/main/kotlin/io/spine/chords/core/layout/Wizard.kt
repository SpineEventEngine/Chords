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

package io.spine.chords.core.layout

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Alignment.Companion.Start
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key.Companion.DirectionLeft
import androidx.compose.ui.input.key.Key.Companion.DirectionRight
import androidx.compose.ui.input.key.Key.Companion.Enter
import androidx.compose.ui.unit.dp
import io.spine.chords.core.Component
import io.spine.chords.core.keyboard.KeyModifiers.Companion.Alt
import io.spine.chords.core.keyboard.KeyModifiers.Companion.Ctrl
import io.spine.chords.core.keyboard.key
import io.spine.chords.core.keyboard.on
import io.spine.chords.core.layout.WizardContentSize.maxHeight
import io.spine.chords.core.layout.WizardContentSize.minHeight
import io.spine.chords.core.layout.WizardContentSize.width
import io.spine.chords.core.primitive.HorizontalScrollbar
import io.spine.chords.core.primitive.VerticalScrollbar

/**
 * Bounds of the wizard's content pane.
 */
private object WizardContentSize {
    val width = 670.dp
    val minHeight = 400.dp
    val maxHeight = 700.dp
}

/**
 * The base class for creating a multi-step form component known as a wizard.
 *
 * To create a concrete wizard you need to extend the class
 * and override all abstract methods that configure the data needed for the wizard.
 *
 * Note that an [onCloseRequest] callback is triggered when the user has
 * finished using the wizard, and it needs to be closed. The container where
 * the wizard is placed is responsible for hiding the wizard (excluding it from
 * the composition) upon this event.
 *
 * The wizard can be closed along two distinct paths, and they invoke different
 * callbacks:
 * - The user presses the "Cancel" button, which invokes [cancel]. It consults
 *   the [onBeforeCancel] callback, and closes the wizard only if that callback
 *   permits closing.
 * - The wizard's submission succeeds, and the wizard's implementation invokes
 *   [close] directly. This path doesn't consult [onBeforeCancel].
 *
 * Both paths end up invoking [onCloseRequest] when the wizard is actually
 * closed, so a host that only needs to remove the wizard from the composition
 * can keep handling [onCloseRequest] alone.
 */
@Stable
@Suppress(
    // All functions are apparently appropriate in the class.
    "TooManyFunctions"
)
public abstract class Wizard : Component() {

    /**
     * The text to be the title of the wizard, or `null`, if the wizard's title
     * shouldn't be displayed at all.
     */
    protected abstract val title: String?

    /**
     * A callback that should be handled to close the wizard (exclude it from
     * the composition).
     *
     * This callback is triggered when the user closes the wizard or after
     * successful submission.
     *
     * It is triggered only when the wizard is actually being closed, so a
     * cancellation that was rejected by [onBeforeCancel] doesn't trigger it.
     *
     * @see onBeforeCancel
     */
    public var onCloseRequest: (() -> Unit)? = null

    /**
     * A suspending callback, which is invoked upon the wizard's "Cancel" button
     * click before the wizard is closed.
     *
     * The callback should return `true` in order for the wizard to proceed with
     * closing, and `false` to prevent the wizard from being closed. The wizard
     * stays in the composition while the callback is suspended, so any data
     * that has been entered in the wizard's pages is retained, both while the
     * callback is pending and if it rejects closing.
     *
     * The default implementation just returns `true`, and one of the typical
     * usage scenarios would be to display the confirmation dialog.
     *
     * Note that this callback is invoked only when the user cancels the wizard,
     * and it is not invoked when the wizard is closed after a successful
     * submission (which happens by invoking [close] directly). A confirmation
     * displayed in this callback therefore doesn't appear after the wizard's
     * operation has succeeded.
     *
     * For example, in order for the custom `MyWizard` implementation to display
     * a confirmation before the wizard is closed upon pressing "Cancel", the
     * following can be done:
     * ```
     * public class MyWizard : Wizard() {
     *
     *     init {
     *         onBeforeCancel = {
     *             ConfirmationDialog.showConfirmation {
     *                 message = "Are you sure you want to discard the data?"
     *             }
     *         }
     *         ...
     *     }
     * ```
     *
     * @see cancel
     */
    public var onBeforeCancel: suspend () -> Boolean = { true }

    public var currentPage: WizardPage
        get() = pages[currentPageIndex]
        set(page) {
            val pageIndex = pages.indexOf(page)
            check(pageIndex != -1) { "Such page does not belong to the wizard" }
            currentPageIndex = pageIndex
        }

    /**
     * Specifies whether the wizard is in the submission state, which means
     * that an asynchronous form submission has started, but not completed yet.
     */
    protected var submitting: Boolean by mutableStateOf(false)

    private var currentPageIndex by mutableStateOf(0)

    /**
     * A list of pages present in the wizard.
     */
    protected val pages: List<WizardPage> by lazy { createPages() }

    override val enableLaunch: Boolean = true

    /**
     * Creates the list of pages of which the wizard consists.
     *
     * They are displayed in the order they are passed.
     */
    protected abstract fun createPages(): List<WizardPage>

    /**
     * Submits the wizard.
     *
     * This action is executed when the user completes and submits the wizard.
     *
     * Note, the wizard is not closed automatically when [submit] is invoked, so
     * the implementation has to ensure that [close] is invoked as soon as the
     * submission process succeeds.
     *
     * @return `true`, if submission was performed successfully, and the wizard
     *   can be closed now, and `false` if submission didn't succeed (e.g. if
     *   some validation errors were identified), and the wizard still needs to
     *   be kept open.
     */
    protected abstract suspend fun submit()

    /**
     * Closes the wizard.
     *
     * This method closes the wizard unconditionally, without consulting the
     * [onBeforeCancel] callback, and it is the method that the wizard's
     * implementation is expected to invoke when its submission succeeds.
     *
     * Use [cancel] instead to close the wizard on the user's request.
     */
    public open fun close() {
        onCloseRequest?.invoke()
    }

    /**
     * Cancels the wizard, which is equivalent to pressing the
     * "Cancel" button.
     *
     * This means invoking the [onBeforeCancel] callback, and closing the wizard
     * if the callback didn't prevent closing.
     *
     * @see onBeforeCancel
     */
    public fun cancel(): Unit = launch {
        requestCancel()
    }

    /**
     * Performs the wizard's cancellation, which is the part of [cancel] that
     * doesn't require a composition-scoped coroutine scope.
     *
     * @return `true`, if the wizard was closed, and `false`, if the
     *   [onBeforeCancel] callback has prevented the wizard from being closed.
     */
    internal suspend fun requestCancel(): Boolean {
        if (!onBeforeCancel()) {
            return false
        }
        close()
        return true
    }

    @Composable
    override fun content() {
        Box(
            modifier = Modifier
                .width(width)
                .heightIn(minHeight, maxHeight),
            contentAlignment = Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                verticalArrangement = spacedBy(16.dp)
            ) {
                if (title != null) {
                    Title(title!!)
                }
                Column(
                    Modifier
                        .weight(1F, fill = false)
                        .on(Ctrl(Enter.key).up) {
                            submitPage(currentPage)
                        }
                        .on(Alt(DirectionLeft.key).up) {
                            handlePreviousClick()
                        }
                        .on(Alt(DirectionRight.key).up) {
                            if (!isOnLastPage()) {
                                submitPage(currentPage)
                            }
                        }
                ) {
                    key(currentPage) {
                        PageContainer(currentPage)
                    }
                    LaunchedEffect(currentPage) {
                        currentPage.show()
                    }
                }
                NavigationPanel(
                    onNextClick = { handleNextClick(currentPage) },
                    onBackClick = { handlePreviousClick() },
                    onFinishClick = {
                        handleFinishClick(currentPage)
                    },
                    onCancelClick = { cancel() },
                    isOnFirstPage = isOnFirstPage(),
                    isOnLastPage = isOnLastPage(),
                    submitting
                )
            }
        }
    }

    private fun Wizard.submitPage(currentPage: WizardPage) {
        if (isOnLastPage()) {
            launch {
                handleFinishClick(currentPage)
            }
        } else {
            handleNextClick(currentPage)
        }
    }

    private fun Wizard.handleFinishClick(currentPage: WizardPage) = launch {
        if (currentPage.validate()) {
            submit()
        }
    }

    private fun handleNextClick(currentPage: WizardPage) {
        if (currentPage.validate()) {
            if (!isOnLastPage()) {
                currentPageIndex += 1
            }
        }
    }

    /**
     * Navigates the wizard to the previous page.
     */
    private fun handlePreviousClick() {
        if (!isOnFirstPage()) {
            currentPageIndex -= 1
        }
    }

    /**
     * Returns `true` if the currently displayed page is the first one,
     * `false` otherwise.
     */
    private fun isOnFirstPage(): Boolean {
        return currentPageIndex == 0
    }

    /**
     * Returns `true` if the currently displayed page is the last one,
     * `false` otherwise.
     */
    private fun isOnLastPage(): Boolean {
        return currentPageIndex == pages.size - 1
    }
}

/**
 * The title of the wizard.
 *
 * @param text The text to be title.
 */
@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge
    )
}

/**
 * The panel with control buttons of the wizard.
 *
 * @param onNextClick A callback triggered when the user clicks on
 *   the "Next" button.
 * @param onBackClick A callback triggered when the user clicks on
 *   the "Back" button.
 * @param onFinishClick A callback triggered when the user clicks on
 *   the "Finish" button. This callback is triggered in a separate coroutine.
 * @param onCancelClick A callback triggered when the user clicks on
 *   the "Cancel" button.
 * @param isOnFirstPage Specifies whether the wizard's currently displayed page
 *   is the first one.
 * @param isOnLastPage Specifies whether the wizard's currently displayed page
 *   is the last one.
 * @param submitting Specifies whether wizard's submission is currently
 *   in progress.
 */
@Composable
private fun NavigationPanel(
    onNextClick: () -> Unit,
    onBackClick: () -> Unit,
    onFinishClick: () -> Unit,
    onCancelClick: () -> Unit,
    isOnFirstPage: Boolean,
    isOnLastPage: Boolean,
    submitting: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = SpaceBetween
    ) {
        TextButton(onClick = onCancelClick) {
            Text("Cancel")
        }
        Row(
            horizontalArrangement = spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onBackClick,
                enabled = !isOnFirstPage && !submitting
            ) {
                Text("Back")
            }
            if (isOnLastPage) {
                Button(onClick = onFinishClick, enabled = !submitting) {
                    Text("Finish")
                }
            } else {
                TextButton(onClick = onNextClick) {
                    Text("Next")
                }
            }
        }
    }
}

/**
 * A component that displays a given page inside, and decorates it with any
 * respective UI as per the wizard's requirements (e.g. adding page scrolling
 * support, etc.).
 *
 * @param page A page that has to be displayed in the container.
 */
@Composable
private fun PageContainer(page: WizardPage) {
    val stateVertical = rememberScrollState(0, page)
    val stateHorizontal = rememberScrollState(0, page)
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(stateVertical)
                .horizontalScroll(stateHorizontal),
            horizontalAlignment = Start
        ) {
            page.content()
        }
        VerticalScrollbar(stateVertical) {
            Modifier.align(CenterEnd)
        }
        HorizontalScrollbar(stateHorizontal) {
            Modifier.align(BottomCenter)
        }
    }
}

@Composable
private fun rememberScrollState(initialScrollPos: Int = 0, key: Any?): ScrollState {
    return if (key == null) {
        rememberScrollState(initialScrollPos)
    } else {
        remember(key) {
            ScrollState(initial = initialScrollPos)
        }
    }
}
