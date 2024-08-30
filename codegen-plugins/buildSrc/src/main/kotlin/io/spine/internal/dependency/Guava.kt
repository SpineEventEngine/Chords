/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * The dependencies for Guava.
 *
 * When changing the version, also change the version used in the `build.gradle.kts`. We need
 * to synchronize the version used in `buildSrc` and in Spine modules. Otherwise, when testing
 * Gradle plugins, errors may occur due to version clashes.
 *
 * @see <a href="https://github.com/google/guava">Guava at GitHub</a>.
 */
@Suppress("unused", "ConstPropertyName")
object Guava {
    private const val version = "32.1.2-jre"
    const val lib     = "com.google.guava:guava:${version}"
    const val testLib = "com.google.guava:guava-testlib:${version}"
}
