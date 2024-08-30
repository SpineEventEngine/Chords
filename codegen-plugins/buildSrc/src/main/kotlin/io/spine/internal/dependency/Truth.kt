/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/google/truth
@Suppress("unused", "ConstPropertyName")
object Truth {
    private const val version = "1.1.5"
    val libs = listOf(
        "com.google.truth:truth:${version}",
        "com.google.truth.extensions:truth-java8-extension:${version}",
        "com.google.truth.extensions:truth-proto-extension:${version}"
    )
}
