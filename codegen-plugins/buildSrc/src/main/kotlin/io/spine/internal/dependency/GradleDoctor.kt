/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * Helps optimize Gradle Builds by ensuring recommendations at build time.
 *
 * See [plugin site](https://runningcode.github.io/gradle-doctor) for features and usage.
 */
@Suppress("unused", "ConstPropertyName")
object GradleDoctor {
    const val version = "0.8.1"
    const val pluginId = "com.osacky.doctor"
}
