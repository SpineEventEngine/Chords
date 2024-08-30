/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/protocolbuffers/protobuf
@Suppress(
    "MemberVisibilityCanBePrivate" /* used directly from the outside */,
    "ConstPropertyName"
)
object Protobuf {
    const val group = "com.google.protobuf"
    const val version       = "3.25.1"
    /**
     * The Java library containing proto definitions of Google Protobuf.
     */
    const val protoSrcLib = "${group}:protobuf-java:${version}"
    val libs = listOf(
        protoSrcLib,
        "${group}:protobuf-java-util:${version}",
        "${group}:protobuf-kotlin:${version}"
    )
    const val compiler = "${group}:protoc:${version}"

    // https://github.com/google/protobuf-gradle-plugin/releases
    object GradlePlugin {
        /**
         * The version of this plugin is already specified in `buildSrc/build.gradle.kts` file.
         * Thus, when applying the plugin to projects build files, only the [id] should be used.
         *
         * When changing the version, also change the version used in the `build.gradle.kts`.
         */
        const val version = "0.9.4"
        const val id = "com.google.protobuf"
        const val lib = "${group}:protobuf-gradle-plugin:${version}"
    }
}
