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

package io.spine.chords.core.appshell

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties.ContentDescription
import androidx.compose.ui.semantics.SemanticsProperties.Selected
import androidx.compose.ui.unit.dp
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.spine.chords.core.TestApplication
import io.spine.chords.core.appshell.given.NavigationDrawerSpecEnv.badge
import io.spine.chords.core.appshell.given.NavigationDrawerSpecEnv.destinations
import io.spine.chords.core.appshell.given.NavigationDrawerSpecEnv.footerAction
import io.spine.chords.core.appshell.given.NavigationDrawerSpecEnv.hover
import io.spine.chords.core.appshell.given.NavigationDrawerSpecEnv.iconBounds
import io.spine.chords.core.appshell.given.NavigationDrawerSpecEnv.scene
import io.spine.chords.core.appshell.given.NavigationDrawerSpecEnv.text
import io.spine.chords.core.appshell.given.NavigationDrawerSpecEnv.toggle
import io.spine.chords.core.appshell.given.NavigationDrawerSpecEnv.views
import io.spine.chords.core.appshell.given.SidebarAccountMenu
import io.spine.chords.core.styling.chordsLightColorScheme
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_ESCAPE
import java.awt.event.KeyEvent.VK_TAB
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Exercises sidebar layout, view retention, accessible destinations, and keyboard activation.
 */
@DisplayName("`NavigationDrawer` should")
internal class NavigationDrawerSpec {

