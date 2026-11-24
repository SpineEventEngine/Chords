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

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.spine.chords.core.appshell.testing.AppViewScaffoldScene
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests how [AppViewScaffold] shares the work area between its content and supporting pane.
 */
@DisplayName("`AppViewScaffold` should")
internal class AppViewScaffoldSpec {

    /**
     * A 1600 dp scene leaves a 1568 dp work area, whose two-fifths exceed the preferred width.
     */
    @Test
    fun `keep the preferred pane width in a wide window`() {
        AppViewScaffoldScene(width = 1600.dp, supportingPaneWidth = 480.dp)
            .use { scene ->
                scene.paneWidth shouldBe 480F
            }
    }

    /**
     * An 800 dp scene leaves a 768 dp work area, so a 480 dp pane would take most of it.
     * The pane may take two-fifths of the 767 dp beside the divider, 306.8 dp, which renders
     * as 307 pixels.
     */
    @Test
    fun `narrow the pane so the content keeps the larger share of a narrow window`() {
        AppViewScaffoldScene(width = 800.dp, supportingPaneWidth = 480.dp)
            .use { scene ->
                scene.paneWidth shouldBe 307F
                scene.contentWidth shouldBeGreaterThan scene.paneWidth
            }
    }

    /**
     * With the theme's 420 dp pane and 16 dp inset, an 800 dp scene narrows the pane to the same
     * 307 pixels, which leaves 275 pixels inside the inset.
     */
    @Test
    fun `narrow the theme pane width in a narrow window`() {
        AppViewScaffoldScene(width = 800.dp, supportingPanePadding = null)
            .use { scene ->
                scene.paneWidth shouldBe 275F
            }
    }

    /**
     * An unspecified preferred width sets no limit of its own, so the pane takes its whole
     * two-fifths share rather than the entire row.
     */
    @Test
    fun `keep an unspecified pane width within its share of a narrow window`() {
        AppViewScaffoldScene(width = 800.dp, supportingPaneWidth = Dp.Unspecified)
            .use { scene ->
                scene.paneWidth shouldBe 307F
            }
    }

    /**
     * A subcomposed layout rejects intrinsic measurement. Here the pane reports the 1200 dp
     * beside the divider whose two-fifths are its preferred 480 dp. The content asks for
     * nothing and the divider for 1 dp, so the parent gives the scaffold its 16 dp insets and
     * a 1201 dp work area, and the content gets the remaining 720 dp.
     */
    @Test
    fun `keep the preferred pane width at its maximum intrinsic width`() {
        AppViewScaffoldScene(
            width = 1600.dp,
            supportingPaneWidth = 480.dp,
            intrinsicWidth = IntrinsicSize.Max
        )
            .use { scene ->
                scene.paneWidth shouldBe 480F
                scene.contentWidth shouldBe 720F
            }
    }

    /**
     * The pane is meant to shrink, so its minimum is only its preferred 480 dp. With the 1 dp
     * divider, the parent gives the scaffold a 481 dp work area. The pane takes two-fifths of
     * the 480 dp beside the divider, and the content gets the remaining 288 dp.
     */
    @Test
    fun `narrow the pane at its minimum intrinsic width`() {
        AppViewScaffoldScene(
            width = 1600.dp,
            supportingPaneWidth = 480.dp,
            intrinsicWidth = IntrinsicSize.Min
        )
            .use { scene ->
                scene.paneWidth shouldBe 192F
                scene.contentWidth shouldBe 288F
            }
    }

    /**
     * A caller's effect runs once the scaffold's first composition is applied, before layout.
     * The pane must be composed by then for its focus requester to work.
     */
    @Test
    fun `let the caller focus the pane from its first composition`() {
        AppViewScaffoldScene(width = 800.dp, focusPaneOnStart = true)
            .use { scene ->
                scene.paneFocused shouldBe true
            }
    }
}
