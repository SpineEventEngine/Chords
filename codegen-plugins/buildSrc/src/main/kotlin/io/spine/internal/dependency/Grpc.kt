/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/grpc/grpc-java
@Suppress("unused", "ConstPropertyName")
object Grpc {
    @Suppress("MemberVisibilityCanBePrivate")
    const val version        = "1.59.0"
    const val api            = "io.grpc:grpc-api:${version}"
    const val auth           = "io.grpc:grpc-auth:${version}"
    const val core           = "io.grpc:grpc-core:${version}"
    const val context        = "io.grpc:grpc-context:${version}"
    const val inProcess      = "io.grpc:grpc-inprocess:${version}"
    const val stub           = "io.grpc:grpc-stub:${version}"
    const val okHttp         = "io.grpc:grpc-okhttp:${version}"
    const val protobuf       = "io.grpc:grpc-protobuf:${version}"
    const val protobufLite   = "io.grpc:grpc-protobuf-lite:${version}"
    const val netty          = "io.grpc:grpc-netty:${version}"
    const val nettyShaded    = "io.grpc:grpc-netty-shaded:${version}"

    object ProtocPlugin {
        const val id = "grpc"
        const val artifact = "io.grpc:protoc-gen-grpc-java:${version}"
    }
}
