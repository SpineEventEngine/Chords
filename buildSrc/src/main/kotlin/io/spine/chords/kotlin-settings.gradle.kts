/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import org.gradle.kotlin.dsl.repositories
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Configures Kotlin-related plugins and tasks.
 */

plugins {
    kotlin("jvm")
    id("detekt-code-analysis")
}

repositories {
    mavenCentral()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    explicitApi()
}
