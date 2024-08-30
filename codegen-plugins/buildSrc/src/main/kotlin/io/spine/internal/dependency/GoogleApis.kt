/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * Provides dependencies on [GoogleApis projects](https://github.com/googleapis/).
 */
@Suppress("unused", "ConstPropertyName")
object GoogleApis {

    // https://github.com/googleapis/google-api-java-client
    const val client = "com.google.api-client:google-api-client:1.32.2"

    // https://github.com/googleapis/api-common-java
    const val common = "com.google.api:api-common:2.1.1"

    // https://github.com/googleapis/java-common-protos
    const val commonProtos = "com.google.api.grpc:proto-google-common-protos:2.7.0"

    // https://github.com/googleapis/gax-java
    const val gax = "com.google.api:gax:2.7.1"

    // https://github.com/googleapis/java-iam
    const val protoAim = "com.google.api.grpc:proto-google-iam-v1:1.2.0"

    // https://github.com/googleapis/google-oauth-java-client
    const val oAuthClient = "com.google.oauth-client:google-oauth-client:1.32.1"

    // https://github.com/googleapis/google-auth-library-java
    object AuthLibrary {
        const val version = "1.3.0"
        const val credentials = "com.google.auth:google-auth-library-credentials:${version}"
        const val oAuth2Http = "com.google.auth:google-auth-library-oauth2-http:${version}"
    }
}
