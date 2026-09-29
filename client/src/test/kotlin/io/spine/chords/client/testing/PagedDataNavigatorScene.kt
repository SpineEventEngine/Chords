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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.spine.chords.client.Client
import io.spine.chords.client.PagedDataNavigator
import io.spine.chords.client.given.ObservedItem
import io.spine.chords.client.given.ObservedItemPages.query
import io.spine.chords.client.rememberPagedDataNavigator

/**
 * An off-screen consumer for testing navigation state across composition and input changes.
 */
internal class PagedDataNavigatorScene(initialClient: Client) : AutoCloseable {

    /**
     * Simulates a consumer replacing its server connection.
     */
    var client: Client by mutableStateOf(initialClient)

    /**
     * The query's lower bound, varied to test replacement at the same composition location.
     */
    var lowerBound: String by mutableStateOf("")

    /**
     * Whether the view containing the navigator is present in the composition.
     */
    var visible by mutableStateOf(true)

    /**
     * Triggers a parent recomposition without changing its data selection.
     */
    private var revision by mutableStateOf(0)

    /**
     * Captures the navigator currently retained by the composition.
     */
    private var displayed: PagedDataNavigator<ObservedItem>? = null

    /**
     * Runs the production Compose effects without an application shell or native window.
     */
    private val scene = CompositionScene {
        revision
        if (visible) displayed = rememberPagedDataNavigator(query(lowerBound), client)
    }

    /**
     * Applies pending recomposition before reading the consumer's current navigator.
     */
    val navigator: PagedDataNavigator<ObservedItem>
        get() {
            scene.render()
            return checkNotNull(displayed)
        }

    /**
     * Applies a visibility change even when no navigator is displayed.
     */
    fun render() = scene.render()

    /**
     * Recreates an equivalent query as an ordinary parent recomposition would.
     */
    fun recompose() {
        revision++
    }

    /**
     * Invokes a consumer action on the same event thread as the composition.
     */
    fun act(action: PagedDataNavigator<ObservedItem>.() -> Unit) {
        val current = navigator
        onUiThread { current.action() }
    }

    /**
     * Disposes the composition so its retained navigator releases live work.
     */
    override fun close(): Unit = scene.close()
}
