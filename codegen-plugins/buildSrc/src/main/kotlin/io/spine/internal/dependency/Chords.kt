/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * The dependency to some modules of the `1DAM` project.
 *
 * The value of the version should be the same as it is defined
 * in the `1DAM/version.gradle.kts`.
 */
@Suppress("unused", "ConstPropertyName")
object Chords {

    private const val group = "io.spine.chords"
    private const val version = "1.0.0-SNAPSHOT"

    object CodegenRuntime {
        const val lib = "${group}:codegen-runtime:${version}"
    }
}
