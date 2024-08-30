/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * Dependencies on ProtoTap plugins.
 *
 * See [`SpineEventEngine/ProtoTap`](https://github.com/SpineEventEngine/ProtoTap/).
 */
@Suppress(
    "unused" /* Some subprojects do not use ProtoData directly. */,
    "ConstPropertyName" /* We use custom convention for artifact properties. */,
    "MemberVisibilityCanBePrivate" /* The properties are used directly by other subprojects. */,
)
object ProtoTap {
    const val group = "io.spine.tools"
    const val version = "0.8.7"
    const val gradlePluginId = "io.spine.prototap"
    const val api = "$group:prototap-api:$version"
    const val gradlePlugin = "$group:prototap-gradle-plugin:$version"
    const val protocPlugin = "$group:prototap-protoc-plugin:$version"
}
