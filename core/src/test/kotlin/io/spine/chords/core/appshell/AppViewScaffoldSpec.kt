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

import androidx.compose.ui.unit.dp
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.spine.chords.core.appshell.given.AppViewScaffoldSpecEnv.contentWidth
import io.spine.chords.core.appshell.given.AppViewScaffoldSpecEnv.paneWidth
import io.spine.chords.core.appshell.given.AppViewScaffoldSpecEnv.scene
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
        scene(width = 1600.dp, supportingPaneWidth = 480.dp).use { scene ->
            paneWidth(scene) shouldBe 480F
        }
    }

    /**
     * An 800 dp scene leaves a 768 dp work area, so a 480 dp pane would take most of it.
     * Two-fifths of the work area is 307.2 dp, which renders as 307 pixels.
     */
    @Test
    fun `narrow the pane so the content keeps the larger share of a narrow window`() {
        scene(width = 800.dp, supportingPaneWidth = 480.dp).use { scene ->
            paneWidth(scene) shouldBe 307F
            contentWidth(scene) shouldBeGreaterThan paneWidth(scene)
        }
    }
}
