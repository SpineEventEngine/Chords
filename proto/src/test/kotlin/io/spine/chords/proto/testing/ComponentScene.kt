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

package io.spine.chords.proto.testing

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.SemanticsActions.SetText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import java.awt.EventQueue.invokeAndWait
import org.jetbrains.skia.Surface

/**
 * Runs a component scenario without a visible window.
 */
internal fun inScene(content: @Composable () -> Unit, test: (ComponentScene) -> Unit) {
    ComponentScene(content)
        .use(test)
}

/**
 * Lets desktop snapshot notifications run between test actions and frames.
 */
internal fun <T> onUiThread(action: () -> T): T {
    var result: Result<T>? = null
    invokeAndWait { result = runCatching(action) }
    return checkNotNull(result)
        .getOrThrow()
}

/**
 * Applies state changes to an off-screen composition of a component.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal class ComponentScene(content: @Composable () -> Unit) : AutoCloseable {

    /**
     * Provides the desktop composition locals used by the components.
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
        try {
            onUiThread {
                scene.setContent {
                    MaterialTheme { content() }
                }
            }
            render()
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    /**
     * Advances frames until recomposition and its effects have settled.
     */
    fun render() {
        repeat(30) {
            onUiThread {
                Snapshot.sendApplyNotifications()
                scene.render(surface.canvas, ++frame * 16_000_000L)
            }
            if (!scene.hasInvalidations()) {
                return
            }
        }
        error("The component composition did not settle.")
    }

    /**
     * Returns visible text so tests can observe feedback without depending on its wording.
     */
    fun displayedText(): Set<String> = onUiThread {
        val pending = ArrayDeque(
            scene.roots
                .map { it.semanticsOwner.rootSemanticsNode }
        )
        val text = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            node.config
                .getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .map { it.text }
                .filter { it.isNotBlank() }
                .forEach { text.add(it) }
            pending.addAll(node.children)
        }
        text
    }

    /**
     * Returns the single text field's displayed value after formatting and validation.
     */
    fun inputText(): String = onUiThread {
        scene.roots
            .flatMap { it.semanticsOwner.getAllSemanticsNodes(false) }
            .mapNotNull { it.config.getOrNull(SemanticsProperties.EditableText) }
            .single()
            .text
    }

    /**
     * Replaces the single text field's contents through its accessibility input action.
     */
    fun enterText(text: String) {
        onUiThread {
            val edit = scene.roots
                .flatMap { it.semanticsOwner.getAllSemanticsNodes(false) }
                .mapNotNull { it.config.getOrNull(SetText)?.action }
                .single()
            check(edit(AnnotatedString(text))) { "The text field rejected the edit." }
        }
        render()
    }

    /**
     * Releases the composition and its drawing surface.
     */
    override fun close(): Unit = onUiThread {
        scene.close()
        surface.close()
    }
}
