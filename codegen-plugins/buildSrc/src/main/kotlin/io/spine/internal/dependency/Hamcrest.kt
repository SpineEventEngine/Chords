/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * The dependency on the Hamcrest, which is transitive for us.
 *
 * If you need assertions in Java, please use Google [Truth] instead.
 * For Kotlin, please use [Kotest].
 */
@Suppress("unused", "ConstPropertyName")
object Hamcrest {
    // https://github.com/hamcrest/JavaHamcrest/releases
    private const val version = "2.2"
    const val core = "org.hamcrest:hamcrest-core:${version}"
}
