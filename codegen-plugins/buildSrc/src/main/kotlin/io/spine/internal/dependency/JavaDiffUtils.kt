/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * The dependency on the `java-diff-utils` library, which is transitive for us at the time
 * of writing.
 *
 * It might become our dependency as a part of
 * the [Spine Text](https://github.com/SpineEventEngine/text) library.
 */
@Suppress("unused", "ConstPropertyName")
object JavaDiffUtils {

    // https://github.com/java-diff-utils/java-diff-utils/releases
    private const val version = "4.12"
    const val lib = "io.github.java-diff-utils:java-diff-utils:${io.spine.internal.dependency.JavaDiffUtils.version}"
}