    /**
     * Footer controls keep their bottom anchor and perform actions independently of navigation.
     */
    @Nested
    @DisplayName("allow footer actions to")
    inner class Footer {

        /**
         * Waiting for the bottom action's tooltip must not consume the click that opens its menu.
         */
        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `open the account menu after its tooltip appears`(expanded: Boolean) {
            val menu = SidebarAccountMenu()
            scene(views = views(), footer = { menu.Content(it) })
                .use { scene ->
                    if (!expanded) {
                        scene.click(toggle(scene).boundsInRoot.center)
                        scene.render()
                    }

                    repeat(2) { attempt ->
                        withClue("Hovered account click $attempt") {
                            val action = footerAction(scene)
                            val occurrences = if (expanded) 2 else 1
                            hover(
                                scene = scene,
                                node = action,
                                tooltip = "Account",
                                occurrences = occurrences
                            )
                            text(scene)
                                .count { it == "Account" } shouldBe occurrences

                            scene.click(action.boundsInRoot.center)
                            repeat(12) { scene.render() }

                            menu.opened shouldBe true
                            text(scene) shouldContain menu.menuLabel
                            scene.pressKey(VK_ESCAPE)
                            scene.releaseKey(VK_ESCAPE)
                            repeat(12) { scene.render() }
                            menu.opened shouldBe false
                            scene.movePointerTo(Offset(400f, 400f))
                            scene.render()
                        }
                    }
                }
        }

        /**
         * Stacked actions must retain their placement and dispatch independently of navigation.
         */
        @Test
        fun `stack actions above the account menu in both sidebar states`() {
            val views = views()
            val menu = SidebarAccountMenu()
            var invoked = 0
            scene(views = views, footer = { expanded ->
                Column {
                    NavigationDrawerAction(
                        label = "Tools",
                        expanded = expanded,
                        onClick = { invoked++ },
                        icon = { Icon(Icons.Default.Build, contentDescription = null) }
                    )
                    menu.Content(expanded)
                }
            })
                .use { scene ->
                    val originalTools = footerAction(scene, "Tools").boundsInRoot
                    val originalAccount = footerAction(scene).boundsInRoot
                    originalTools.bottom shouldBeLessThan originalAccount.top
                    originalAccount.bottom shouldBe 788f

                    repeat(4) { transition ->
                        withClue("Stacked footer transition $transition") {
                            val tools = footerAction(scene, "Tools")
                            tools.boundsInRoot.topLeft shouldBe originalTools.topLeft
                            tools.boundsInRoot.height shouldBe originalTools.height
                            footerAction(scene).boundsInRoot.topLeft shouldBe
                                    originalAccount.topLeft
                            tools.config.contains(Selected) shouldBe false

                            scene.click(tools.boundsInRoot.center)
                            scene.render()

                            invoked shouldBe transition + 1
                            menu.opened shouldBe false
                            destinations(scene)
                                .map { it.config[Selected] } shouldBe views.indices.map { it == 0 }
                            scene.click(toggle(scene).boundsInRoot.center)
                            scene.render()
                        }
                    }
                }
        }

        /**
         * A menu must preserve view edits, selection, and avatar placement in either width.
         */
        @ParameterizedTest
        @ValueSource(ints = [600, 800])
        fun `stay at the bottom and open without selecting a view`(height: Int) {
            val views = views()
            val menu = SidebarAccountMenu(showTooltip = false)
            scene(views = views, height = height.dp, footer = { menu.Content(it) })
                .use { scene ->
                    scene.click(views[0].editButtonBounds.center)
                    repeat(3) { scene.render() }
                    views[0].edits shouldBe 1
                    val originalIcon = menu.iconBounds
                    val originalAction = footerAction(scene).boundsInRoot
                    originalIcon.size shouldBe Size(36f, 36f)
                    originalIcon.center.x shouldBe toggle(scene).boundsInRoot.center.x
                    originalAction.bottom shouldBe (height - 12).toFloat()

                    repeat(4) { transition ->
                        withClue("Footer transition $transition") {
                            val action = footerAction(scene)
                            action.config.contains(Selected) shouldBe false
                            action.boundsInRoot.topLeft shouldBe originalAction.topLeft
                            action.boundsInRoot.height shouldBe originalAction.height
                            menu.iconBounds shouldBe originalIcon
                            val selection = destinations(scene)
                                .map { it.config[Selected] }
                            val viewBounds = views[0].bounds

                            scene.click(action.boundsInRoot.center)
                            repeat(12) { scene.render() }

                            menu.opened shouldBe true
                            text(scene) shouldContain menu.menuLabel
                            views[0].bounds shouldBe viewBounds
                            views[0].edits shouldBe 1
                            destinations(scene)
                                .map { it.config[Selected] } shouldBe selection

                            scene.pressKey(VK_ESCAPE)
                            scene.releaseKey(VK_ESCAPE)
                            repeat(12) { scene.render() }
                            menu.opened shouldBe false
                            scene.click(toggle(scene).boundsInRoot.center)
                            scene.render()
                        }
                    }
                }
        }

        /**
         * Tab traversal reaches the footer after the destinations, and Enter opens its menu.
         */
        @Test
        fun `open with the keyboard without selecting a view`() {
            val views = views()
            val menu = SidebarAccountMenu()
            scene(views = views, footer = { menu.Content(it) })
                .use { scene ->
                    repeat(views.size + 2) {
                        scene.pressKey(VK_TAB)
                        scene.releaseKey(VK_TAB)
                        scene.render()
                    }
                    scene.pressKey(VK_ENTER)
                    scene.releaseKey(VK_ENTER)
                    repeat(12) { scene.render() }

                    menu.opened shouldBe true
                    text(scene) shouldContain menu.menuLabel
                    destinations(scene)
                        .map { it.config[Selected] } shouldBe views.indices.map { it == 0 }
                }
        }
    }

    /**
     * Supplies the shared defaults required to compose application views.
     */
    @BeforeEach
    fun installApplication() {
        TestApplication.install()
    }

