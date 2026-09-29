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

package io.spine.chords.client.testing

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import java.awt.EventQueue.invokeAndWait
import org.jetbrains.skia.Surface

/**
 * An off-screen component view for testing recomposition and lifecycle effects.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal class CompositionScene(content: @Composable () -> Unit) : AutoCloseable {

    /**
     * The test composition with the context provided by a desktop view.
     */
    private val scene = onUiThread { ComposeScene() }

    /**
     * Supplies a canvas for advancing frames without showing a window.
     */
    private val surface = Surface.makeRasterN32Premul(1, 1)

    /**
     * Keeps frame timestamps increasing as the test applies edits.
     */
    private var frame = 0L

    init {
        onUiThread {
            scene.setContent {
                MaterialTheme { content() }
            }
        }
        render()
    }

    /**
     * Advances frames until recomposition and its effects have settled.
     */
    fun render() {
        repeat(10) {
            onUiThread {
                Snapshot.sendApplyNotifications()
                scene.render(surface.canvas, ++frame * 16_000_000L)
            }
            if (!scene.hasInvalidations()) {
                return
            }
        }
        error("The composition did not settle.")
    }

    /**
     * Releases the composition and its drawing surface.
     */
    override fun close(): Unit = onUiThread {
        scene.close()
        surface.close()
    }
}

/**
 * Runs [action] on the AWT event thread, where the composition runs, and rethrows its failure.
 */
internal fun <T> onUiThread(action: () -> T): T {
    var result: Result<T>? = null
    invokeAndWait { result = runCatching(action) }
    return checkNotNull(result)
        .getOrThrow()
}
