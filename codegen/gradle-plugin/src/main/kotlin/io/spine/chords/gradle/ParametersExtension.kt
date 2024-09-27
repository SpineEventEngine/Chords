/*
 * Copyright 2024, TeamDev. All rights reserved.
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

package io.spine.chords.gradle

import io.spine.chords.gradle.ParametersExtension.Companion.extensionName
import org.gradle.api.Project

/**
 * The extension for [GradlePlugin] that allows to apply some parameters
 * in Gradle build script.
 *
 * The sample plugin configuration:
 * ```
 * chordsGradlePlugin {
 *     protoDependencies("io.spine:spine-money:1.5.0")
 * }
 * ```
 */
public class ParametersExtension {

    @Suppress("ConstPropertyName")
    internal companion object {
        internal const val extensionName = "chordsGradlePlugin"
    }

    internal val dependencies: MutableSet<String> = mutableSetOf()

    /**
     * Allows to specify dependencies on Proto sources that are required
     * to generate the code.
     */
    public fun protoDependencies(vararg protoDependencies: String) {
        dependencies.clear()
        dependencies.addAll(protoDependencies)
    }
}

/**
 * Creates extension which allows to configure the plugin in Gradle build script.
 */
internal fun Project.createExtension(): ParametersExtension {
    val extension = ParametersExtension()
    extensions.add(ParametersExtension::class.java, extensionName, extension)
    return extension
}
