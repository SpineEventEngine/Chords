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

package io.spine.chords.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.spine.base.EntityState

/**
 * Creates a navigator for [query] and [client], starts it after composition commits, and closes
 * it when the composition leaves or either input changes. Equivalent queries retain the page.
 * Re-entering the composition creates a new navigator even when the query is retained elsewhere.
 *
 * Given an entity query and the application's client:
 * ```kotlin
 * @Composable
 * fun <T : EntityState> PageItems(query: PagedDataQuery<T>, client: Client) {
 *     val navigator = rememberPagedDataNavigator(query, client)
 *     Text("${navigator.items.size} items")
 * }
 * ```
 * The example uses `androidx.compose.material.Text`.
 *
 * @param T The entity type displayed by the navigator.
 * @param query The ordered selection to navigate; equivalent queries retain the displayed page.
 * @param client The server connection; replacing it creates a new navigator.
 */
@Composable
public fun <T : EntityState> rememberPagedDataNavigator(
    query: PagedDataQuery<T>,
    client: Client
): PagedDataNavigator<T> {
    val scope = rememberCoroutineScope()
    val retained = remember(query, client) {
        object : RememberObserver {
            /**
             * Defers requests until this composition is committed.
             */
            val navigator = createPagedDataNavigator(query, client, scope)

            /**
             * Starts the first page after the composition is committed.
             */
            override fun onRemembered() = navigator.first()

            /**
             * Releases requests when these inputs leave the composition.
             */
            override fun onForgotten() = navigator.close()

            /**
             * Releases the child lifetime if composition is abandoned before committing.
             */
            override fun onAbandoned() = navigator.close()
        }
    }
    return retained.navigator
}
