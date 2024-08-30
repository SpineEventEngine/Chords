/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://junit.org/junit5/
@Suppress("unused", "ConstPropertyName")
object JUnit {
    const val version = "5.10.0"
    private const val legacyVersion = "4.13.1"

    // https://github.com/apiguardian-team/apiguardian
    private const val apiGuardianVersion = "1.1.2"

    // https://github.com/junit-pioneer/junit-pioneer
    private const val pioneerVersion = "2.0.1"

    const val legacy = "junit:junit:${legacyVersion}"

    val api = listOf(
        "org.apiguardian:apiguardian-api:${apiGuardianVersion}",
        "org.junit.jupiter:junit-jupiter-api:${version}",
        "org.junit.jupiter:junit-jupiter-params:${version}"
    )
    const val bom = "org.junit:junit-bom:${version}"

    const val runner = "org.junit.jupiter:junit-jupiter-engine:${version}"
    const val params = "org.junit.jupiter:junit-jupiter-params:${version}"

    const val pioneer = "org.junit-pioneer:junit-pioneer:${pioneerVersion}"

    object Platform {
        // https://junit.org/junit5/
        const val version = "1.10.0"
        internal const val group = "org.junit.platform"
        const val commons = "$group:junit-platform-commons:$version"
        const val launcher = "$group:junit-platform-launcher:$version"
        const val engine = "$group:junit-platform-engine:$version"
        const val suiteApi = "$group:junit-platform-suite-api:$version"
    }
}
