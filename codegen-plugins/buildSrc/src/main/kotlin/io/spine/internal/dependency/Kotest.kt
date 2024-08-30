/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * Testing framework for Kotlin.
 *
 * @see <a href="https://kotest.io/">Kotest site</a>
 */
@Suppress("unused", "ConstPropertyName")
object Kotest {
    const val version = "5.6.2"
    const val group = "io.kotest"
    const val assertions = "$group:kotest-assertions-core:$version"
    const val runnerJUnit5 = "$group:kotest-runner-junit5:$version"
    const val runnerJUnit5Jvm = "$group:kotest-runner-junit5-jvm:$version"
    const val frameworkApi = "$group:kotest-framework-api:$version"
    const val datatest = "$group:kotest-framework-datatest:$version"
    const val frameworkEngine = "$group:kotest-framework-engine:$version"

    // https://plugins.gradle.org/plugin/io.kotest.multiplatform
    object MultiplatformGradlePlugin {
        const val version = Kotest.version
        const val id = "io.kotest.multiplatform"
        const val classpath = "$group:kotest-framework-multiplatform-plugin-gradle:$version"
    }

    // https://github.com/kotest/kotest-gradle-plugin
    object JvmGradlePlugin {
        const val version = "0.4.10"
        const val id = "io.kotest"
        const val classpath = "$group:kotest-gradle-plugin:$version"
    }
}