    /**
     * Two full cycles must resize the current content without discarding its local edits.
     */
    @Test
    fun `give collapsed space to the current view and restore its expanded layout`() {
        val views = views()
        val view = views[0]
        scene(views)
            .use { scene ->
                val expandedBounds = view.bounds
                val togglePosition = toggle(scene).boundsInRoot.center
                scene.movePointerTo(view.editButtonBounds.center)
                scene.render()
                scene.click(view.editButtonBounds.center)
                repeat(3) { scene.render() }
                view.edits shouldBe 1

                repeat(2) { cycle ->
                    withClue("Toggle cycle $cycle") {
                        scene.click(toggle(scene).boundsInRoot.center)
                        scene.render()

                        view.bounds.left shouldBeLessThan expandedBounds.left
                        view.bounds.width shouldBeGreaterThan expandedBounds.width
                        view.bounds.right shouldBe expandedBounds.right
                        view.edits shouldBe 1
                        toggle(scene).boundsInRoot.center shouldBe togglePosition
                        toggle(scene).config[ContentDescription] shouldBe listOf("Expand sidebar")
                        text(scene) shouldNotContain view.name

                        scene.click(toggle(scene).boundsInRoot.center)
                        scene.render()

                        view.bounds shouldBe expandedBounds
                        view.edits shouldBe 1
                        toggle(scene).boundsInRoot.center shouldBe togglePosition
                        toggle(scene).config[ContentDescription] shouldBe listOf("Collapse sidebar")
                        text(scene) shouldContain view.name
                    }
                }
            }
    }

    /**
     * Hiding labels must keep full-size icons centered within their destination and the sidebar.
     */
    @Test
    fun `keep normal icons centered when collapsed`() {
        val views = views()
        scene(views)
            .use { scene ->
                scene.click(toggle(scene).boundsInRoot.center)
                scene.render()

                views.forEachIndexed { index, view ->
                    withClue(view.name) {
                        val icon = iconBounds(scene, view)
                        icon.size shouldBe Size(
                            view.icon.defaultWidth.value,
                            view.icon.defaultHeight.value
                        )
                        icon.center shouldBe destinations(scene)[index].boundsInRoot.center
                        icon.center.x shouldBe toggle(scene).boundsInRoot.center.x
                    }
                }
            }
    }

    /**
     * Toggling labels must leave every rendered icon in exactly the same position.
     */
    @Test
    fun `keep destination icons stationary when toggling`() {
        val views = views()
        scene(views)
            .use { scene ->
                scene.click(toggle(scene).boundsInRoot.center)
                scene.render()
                val iconAreas = views.map { iconBounds(scene, it) }
                val iconPixels = iconAreas.map { scene.pixelsIn(it) }
                val buttonAreas = destinations(scene)
                    .map { it.boundsInRoot }

                repeat(4) { transition ->
                    scene.click(toggle(scene).boundsInRoot.center)
                    scene.render()

                    views.forEachIndexed { index, view ->
                        withClue("${view.name}, transition $transition") {
                            scene.pixelsIn(iconAreas[index]) shouldBe iconPixels[index]
                            val button = destinations(scene)[index].boundsInRoot
                            button.topLeft shouldBe buttonAreas[index].topLeft
                            button.height shouldBe buttonAreas[index].height
                        }
                    }
                }
            }
    }

    /**
     * Live badges on inactive views must fit without moving the icon or changing button layout.
     */
    @Test
    fun `update badges in both sidebar states without moving destinations`() {
        val views = views()
        val view = views[1]
        scene(views)
            .use { scene ->
                scene.click(toggle(scene).boundsInRoot.center)
                scene.render()
                val originalIcon = iconBounds(scene, view)
                val originalButton = destinations(scene)[1].boundsInRoot

                repeat(2) { state ->
                    val button = destinations(scene)[1].boundsInRoot
                    listOf(1, 12, 12345, 0).forEach { count ->
                        withClue("Sidebar state $state, count $count") {
                            view.badgeCount = count
                            scene.render()

                            destinations(scene)[1].boundsInRoot shouldBe button
                            button.topLeft shouldBe originalButton.topLeft
                            button.height shouldBe originalButton.height
                            if (state == 0) {
                                iconBounds(scene, view) shouldBe originalIcon
                            }
                            val badge = badge(scene, view)
                            if (count == 0) {
                                badge shouldBe null
                            } else {
                                text(scene) shouldContain count.toString()
                                val bounds = requireNotNull(badge).boundsInRoot
                                bounds.left shouldBeGreaterThan button.left
                                bounds.right shouldBeLessThan button.right
                                bounds.top shouldBeGreaterThan button.top
                                bounds.bottom shouldBeLessThan button.bottom
                            }
                        }
                    }
                    scene.click(toggle(scene).boundsInRoot.center)
                    scene.render()
                }
            }
    }

