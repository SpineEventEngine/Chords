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


package io.spine.chords.core.appshell.given

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties.TestTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.spine.chords.core.appshell.AppViewScaffold
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsTheme

/**
 * Supplies scaffolds whose work area and supporting pane can be measured in an off-screen scene.
 */
internal object AppViewScaffoldSpecEnv {

    /**
     * Tags the area filled by the primary content.
     */
    private const val ContentTag = "content"

    /**
     * Tags the area filled by the supporting pane.
     */
    private const val PaneTag = "pane"

    /**
     * Composes a scaffold with a 16 dp page inset and an unpadded pane of the given preference.
     *
     * The work area is therefore 32 dp narrower than the scene.
     */
    fun scene(width: Dp, supportingPaneWidth: Dp): TestScene = TestScene(width = width) {
        ChordsTheme {
            AppViewScaffold(
                title = "Items",
                pagePadding = PaddingValues(16.dp),
                supportingPaneWidth = supportingPaneWidth,
                supportingPanePadding = PaddingValues(),
                supportingPane = {
                    Box(Modifier
                        .fillMaxSize()
                        .testTag(PaneTag))
                }
            ) {
                Box(Modifier
                    .fillMaxSize()
                    .testTag(ContentTag))
            }
        }
    }

    /**
     * Returns the rendered width of the primary content, in dp.
     */
    fun contentWidth(scene: TestScene): Float = widthOf(scene, ContentTag)

    /**
     * Returns the rendered width of the supporting pane, in dp.
     */
    fun paneWidth(scene: TestScene): Float = widthOf(scene, PaneTag)

    /**
     * Measures the tagged area; the scene renders one pixel per dp.
     */
    private fun widthOf(scene: TestScene, tag: String): Float = scene
        .semanticsNodes(mergingEnabled = false)
        .single { it.config.getOrNull(TestTag) == tag }
        .boundsInRoot
        .width
}
