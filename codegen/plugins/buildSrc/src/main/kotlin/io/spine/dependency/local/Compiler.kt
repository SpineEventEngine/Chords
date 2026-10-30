/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.dependency.local

/**
 * Dependencies on the Spine Compiler.
 */
@Suppress("ConstPropertyName")
object Compiler {
    /**
     * The Compiler release used by the generator and its workspace.
     */
    private const val version = "2.0.0-SNAPSHOT.082"

    /**
     * Provides the Gradle plugin on the buildscript classpath.
     */
    const val pluginLib = "${Spine.toolsGroup}:compiler-gradle-plugin:$version"

    /**
     * Provides model events, views, and rendering APIs for Chords plugins.
     */
    const val api = "${Spine.toolsGroup}:compiler-api:$version"

    /**
     * Resolves Java types and accessors from the Protobuf model.
     */
    const val jvm = "${Spine.toolsGroup}:compiler-jvm:$version"

    /**
     * Runs the CLI with separately resolved dependencies instead of bundled Spine classes.
     */
    const val cli = "${Spine.toolsGroup}:compiler-cli:$version"

    /**
     * Identifies the bundled distribution requested by the Compiler Gradle plugin.
     */
    const val bundledCliModule = "${Spine.toolsGroup}:compiler-cli-all"

    /**
     * Keeps transitive Compiler modules on the release used by the Gradle plugin.
     */
    val libs = listOf(
        "compiler-api",
        "compiler-backend",
        "compiler-cli",
        "compiler-gradle-api",
        "compiler-gradle-plugin",
        "compiler-jvm",
        "compiler-params",
        "compiler-protoc-plugin"
    )
        .map { "${Spine.toolsGroup}:$it:$version" }
}
