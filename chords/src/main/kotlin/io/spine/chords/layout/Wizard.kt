/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.layout

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.spine.chords.Component
import io.spine.chords.keyboard.KeyModifiers.Companion.Alt
import io.spine.chords.keyboard.KeyModifiers.Companion.Ctrl
import io.spine.chords.keyboard.key
import io.spine.chords.keyboard.on
import io.spine.chords.layout.WizardContentSize.maxHeight
import io.spine.chords.layout.WizardContentSize.minHeight
import io.spine.chords.layout.WizardContentSize.width
import io.spine.chords.primitive.HorizontalScrollbar
import io.spine.chords.primitive.VerticalScrollbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Constants for the min and max size of the wizard's content pane.
 */
private object WizardContentSize {
    val width = 670.dp
    val minHeight = 400.dp
    val maxHeight = 700.dp
}

/**
 * The base class for creating multi-step form component known as wizard.
 *
 * To create a concrete wizard you need to extend the class
 * and override all abstract methods that configure the data needed for the wizard.
 *
 * Note that an [onCloseRequest] callback is triggered when the user has
 * finished using the wizard, and it needs to be closed. The container where
 * the wizard is placed is responsible for hiding the wizard (excluding it from
 * the composition) upon this event.
 */
public abstract class Wizard : Component() {

    /**
     * The text to be the title of the wizard.
     */
    protected abstract val title: String

    /**
     * A callback that should be handled to close the wizard (exclude it from
     * the composition).
     *
     * This callback is triggered when the user closes the wizard or after
     * successful submission.
     */
    public var onCloseRequest: (() -> Unit)? = null

    public var currentPage: WizardPage
        get() = pages[currentPageIndex]
        set(page) {
            val pageIndex = pages.indexOf(page)
            check(pageIndex != -1) { "Such page does not belong to the wizard" }
            currentPageIndex = pageIndex
        }

    private var currentPageIndex by mutableStateOf(0)

    private val pages by lazy { createPages() }

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
     * `onCloseRequest` is triggerred right after the `submit` action,
     * so it is not needed to configure it manually.
     */
    protected abstract suspend fun submit(): Boolean

    @Composable
    override fun content() {
        val coroutineScope = rememberCoroutineScope()
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(width, width)
                    .heightIn(minHeight, maxHeight)
                    .padding(32.dp),
                verticalArrangement = spacedBy(16.dp)
            ) {
                Title(title)
                Column(
                    Modifier
                        .weight(1F)
                        .on(Ctrl(Enter.key).up) {
                            submitPage(currentPage, coroutineScope)
                        }
                        .on(Alt(DirectionLeft.key).up) {
                            goToPreviousPage()
                        }
                        .on(Alt(DirectionRight.key).up) {
                            if (!isOnLastPage()) {
                                submitPage(currentPage, coroutineScope)
                            }
                        }
                ) {
                    PageContainer(currentPage)
                }
                NavigationPanel(
                    onNextClick = { handleNextClick(currentPage) },
                    onBackClick = { goToPreviousPage() },
                    onFinishClick = {
                        coroutineScope.launch {
                            handleFinishClick(currentPage)
                        }
                    },
                    onCancelClick = { onCloseRequest?.invoke() },
                    isOnFirstPage = isOnFirstPage(),
                    isOnLastPage = isOnLastPage()
                )
            }
        }
    }

    private fun Wizard.submitPage(
        currentPage: WizardPage,
        coroutineScope: CoroutineScope
    ) {
        if (isOnLastPage()) {
            coroutineScope.launch {
                handleFinishClick(currentPage)
            }
        } else {
            handleNextClick(currentPage)
        }
    }

    private suspend fun Wizard.handleFinishClick(currentPage: WizardPage) {
        if (currentPage.validate()) {
            if (submit()) {
                onCloseRequest?.invoke()
            }
        }
    }

    private fun handleNextClick(currentPage: WizardPage) {
        if (currentPage.validate()) {
            goToNextPage()
        }
    }

    /**
     * Navigates the wizard to the next page.
     */
    private fun goToNextPage() {
        if (!isOnLastPage()) {
            currentPageIndex += 1
        }
    }

    /**
     * Navigates the wizard to the previous page.
     */
    private fun goToPreviousPage() {
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
 * @param text
 *         the text to be title.
 */
@Composable
private fun Title(
    text: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge
    )
}

/**
 * The panel with control buttons of the wizard.
 *
 * @param onNextClick
 *         a callback triggered when the user clicks on the "Next" button.
 * @param onBackClick
 *         a callback triggered when the user clicks on the "Back" button.
 * @param onFinishClick
 *         a callback triggered when the user clicks on the "Finish" button.
 *         this callback triggers in a separate coroutine.
 * @param onCancelClick
 *         a callback triggered when the user clicks on the "Cancel" button.
 * @param isOnFirstPage
 *         is a wizard's currently displayed page the first one.
 * @param isOnLastPage
 *         is a wizard's currently displayed page the last one.
 */
@Composable
private fun NavigationPanel(
    onNextClick: () -> Unit,
    onBackClick: () -> Unit,
    onFinishClick: () -> Unit,
    onCancelClick: () -> Unit,
    isOnFirstPage: Boolean,
    isOnLastPage: Boolean
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
                enabled = !isOnFirstPage
            ) {
                Text("Back")
            }
            if (isOnLastPage) {
                Button(onClick = onFinishClick) {
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
 * @param page
 *         a page that has to be displayed in the container.
 */
@Composable
private fun PageContainer(
    page: WizardPage
) {
    val stateVertical = rememberScrollState(0, page)
    val stateHorizontal = rememberScrollState(0, page)
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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