/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
    "unused" /* Some subprojects do not use ProtoData directly. */,
    "ConstPropertyName",
    "MemberVisibilityCanBePrivate" /* The properties are used directly by other subprojects. */
)
object ProtoData {
    const val group = "io.spine.protodata"
    const val pluginId = "io.spine.protodata"

    /**
     * The version of ProtoData dependencies.
     */
    const val version: String = "0.50.0"

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
}
