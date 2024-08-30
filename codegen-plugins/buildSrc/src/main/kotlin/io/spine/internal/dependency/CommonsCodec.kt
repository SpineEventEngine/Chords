/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://commons.apache.org/proper/commons-codec/changes-report.html
@Suppress("unused", "ConstPropertyName")
object CommonsCodec {
    private const val version = "1.16.0"
    const val lib = "commons-codec:commons-codec:$version"
}
