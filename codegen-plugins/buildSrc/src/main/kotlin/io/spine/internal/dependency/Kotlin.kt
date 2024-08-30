/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/JetBrains/kotlin
// https://github.com/Kotlin
@Suppress("unused", "ConstPropertyName")
object Kotlin {

    /**
     * When changing the version, also change the version used in the `buildSrc/build.gradle.kts`.
     */
    @Suppress("MemberVisibilityCanBePrivate") // used directly from the outside.
    const val version = "1.8.22"

    /**
     * The version of the JetBrains annotations library, which is a transitive
     * dependency for us via Kotlin libraries.
     *
     * @see <a href="https://github.com/JetBrains/java-annotations">Java Annotations</a>
     */
    private const val annotationsVersion = "24.0.1"

    private const val group = "org.jetbrains.kotlin"

    const val stdLib       = "${group}:kotlin-stdlib:${version}"
    const val stdLibCommon = "${group}:kotlin-stdlib-common:${version}"

    @Deprecated("Please use `stdLib` instead.")
    const val stdLibJdk7   = "${group}:kotlin-stdlib-jdk7:${version}"

    @Deprecated("Please use `stdLib` instead.")
    const val stdLibJdk8   = "${group}:kotlin-stdlib-jdk8:${version}"

    const val reflect    = "${group}:kotlin-reflect:${version}"
    const val testJUnit5 = "${group}:kotlin-test-junit5:${version}"

    const val gradlePluginApi = "${group}:kotlin-gradle-plugin-api:${version}"
    const val gradlePluginLib = "${group}:kotlin-gradle-plugin:${version}"

    const val jetbrainsAnnotations = "org.jetbrains:annotations:${annotationsVersion}"
}
