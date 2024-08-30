/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

@file:Suppress("unused", "ConstPropertyName")

package io.spine.internal.dependency

// https://github.com/google/auto
object AutoCommon {
    private const val version = "1.2.2"
    const val lib = "com.google.auto:auto-common:${version}"
}

// https://github.com/google/auto
object AutoService {
    private const val version = "1.1.1"
    const val annotations = "com.google.auto.service:auto-service-annotations:${version}"
    @Suppress("unused")
    const val processor   = "com.google.auto.service:auto-service:${version}"
}

// https://github.com/google/auto
object AutoValue {
    private const val version = "1.10.2"
    const val annotations = "com.google.auto.value:auto-value-annotations:${version}"
}
