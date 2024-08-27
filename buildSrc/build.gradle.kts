/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

plugins {
    `kotlin-dsl`

    // NOTE: If changing Kotlin version here, make sure to update the same in
    //       the `kotlinVersion` variable below.
    kotlin("jvm") version "1.8.20" apply false
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

/**
 * A version of the Kotlin language.
 *
 * NOTE: If updating here, make sure to also update the same in the `plugins`
 *       section above.
 */
val kotlinVersion = "1.8.20"

/**
 * A version of the Spine Gradle plugin.
 *
 */
val spineVersion = "1.9.0"

/**
 * A version of detekt (static code analysis tool for Kotlin).
 *
 * See [https://github.com/detekt/detekt](https://github.com/detekt/detekt)
 */
val detektVersion = "1.23.0"

/**
 * The version of the Jib Gradle plugin.
 */
val jibVersion = "3.2.1"

/**
 * The version of the Kotest library.
 */
val kotestPluginVersion = "0.4.10"

dependencies {
    implementation("io.spine.tools:spine-bootstrap:${spineVersion}")
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:$kotlinVersion")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:$detektVersion")
    implementation("gradle.plugin.com.google.cloud.tools:jib-gradle-plugin:${jibVersion}")
    implementation("io.kotest:kotest-gradle-plugin:$kotestPluginVersion")
}
