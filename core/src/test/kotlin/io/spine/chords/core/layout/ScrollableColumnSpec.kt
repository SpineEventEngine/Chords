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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.spine.chords.core.styling.ChordsTheme
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Verifies that overflow keeps lower actions reachable while the surrounding header stays fixed.
 */
@DisplayName("`ScrollableColumn` should")
internal class ScrollableColumnSpec {

    /**
     * New scroll states must not change the content width after their first measurement.
     * Alternating short and long selections also keeps the surrounding controls in place.
     */
    @ParameterizedTest(name = "starts with overflow: {0}")
    @ValueSource(booleans = [false, true])
    fun `keep content geometry stable when selecting another item`(startsWithOverflow: Boolean) {
        val selection = mutableStateOf(0)
        var bodyBounds = Rect.Zero
        var headerBounds = Rect.Zero
        var footerBounds = Rect.Zero
        TestScene {
            ChordsTheme {
                Column(Modifier.size(240.dp, 180.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .onGloballyPositioned { headerBounds = it.boundsInRoot() }
                    )
                    key(selection.value) {
                        val longContent = (selection.value % 2 == 0) == startsWithOverflow
                        ScrollableColumn(Modifier.weight(1F)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(if (longContent) 300.dp else 48.dp)
                                    .onGloballyPositioned { bodyBounds = it.boundsInRoot() }
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .onGloballyPositioned { footerBounds = it.boundsInRoot() }
                    )
                }
            }
        }
            .use { scene ->
                val initialHeader = headerBounds
                val initialFooter = footerBounds
                val initialWidth = bodyBounds.width

                repeat(3) { item ->
                    if (item > 0) {
                        selection.value = item
                        scene.render()
                    }
                    val firstFrame = bodyBounds
                    repeat(5) { scene.render() }

                    withClue("Selected item $item; starts with overflow: $startsWithOverflow") {
                        bodyBounds shouldBe firstFrame
                        bodyBounds.width shouldBe initialWidth
                        headerBounds shouldBe initialHeader
                        footerBounds shouldBe initialFooter
                    }
                }
            }
    }

    /**
     * A long body must not consume all remaining space and hide its following action.
     */
    @Test
    fun `scroll to a lower action without moving its header`() {
        val scrollState = ScrollState(0)
        var headerBounds = Rect.Zero
        var clicks = 0
        TestScene {
            ChordsTheme {
                Column(Modifier.size(240.dp, 180.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(40.dp)
                            .onGloballyPositioned { headerBounds = it.boundsInRoot() }
                    )
                    ScrollableColumn(scrollState = scrollState) {
                        Box(Modifier.fillMaxWidth().height(300.dp))
                        Box(
                            Modifier.fillMaxWidth().height(36.dp)
                                .clickable { clicks++ }
                        )
                    }
                }
            }
        }.use { scene ->
            val initialHeader = headerBounds
            scrollState.maxValue shouldBeGreaterThan 0
            runBlocking { scrollState.scrollTo(scrollState.maxValue) }
            scene.render()
            scene.click(Offset(120f, 160f))
            clicks shouldBe 1
            headerBounds shouldBe initialHeader
        }
    }
}
