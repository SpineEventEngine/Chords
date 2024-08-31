/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
