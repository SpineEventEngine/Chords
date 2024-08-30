/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

@Suppress("unused", "ConstPropertyName")
object KotlinX {

    const val group = "org.jetbrains.kotlinx"

    object Coroutines {

        // https://github.com/Kotlin/kotlinx.coroutines
        const val version = "1.7.3"
        const val core = "$group:kotlinx-coroutines-core:$version"
        const val jvm = "$group:kotlinx-coroutines-core-jvm:$version"
        const val jdk8 = "$group:kotlinx-coroutines-jdk8:$version"
    }
}
