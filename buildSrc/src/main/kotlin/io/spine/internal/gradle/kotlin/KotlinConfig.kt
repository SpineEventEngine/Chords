/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
@Suppress("unused", "MagicNumber" /* Kotlin Compiler version. */)
fun KotlinCompile.setFreeCompilerArgs() {
    // Avoid the "unsupported flag warning" for Kotlin compilers pre 1.9.20.
    // See: https://youtrack.jetbrains.com/issue/KT-61573
    val expectActualClasses =
        if (KotlinVersion.CURRENT.isAtLeast(1, 9, 20)) "-Xexpect-actual-classes" else ""
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-Xskip-prerelease-check",
            "-Xjvm-default=all",
            "-Xinline-classes",

            // TODO:2024-08-29:dmitry.pikhulya: the line below is an addition relative to the
            //   `config` module's content. Consider update the `config` module accordingly
            //   (as an option?).
            //   See https://github.com/SpineEventEngine/Chords/issues/3
            "-Xcontext-receivers",
            expectActualClasses,
            "-opt-in=" +
                    "kotlin.contracts.ExperimentalContracts," +
                    "kotlin.io.path.ExperimentalPathApi," +
                    "kotlin.ExperimentalUnsignedTypes," +
                    "kotlin.ExperimentalStdlibApi," +
                    "kotlin.experimental.ExperimentalTypeInference"
        ).filter { it.isNotBlank() }
    }
}
