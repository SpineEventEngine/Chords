/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://errorprone.info/
@Suppress("unused", "ConstPropertyName")
object ErrorProne {
    // https://github.com/google/error-prone
    private const val version = "2.21.1"
    // https://github.com/tbroyer/gradle-errorprone-plugin/blob/v0.8/build.gradle.kts
    private const val javacPluginVersion = "9+181-r4173-1"

    val annotations = listOf(
        "com.google.errorprone:error_prone_annotations:${version}",
        "com.google.errorprone:error_prone_type_annotations:${version}"
    )
    const val core = "com.google.errorprone:error_prone_core:${version}"
    const val checkApi = "com.google.errorprone:error_prone_check_api:${version}"
    const val testHelpers = "com.google.errorprone:error_prone_test_helpers:${version}"
    const val javacPlugin  = "com.google.errorprone:javac:${javacPluginVersion}"

    // https://github.com/tbroyer/gradle-errorprone-plugin/releases
    object GradlePlugin {
        const val id = "net.ltgt.errorprone"
        /**
         * The version of this plugin is already specified in `buildSrc/build.gradle.kts` file.
         * Thus, when applying the plugin in projects build files, only the [id] should be used.
         *
         * When the plugin is used as a library (e.g. in tools), its version and the library
         * artifacts are of importance.
         */
        const val version = "3.1.0"
        const val lib = "net.ltgt.gradle:gradle-errorprone-plugin:${version}"
    }
}
