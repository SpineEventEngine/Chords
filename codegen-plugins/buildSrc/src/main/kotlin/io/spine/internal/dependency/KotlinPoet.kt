/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * The dependency on the Kotlin sources generator.
 *
 * [KotlinPoet Releases]](https://github.com/square/kotlinpoet/releases)
 */
@Suppress("unused", "ConstPropertyName")
object KotlinPoet {
    private const val version = "1.15.2"

    const val lib = "com.squareup:kotlinpoet:${io.spine.internal.dependency.KotlinPoet.version}"
}
