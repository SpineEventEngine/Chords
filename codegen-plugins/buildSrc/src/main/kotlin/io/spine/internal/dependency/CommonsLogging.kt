/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * [Commons Logging](https://commons.apache.org/proper/commons-logging/) is a transitive
 * dependency which we don't use directly. This object is used for forcing the version.
 */
@Suppress("unused", "ConstPropertyName")
object CommonsLogging {
    // https://commons.apache.org/proper/commons-logging/
    private const val version = "1.2"
    const val lib = "commons-logging:commons-logging:${io.spine.internal.dependency.CommonsLogging.version}"
}
