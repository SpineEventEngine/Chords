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

package io.spine.internal.dependency

/**
 * Coordinates for the Spine APIs consumed by Chords libraries.
 */
@Suppress("ConstPropertyName")
object Spine {
    /**
     * Hosts the Spine runtime libraries.
     */
    const val group = "io.spine"

    /**
     * Hosts the Compiler and Gradle tooling.
     */
    const val toolsGroup = "io.spine.tools"

    /**
     * Provides the message and Protobuf APIs.
     */
    const val base = Base.lib

    /**
     * Provides runtime type inspection.
     */
    const val reflect = Reflect.lib

    /**
     * Supplies shared value types such as URLs and person names.
     */
    const val baseTypes = "$group:spine-base-types:2.0.0-SNAPSHOT.227"

    /**
     * Supplies the currency and attos-based monetary value model.
     */
    const val money = "$group:spine-money:2.0.0-SNAPSHOT.20"

    /**
     * Aligns the change model used transitively by the SDK.
     */
    const val change = "$group:spine-change:2.0.0-SNAPSHOT.210"

    /**
     * Keeps the Javadoc doclet version visible to the publishing helpers.
     */
    object ArtifactVersion {
        /**
         * The latest published Javadoc filtering tools.
         */
        const val javadocTools = "2.0.0-SNAPSHOT.75"
    }

    /**
     * Filters internal declarations from published Java documentation.
     */
    const val javadocFilter = "$toolsGroup:spine-javadoc-filter:${ArtifactVersion.javadocTools}"

    /**
     * Aligns client, entity, and server protocol APIs.
     */
    object CoreJvm {
        /**
         * The SDK release shared by all core artifacts.
         */
        const val version = "2.0.0-SNAPSHOT.612"

        /**
         * Supplies entity, command, and event contracts.
         */
        const val core = "$group:spine-core:$version"

        /**
         * Supplies the gRPC client and query protocol.
         */
        const val client = "$group:spine-client:$version"

        /**
         * Aligns the server classes used by the Compiler plugins.
         */
        const val server = "$group:spine-server:$version"
    }

    /**
     * Loads the Chords generator into Spine Compiler.
     */
    object Chords {
        /**
         * Resolves the generator built alongside the runtime.
         */
        object CodegenPlugins {
            /**
             * Selects the generator for this Chords release.
             */
            fun artifact(version: String): String =
                "$group.chords:spine-chords-codegen-plugins:$version"
        }
    }
}