    /**
     * Every icon remains selectable, and navigation must keep the chosen sidebar width.
     */
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3])
    fun `keep destinations usable and visibly selected in either state`(index: Int) {
        val views = views()
        scene(views)
            .use { scene ->
                val expandedLeft = views[0].bounds.left
                scene.click(toggle(scene).boundsInRoot.center)
                scene.render()
                val collapsedLeft = views[0].bounds.left
                val destination = destinations(scene)[index]
                destination.config[ContentDescription] shouldBe listOf(views[index].name)

                scene.click(destination.boundsInRoot.center)
                scene.movePointerTo(Offset(799f, 799f))
                repeat(30) { scene.render() }

                views[index].bounds.left shouldBe collapsedLeft
                destinations(scene)
                    .map { it.config[Selected] } shouldBe views.indices.map { it == index }
                val selectedBounds = destinations(scene)[index].boundsInRoot
                scene.pixelAt((selectedBounds.left + 4).dp, selectedBounds.center.y.dp) shouldBe
                        chordsLightColorScheme().primaryContainer.toArgb()

                scene.click(toggle(scene).boundsInRoot.center)
                scene.render()
                val nextIndex = (index + 1) % views.size
                scene.click(destinations(scene)[nextIndex].boundsInRoot.center)
                scene.render()

                views[nextIndex].bounds.left shouldBe expandedLeft
                destinations(scene)
                    .map { it.config[Selected] } shouldBe views.indices.map { it == nextIndex }
            }
    }

    /**
     * The focused toggle must remain keyboard-operable after its label and icon change.
     */
    @Test
    fun `collapse and expand with the keyboard`() {
        val views = views()
        scene(views)
            .use { scene ->
                val expandedBounds = views[0].bounds
                scene.pressKey(VK_TAB)
                scene.releaseKey(VK_TAB)
                scene.render()

                scene.pressKey(VK_ENTER)
                scene.releaseKey(VK_ENTER)
                scene.render()

                views[0].bounds.width shouldBeGreaterThan expandedBounds.width

                scene.pressKey(VK_ENTER)
                scene.releaseKey(VK_ENTER)
                scene.render()

                views[0].bounds shouldBe expandedBounds
            }
    }

    /**
     * Tab traversal must reach all destinations even when their labels are hidden.
     */
    @Test
    fun `navigate between collapsed destinations with the keyboard`() {
        val views = views()
        scene(views)
            .use { scene ->
                scene.pressKey(VK_TAB)
                scene.releaseKey(VK_TAB)
                scene.render()
                scene.pressKey(VK_ENTER)
                scene.releaseKey(VK_ENTER)
                scene.render()
                val collapsedLeft = views[0].bounds.left

                views.indices.forEach { index ->
                    withClue("Destination $index") {
                        scene.pressKey(VK_TAB)
                        scene.releaseKey(VK_TAB)
                        scene.render()
                        scene.pressKey(VK_ENTER)
                        scene.releaseKey(VK_ENTER)
                        scene.render()

                        destinations(scene)
                            .map { it.config[Selected] } shouldBe views.indices.map { it == index }
                        views[index].bounds.left shouldBe collapsedLeft
                    }
                }
            }
    }

    /**
     * A collapsed destination and both toggle states expose their names on hover.
     */
    @Test
    fun `identify icon actions with tooltips`() {
        val views = views()
        scene(views)
            .use { scene ->
                val collapse = toggle(scene)
                hover(scene = scene, node = collapse, tooltip = "Collapse")
                text(scene) shouldContain "Collapse"
                scene.click(collapse.boundsInRoot.center)
                scene.render()
                scene.movePointerTo(Offset(799f, 799f))
                scene.render()
                val expand = toggle(scene)
                hover(scene = scene, node = expand, tooltip = "Expand")
                text(scene) shouldContain "Expand"

                val destination = destinations(scene)[1]
                hover(scene = scene, node = destination, tooltip = views[1].name)

                text(scene) shouldContain views[1].name
            }
    }
}
