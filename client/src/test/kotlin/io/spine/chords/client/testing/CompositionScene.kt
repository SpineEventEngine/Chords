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
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.jetbrains.skia.Surface

/**
 * Limits settling to two seconds of simulated frame time so endless recomposition fails.
 */
private const val IdleFrameLimit = 125

/**
 * An off-screen component view for testing recomposition and lifecycle effects.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
internal class CompositionScene(
    private val scheduler: TestCoroutineScheduler? = null,
    content: @Composable () -> Unit
) : AutoCloseable {

    /**
     * The test composition with the context provided by a desktop view.
     */
    private val scene = onUiThread {
        ComposeScene(coroutineContext = scheduler?.let { StandardTestDispatcher(it) }
            ?: EmptyCoroutineContext)
    }

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
     *
     * Fails if the scene still has pending work after two seconds of simulated frame time.
     */
    fun render() {
        onUiThread {
            repeat(IdleFrameLimit) {
                scheduler?.runCurrent()
                Snapshot.sendApplyNotifications()
                scene.render(surface.canvas, ++frame * 16_000_000L)
                scheduler?.runCurrent()
                if (!scene.hasInvalidations()) {
                    return@onUiThread
                }
            }
            error("The composition still has pending work after $IdleFrameLimit frames.")
        }
    }

    /**
     * Releases the composition and its drawing surface.
     */
    override fun close(): Unit = onUiThread {
        scene.close()
        scheduler?.runCurrent()
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
