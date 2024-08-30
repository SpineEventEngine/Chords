/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

@Suppress("unused", "ConstPropertyName")
object JavaX {
    // This artifact which used to be a part of J2EE moved under Eclipse EE4J project.
    // https://github.com/eclipse-ee4j/common-annotations-api
    const val annotations = "javax.annotation:javax.annotation-api:1.3.2"

    const val servletApi = "javax.servlet:javax.servlet-api:3.1.0"
}
