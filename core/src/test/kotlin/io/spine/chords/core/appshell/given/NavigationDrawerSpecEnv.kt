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

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions.OnClick
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties.ContentDescription
import androidx.compose.ui.semantics.SemanticsProperties.Selected
import androidx.compose.ui.semantics.SemanticsProperties.TestTag
import androidx.compose.ui.semantics.SemanticsProperties.Text
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import io.spine.chords.core.appshell.NavigationDrawer
import io.spine.chords.core.appshell.testing.StatefulNavigationView
import io.spine.chords.core.layout.TestScene
import io.spine.chords.core.styling.ChordsTheme
import io.spine.chords.core.styling.chordsLightColorScheme

/**
 * Supplies navigation scenes and locates controls by their accessibility roles and state.
 */
internal object NavigationDrawerSpecEnv {

    /**
     * Creates independent destinations without a server or native application window.
     */
    fun views(): List<StatefulNavigationView> = (1..4)
        .map { StatefulNavigationView("Destination $it") }

    /**
     * Composes the same drawer and view navigator used by the application shell.
     */
    fun scene(
        views: List<StatefulNavigationView>,
        height: Dp = 800.dp,
        footer: @Composable (Boolean) -> Unit = {}
    ): TestScene = TestScene(height = height) {
        ChordsTheme(colorScheme = chordsLightColorScheme()) {
            Navigator(views[0]) {
                NavigationDrawer(appViews = views, topPadding = 0.dp, footer = footer)
            }
        }
    }

    /**
     * Finds destination controls, excluding their merged text and icon children.
     */
    fun destinations(scene: TestScene): List<SemanticsNode> = scene.semanticsNodes()
        .filter { it.config.contains(Selected) }

    /**
     * Measures the icon independently of the collapsed destination's clickable area.
     */
    fun iconBounds(scene: TestScene, view: StatefulNavigationView): Rect = scene
        .semanticsNodes(mergingEnabled = false)
        .single { it.config.getOrNull(ContentDescription) == listOf(view.name) }
        .boundsInRoot

    /**
     * Finds the optional status badge without relying on its numeric label.
     */
    fun badge(scene: TestScene, view: StatefulNavigationView): SemanticsNode? = scene
        .semanticsNodes(mergingEnabled = false)
        .singleOrNull { it.config.getOrNull(TestTag) == "${view.name} badge" }

    /**
     * Finds the named icon action above the destinations.
     */
    fun toggle(scene: TestScene): SemanticsNode = scene.semanticsNodes()
        .single {
            it.config.contains(OnClick) && it.config.getOrNull(ContentDescription) in listOf(
                listOf("Expand sidebar"), listOf("Collapse sidebar")
            )
        }

    /**
     * Finds a named footer action without treating it as a selected destination.
     */
    fun footerAction(scene: TestScene, label: String = "Account"): SemanticsNode = scene
        .semanticsNodes()
        .single {
            it.config.contains(OnClick) &&
                    it.config.getOrNull(ContentDescription) == listOf(label)
        }

    /**
     * Reports text exposed by labels and open tooltip popups.
     */
    fun text(scene: TestScene): List<String> = scene.semanticsNodes()
        .flatMap { it.config.getOrNull(Text).orEmpty() }
        .map { it.text }

    /**
     * Waits for tooltip text, counting an identical button label when it is already visible.
     */
    fun hover(
        scene: TestScene,
        node: SemanticsNode,
        tooltip: String,
        occurrences: Int = 1
    ) {
        scene.movePointerTo(node.boundsInRoot.center)
        repeat(100) {
            Thread.sleep(20)
            scene.render()
            val matches = text(scene)
                .count { it == tooltip }
            if (matches >= occurrences) {
                return
            }
        }
    }
}
