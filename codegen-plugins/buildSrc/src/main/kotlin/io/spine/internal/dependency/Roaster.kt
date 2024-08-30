/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/forge/roaster
@Suppress("unused", "ConstPropertyName")
object Roaster {

    /**
     * This is the last version build with Java 11.
     *
     * Starting from version
     * [2.29.0.Final](https://github.com/forge/roaster/releases/tag/2.29.0.Final),
     * Roaster requires Java 17.
     */
    private const val version = "2.28.0.Final"

    const val api = "org.jboss.forge.roaster:roaster-api:${version}"
    const val jdt = "org.jboss.forge.roaster:roaster-jdt:${version}"
}
