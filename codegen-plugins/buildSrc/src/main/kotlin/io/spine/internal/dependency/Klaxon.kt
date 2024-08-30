/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * A JSON parser in Kotlin.
 *
 * [Klaxon](https://github.com/cbeust/klaxon)
 */
@Suppress("unused", "ConstPropertyName")
object Klaxon {
    private const val version = "5.6"
    const val lib = "com.beust:klaxon:${io.spine.internal.dependency.Klaxon.version}"
}
