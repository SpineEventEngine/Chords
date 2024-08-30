/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * Dependencies on [Projects](https://github.com/Projects-tm/Server) modules.
 */
@Suppress("unused", "ConstPropertyName")
object Projects {

    object Users {
        private const val version = "0.8.16"
        const val lib = "com.teamdev.projects.v2:users:${version}"
    }
}
