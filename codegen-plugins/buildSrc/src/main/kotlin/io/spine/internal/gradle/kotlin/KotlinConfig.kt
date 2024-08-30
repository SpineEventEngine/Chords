/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.gradle.kotlin

import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Sets [Java toolchain](https://kotlinlang.org/docs/gradle.html#gradle-java-toolchains-support)
 * to the specified version (e.g. 11 or 8).
 */
fun KotlinJvmProjectExtension.applyJvmToolchain(version: Int) {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(version))
    }
}

/**
 * Sets [Java toolchain](https://kotlinlang.org/docs/gradle.html#gradle-java-toolchains-support)
 * to the specified version (e.g. "11" or "8").
 */
@Suppress("unused")
fun KotlinJvmProjectExtension.applyJvmToolchain(version: String) =
    applyJvmToolchain(version.toInt())

/**
 * Opts-in to experimental features that we use in our codebase.
 */
@Suppress("unused")
fun KotlinCompile.setFreeCompilerArgs() {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-Xskip-prerelease-check",
            "-Xjvm-default=all",
            "-Xinline-classes",
            "-opt-in=" +
                    "kotlin.contracts.ExperimentalContracts," +
                    "kotlin.io.path.ExperimentalPathApi," +
                    "kotlin.ExperimentalUnsignedTypes," +
                    "kotlin.ExperimentalStdlibApi," +
                    "kotlin.experimental.ExperimentalTypeInference," +
                    "kotlin.RequiresOptIn"
        )
    }
}
