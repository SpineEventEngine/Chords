/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

@file:Suppress("MaxLineLength")

package io.spine.internal.dependency

/**
 * Gradle TestKit extension for Google Truth.
 *
 * @see <a href="https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/tree/main/testkit-truth">TestKit source code</a>
 * @see <a href="https://dev.to/autonomousapps/gradle-all-the-way-down-testing-your-gradle-plugin-with-gradle-testkit-2hmc">Usage description</a>
 */
@Suppress("unused", "ConstPropertyName")
object TestKitTruth {
    private const val version = "1.20.0"
    const val lib = "com.autonomousapps:testkit-truth:$version"
}
