/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * Gson is a transitive dependency which we don't use directly.
 * We `force` it in [DependencyResolution.forceConfiguration()].
 *
 * [Gson](https://github.com/google/gson)
 */
@Suppress("unused", "ConstPropertyName")
object Gson {
    private const val version = "2.10.1"
    const val lib = "com.google.code.gson:gson:${io.spine.internal.dependency.Gson.version}"
}
