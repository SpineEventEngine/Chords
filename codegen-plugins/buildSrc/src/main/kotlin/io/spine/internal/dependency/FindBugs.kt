/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * The FindBugs project is dead since 2017. It has a successor called SpotBugs, but we don't use it.
 * We use ErrorProne for static analysis instead. The only reason for having this dependency is
 * the annotations for null-checking introduced by JSR-305. These annotations are troublesome,
 * but no alternatives are known for some of them so far.  Please see
 * [this issue](https://github.com/SpineEventEngine/base/issues/108) for more details.
 */
@Suppress("unused", "ConstPropertyName")
object FindBugs {
    private const val version = "3.0.2"
    const val annotations = "com.google.code.findbugs:jsr305:${version}"
}
