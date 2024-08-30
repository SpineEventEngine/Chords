/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/z4kn4fein/kotlin-semver
@Suppress("unused", "ConstPropertyName")
object KotlinSemver {
    private const val version = "1.4.2"
    const val lib     = "io.github.z4kn4fein:semver:${io.spine.internal.dependency.KotlinSemver.version}"
}
