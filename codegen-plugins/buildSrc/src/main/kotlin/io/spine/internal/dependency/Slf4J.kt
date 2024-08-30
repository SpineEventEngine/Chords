/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * Spine used to log with SLF4J. Now we use Flogger. Whenever a choice comes up, we recommend to
 * use the latter.
 *
 * The primary purpose of having this dependency object is working in combination with
 * [Flogger.Runtime.slf4JBackend].
 *
 * Some third-party libraries may clash with different versions of the library.
 * Thus, we specify this version and force it via [forceVersions].
 * Please see `DependencyResolution.kt` for details.
 */
@Suppress("unused", "ConstPropertyName")
object Slf4J {
    private const val version = "2.0.7"
    const val lib = "org.slf4j:slf4j-api:${version}"
    const val jdk14 = "org.slf4j:slf4j-jdk14:${version}"
    const val reload4j = "org.slf4j:slf4j-reload4j:${version}"
    const val simple = "org.slf4j:slf4j-simple:${version}"
}
