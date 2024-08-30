/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

/**
 * Dependencies on Spine Validation SDK.
 *
 * See [`SpineEventEngine/validation`](https://github.com/SpineEventEngine/validation/).
 */
@Suppress("unused", "ConstPropertyName")
object Validation {
    const val version = "2.0.0-SNAPSHOT.126"
    const val group = "io.spine.validation"
    const val runtime = "${group}:spine-validation-java-runtime:${version}"
    const val java = "${group}:spine-validation-java:${version}"
    const val javaBundle = "${group}:spine-validation-java-bundle:${version}"
    const val model = "${group}:spine-validation-model:${version}"
    const val config = "${group}:spine-validation-configuration:${version}"
}
