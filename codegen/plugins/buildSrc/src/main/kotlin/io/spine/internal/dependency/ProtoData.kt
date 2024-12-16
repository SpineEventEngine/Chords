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

package io.spine.internal.dependency

/**
* Dependencies on ProtoData modules.
*
* See [`SpineEventEngine/ProtoData`](https://github.com/SpineEventEngine/ProtoData/).
*/
@Suppress(
    "unused" /* Some subprojects do not use ProtoData directly. */,
    "ConstPropertyName" /* We use custom convention for artifact properties. */,
    "MemberVisibilityCanBePrivate" /* The properties are used directly by other subprojects. */
)
object ProtoData {
    const val pluginGroup = Spine.group
    const val group = "io.spine.protodata"
    const val pluginId = "io.spine.protodata"

    /**
     * The version of ProtoData dependencies.
     */
    const val version = "0.70.3"

    /**
     * Identifies ProtoData as a `classpath` dependency under `buildScript` block.
     *
     * The dependency is obtained from https://plugins.gradle.org/m2/.
     */
    const val lib = "io.spine:protodata:$version"

    /**
     * The artifact for the ProtoData Gradle plugin.
     */
    const val pluginLib =  "$group:gradle-plugin:$version"

    fun api(version: String): String =
        "$group:protodata-api:$version"

    val api
        get() = api(version)

    val backend
        get() = "$group:protodata-backend:$version"

    val protocPlugin
        get() = "$group:protodata-protoc:$version"

    val gradleApi
        get() = "$group:protodata-gradle-api:$version"

    val cliApi
        get() = "$group:protodata-cli-api:$version"

    fun java(version: String): String =
        "$group:protodata-java:$version"

    val java
        get() = java(version)

    val fatCli
        get() = "$group:protodata-fat-cli:$version"

    val testlib
        get() = "$group:protodata-testlib:$version"
}
