/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://checkerframework.org/
@Suppress("unused", "ConstPropertyName")
object CheckerFramework {
    private const val version = "3.37.0"
    const val annotations = "org.checkerframework:checker-qual:${version}"
    @Suppress("unused")
    val dataflow = listOf(
        "org.checkerframework:dataflow:${version}",
        "org.checkerframework:javacutil:${version}"
    )
}
