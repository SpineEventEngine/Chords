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
 * To use a locally published ProtoData version instead of the version from a public plugin
 * registry, set the `PROTODATA_VERSION` and/or the `PROTODATA_DF_VERSION` environment variables
 * and stop the Gradle daemons so that Gradle observes the env change:
 * ```
 * export PROTODATA_VERSION=0.43.0-local
 * export PROTODATA_DF_VERSION=0.41.0
 *
 * ./gradle --stop
 * ./gradle build   # Conduct the intended checks.
 * ```
 *
 * Then, to reset the console to run the usual versions again, remove the values of
 * the environment variables and stop the daemon:
 * ```
 * export PROTODATA_VERSION=""
 * export PROTODATA_DF_VERSION=""
 *
 * ./gradle --stop
 * ```
 *
 * See [`SpineEventEngine/ProtoData`](https://github.com/SpineEventEngine/ProtoData/).
 */
@Suppress(
    "ConstPropertyName",
    "MemberVisibilityCanBePrivate" /* The properties are used directly by other subprojects. */
)
object ProtoData {
    const val group = "io.spine.protodata"

    /**
     * The version of ProtoData dependencies.
     */
    const val version: String = "0.60.2"

    const val lib: String =
        "io.spine:protodata:$version"

    const val pluginLib: String =
        "$group:gradle-plugin:$version"

    fun api(version: String): String =
        "$group:protodata-api:$version"

    val api
        get() = api(version)

    @Deprecated("Use `backend` instead", ReplaceWith("backend"))
    val compiler
        get() = backend

    val backend
        get() = "$group:protodata-backend:$version"

    fun java(version: String): String =
        "$group:protodata-java:$version"

    val java
        get() = java(version)
}
