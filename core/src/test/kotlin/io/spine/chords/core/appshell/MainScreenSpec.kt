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

import io.kotest.matchers.shouldBe
import io.spine.chords.core.TestApplication
import io.spine.chords.core.appshell.given.MainScreenSpecEnv.scene
import io.spine.chords.core.appshell.given.MainScreenSpecEnv.text
import io.spine.chords.core.appshell.testing.StatefulNavigationView
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Verifies that opting out of the header gives its entire height to the current view.
 */
@DisplayName("`MainScreen` should")
internal class MainScreenSpec {

    /**
     * Supplies the shared defaults needed to compose the application shell.
     */
    @BeforeEach
    fun installApplication() {
        TestApplication.install()
    }

    /**
     * The default header stays available; a headerless application reserves no empty strip.
     */
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `honor the application's header setting`(showTopBar: Boolean) {
        val view = StatefulNavigationView("Current view")
        scene(view = view, showTopBar = showTopBar)
            .use { scene ->
                text(scene).contains(app.name) shouldBe showTopBar
                view.bounds.top shouldBe if (showTopBar) 64f else 0f
                view.bounds.bottom shouldBe 800f
                view.bounds.height shouldBe if (showTopBar) 736f else 800f
            }
    }
}
