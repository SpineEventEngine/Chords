/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/google/flogger
@Suppress("unused", "ConstPropertyName")
object Flogger {
    internal const val version = "0.7.4"
    const val lib = "com.google.flogger:flogger:${version}"

    object Runtime {
        const val systemBackend = "com.google.flogger:flogger-system-backend:${version}"
        const val log4j2Backend = "com.google.flogger:flogger-log4j2-backend:${version}"
        const val slf4JBackend  = "com.google.flogger:flogger-slf4j-backend:${version}"
        const val grpcContext   = "com.google.flogger:flogger-grpc-context:${version}"
    }
}
