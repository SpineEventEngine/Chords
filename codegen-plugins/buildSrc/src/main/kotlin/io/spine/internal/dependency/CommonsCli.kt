/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * Commons CLI is a transitive dependency which we don't use directly.
 * We `force` it in [forceVersions].
 *
 * [Commons CLI](https://commons.apache.org/proper/commons-cli/)
 */
@Suppress("unused", "ConstPropertyName")
object CommonsCli {
    private const val version = "1.5.0"
    const val lib = "commons-cli:commons-cli:${version}"
}
