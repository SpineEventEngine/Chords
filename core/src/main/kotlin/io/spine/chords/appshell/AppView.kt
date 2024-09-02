/*
 * Copyright 2024, TeamDev. All rights reserved.
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

package io.spine.chords.appshell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.vector.ImageVector
import io.spine.chords.Component

/**
 * A base class for each of application's view implementation.
 *
 * It is expected that each application's view has only one respective instance
 * having a lifecycle that spans the application's lifecycle, so in many cases
 * it can be convenient to implement actual [AppView] subclasses as singletons
 * (being Kotlin `object` declarations), for example like this:
 *
 * ```kotlin
 *   public object DashboardView : AppView(
 *       "Dashboard",
 *       Icons.Default.Home
 *   ) {
 *       @Composable
 *       override fun content() {
 *           Text("Dashboard screen content")
 *       }
 *   }
 * ```
 *
 * This `object`-based approach is just a recommendation that is considered
 * convenient for the majority of scenarios though, but it's not a requirement.
 * It is equally possible to declare [AppView] subclasses as regular classes
 * if needed.
 *
 * The actual view content for the concrete view implementation has to be
 * rendered by implementing the abstract [content] method.
 *
 * @param name
 *         a view's name, which is in particular displayed in the application's
 *         view selection menu.
 * @param icon
 *         an icon that symbolically denotes a view, which is in particular
 *         displayed near the respective view selection menu item.
 */
public abstract class AppView
protected constructor(
    public val name: String,
    public val icon: ImageVector = Icons.Default.Menu
) : Component()
