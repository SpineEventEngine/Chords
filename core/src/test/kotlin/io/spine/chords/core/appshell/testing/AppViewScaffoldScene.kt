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

package io.spine.chords.core.appshell.testing

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties.Focused
import androidx.compose.ui.semantics.SemanticsProperties.TestTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.spine.chords.core.appshell.AppViewScaffold
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsTheme

/**
 * Renders an [AppViewScaffold] off-screen and measures how it shares the work area.
 *
 * The scaffold has a 16 dp page inset, so its work area is 32 dp narrower than the scene.
 * The scene renders one pixel per dp, so the measured widths are the dp values that the
 * scaffold laid out.
 *
 * @param width The width of the scene.
 * @param supportingPaneWidth The preferred pane width, or `null` to use the theme value.
 * @param supportingPanePadding The pane inset, or `null` to use the theme value. The default
 *   leaves the pane unpadded, so [paneWidth] measures the whole pane.
 * @param intrinsicWidth Whether a parent sizes the scaffold to its maximum intrinsic width.
 * @param focusPaneOnStart Whether an effect beside the scaffold focuses the pane as soon as
 *   the scaffold's first composition is applied.
 */
internal class AppViewScaffoldScene(
    width: Dp,
    supportingPaneWidth: Dp? = null,
    supportingPanePadding: PaddingValues? = PaddingValues(),
    intrinsicWidth: Boolean = false,
    focusPaneOnStart: Boolean = false
) : AutoCloseable {

    /**
     * Renders the scaffold with tagged content and pane areas.
     */
    private val scene = TestScene(width = width) {
        ChordsTheme {
            val paneFocus = remember { FocusRequester() }
            Box(
                modifier = if (intrinsicWidth) Modifier.width(IntrinsicSize.Max) else Modifier
            ) {
                AppViewScaffold(
                    title = "Items",
                    pagePadding = PaddingValues(16.dp),
                    supportingPaneWidth = supportingPaneWidth,
                    supportingPanePadding = supportingPanePadding,
                    supportingPane = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(paneFocus)
                                .focusable()
                                .testTag(PaneTag)
                        )
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(ContentTag)
                    )
                }
            }
            if (focusPaneOnStart) {
                DisposableEffect(Unit) {
                    paneFocus.requestFocus()
                    onDispose {}
                }
            }
        }
    }

    /**
     * The rendered width of the primary content, in dp.
     */
    val contentWidth: Float
        get() = node(ContentTag).boundsInRoot.width

    /**
     * The rendered width of the supporting pane's content, inside its padding, in dp.
     */
    val paneWidth: Float
        get() = node(PaneTag).boundsInRoot.width

    /**
     * Whether keyboard input is directed to the supporting pane.
     */
    val paneFocused: Boolean
        get() = node(PaneTag).config
            .getOrNull(Focused) == true

    /**
     * Releases composition resources after each scenario.
     */
    override fun close() {
        scene.close()
    }

    /**
     * Finds the tagged area among the unmerged semantics nodes.
     */
    private fun node(tag: String): SemanticsNode = scene
        .semanticsNodes(mergingEnabled = false)
        .single {
            it.config
                .getOrNull(TestTag) == tag
        }
}

/**
 * Tags the area filled by the primary content.
 */
private const val ContentTag = "content"

/**
 * Tags the area filled by the supporting pane.
 */
private const val PaneTag = "pane"
