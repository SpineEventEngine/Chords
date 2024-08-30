/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * [J2ObjC](https://developers.google.com/j2objc) is a transitive dependency
 * which we don't use directly. This object is used for forcing the version.
 */
@Suppress("unused", "ConstPropertyName")
object J2ObjC {
    // https://github.com/google/j2objc/releases
    // `1.3.` is the latest version available from Maven Central.
    // https://search.maven.org/artifact/com.google.j2objc/j2objc-annotations
    private const val version = "1.3"
    const val annotations = "com.google.j2objc:j2objc-annotations:${version}"
    @Deprecated("Please use `annotations` instead.", ReplaceWith("annotations"))
    const val lib = annotations
}
