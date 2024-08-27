/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.dependency

/**
 * Testing framework for Kotlin.
 *
 * @see <a href="https://kotest.io/">Kotest site</a>
 */
object Kotest {
    private const val version = "5.8.0"

    object Runner {
        const val lib = "io.kotest:kotest-runner-junit5:$version"
    }
}
