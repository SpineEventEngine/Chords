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

package io.spine.internal.dependency

/**
 * Aligns Compose modules brought in by desktop, Material, and navigation libraries.
 */
object Compose {
    /**
     * The last published Material icons artifact, used by existing component APIs.
     */
    const val materialIcons = "org.jetbrains.compose.material:material-icons-extended:1.7.3"

    /**
     * Matches the Compose Gradle plugin in `buildSrc/build.gradle.kts`.
     */
    private const val version = "1.12.2"

    /**
     * Provides the desktop composition and window APIs.
     */
    const val ui = "org.jetbrains.compose.ui:ui:$version"

    /**
     * Provides layout, text editing, and interaction primitives.
     */
    const val foundation = "org.jetbrains.compose.foundation:foundation:$version"

    /**
     * Allows the test host to deliver desktop back gestures through Compose's navigation API.
     */
    const val navigationEvent = "androidx.navigationevent:navigationevent:1.2.0"

    /**
     * Supplies access to the composition's navigation dispatcher.
     */
    const val navigationEventCompose = "androidx.navigationevent:navigationevent-compose:1.2.0"

    /**
     * Matches the native rendering library used to build this Compose release.
     */
    private const val skikoVersion = "0.150.2"

    /**
     * Supplies the native renderer for the platform running the desktop application.
     */
    val skikoRuntime: String
        get() {
            val os = System.getProperty("os.name").lowercase().let {
                when {
                    it.startsWith("mac") -> "macos"
                    it.startsWith("win") -> "windows"
                    it.startsWith("linux") -> "linux"
                    else -> error("Unsupported desktop platform: $it")
                }
            }
            val arch = when (val architecture = System.getProperty("os.arch")) {
                "aarch64", "arm64" -> "arm64"
                "amd64", "x86_64" -> "x64"
                else -> error("Unsupported desktop architecture: $architecture")
            }
            return "org.jetbrains.skiko:skiko-awt-runtime-$os-$arch:$skikoVersion"
        }

    /**
     * JVM modules sharing the Compose Multiplatform release.
     * Material 3 and Material icons have independent release schedules.
     */
    val libs = mapOf(
        "animation" to listOf("animation", "animation-core"),
        "foundation" to listOf("foundation", "foundation-layout"),
        "material" to listOf("material", "material-ripple"),
        "runtime" to listOf("runtime", "runtime-saveable"),
        "ui" to listOf(
            "ui", "ui-geometry", "ui-graphics", "ui-text", "ui-unit", "ui-util",
            "ui-tooling-preview", "ui-tooling", "ui-backhandler"
        )
    )
        .flatMap { (group, modules) ->
            modules.flatMap {
                listOf(
                    "org.jetbrains.compose.$group:$it:$version",
                    "org.jetbrains.compose.$group:$it-desktop:$version"
                )
            }
        }

    /**
     * AndroidX support libraries used by Compose's JVM variants.
     */
    val androidX = listOf(
        navigationEvent,
        navigationEventCompose,
        "androidx.navigationevent:navigationevent-desktop:1.2.0",
        "androidx.navigationevent:navigationevent-compose-desktop:1.2.0",
        "org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.0",
        "org.jetbrains.androidx.navigationevent:navigationevent-compose-desktop:1.1.0",
        "androidx.collection:collection:1.7.0",
        "androidx.collection:collection-jvm:1.7.0",
        "org.jetbrains.compose.collection-internal:collection:1.11.1",
        "org.jetbrains.kotlinx:kotlinx-datetime:0.8.0",
        "org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.8.0",
        "org.jetbrains.runtime:jbr-api:1.12.0",
        "org.jetbrains.compose.annotation-internal:annotation:1.11.1",
        "org.jetbrains.compose.annotation-internal:annotation-jvm:1.11.1",
        "androidx.compose.runtime:runtime-saveable:1.12.1",
        "androidx.compose.runtime:runtime-saveable-desktop:1.12.1",
        "androidx.annotation:annotation:1.11.0",
        "androidx.annotation:annotation-jvm:1.11.0",
        "androidx.compose.runtime:runtime:1.12.1",
        "androidx.compose.runtime:runtime-desktop:1.12.1",
        "androidx.compose.runtime:runtime-annotation:1.12.1",
        "androidx.compose.runtime:runtime-annotation-jvm:1.12.1",
        "androidx.savedstate:savedstate:1.5.0",
        "androidx.savedstate:savedstate-jvm:1.5.0",
        "androidx.savedstate:savedstate-compose:1.5.0",
        "androidx.savedstate:savedstate-compose-desktop:1.5.0"
    )

    /**
     * Aligns lifecycle and saved-state bridges used by Compose and Voyager.
     */
    val lifecycle = listOf("androidx.lifecycle", "org.jetbrains.androidx.lifecycle")
        .flatMap { group ->
            listOf(
                "lifecycle-common", "lifecycle-common-jvm", "lifecycle-runtime",
                "lifecycle-runtime-desktop", "lifecycle-runtime-compose",
                "lifecycle-runtime-compose-desktop", "lifecycle-viewmodel",
                "lifecycle-viewmodel-desktop", "lifecycle-viewmodel-compose",
                "lifecycle-viewmodel-compose-desktop", "lifecycle-viewmodel-savedstate",
                "lifecycle-viewmodel-savedstate-desktop"
            )
                .map { "$group:$it:2.11.0" }
        } + listOf(
            "savedstate", "savedstate-jvm", "savedstate-compose", "savedstate-compose-desktop"
        )
            .map { "org.jetbrains.androidx.savedstate:$it:1.4.0" }

    /**
     * Matches Compose 1.12.2's Skiko ABI in both the Java API and native binaries.
     */
    val skiko = listOf(
        "skiko", "skiko-awt", "skiko-awt-runtime-macos-arm64", "skiko-awt-runtime-macos-x64",
        "skiko-awt-runtime-linux-x64", "skiko-awt-runtime-linux-arm64",
        "skiko-awt-runtime-windows-x64", "skiko-awt-runtime-windows-arm64"
    )
        .map { "org.jetbrains.skiko:$it:$skikoVersion" }
}
