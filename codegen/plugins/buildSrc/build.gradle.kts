/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    gradlePluginPortal()
    mavenCentral()
}

/**
 * The version of the Kotlin Gradle plugin.
 *
 * Please check that this value matches one defined in
 *  [i o.spine.internal.dependency.Kotlin.version].
 */
val kotlinVersion = "2.4.10"

/**
 * The version of Guava used in `buildSrc`.
 *
 * Always use the same version as the one specified in [io.spine.dependency.lib.Guava].
 * Otherwise, when testing Gradle plugins, clashes may occur.
 */
val guavaVersion = "33.5.0-jre"

/**
 * The version of ErrorProne Gradle plugin.
 *
 * @see <a href="https://github.com/tbroyer/gradle-errorprone-plugin/releases">
 *     Error Prone Gradle Plugin Releases</a>
 */
val errorPronePluginVersion = "4.2.0"

/**
 * The version of Protobuf Gradle Plugin.
 *
 * Please keep in sync. with [io.spine.dependency.lib.Protobuf.GradlePlugin.version].
 *
 * @see <a href="https://github.com/google/protobuf-gradle-plugin/releases">
 *     Protobuf Gradle Plugins Releases</a>
 */
val protobufPluginVersion = "0.10.0"

/**
 * The version of Detekt Gradle Plugin.
 *
 * @see <a href="https://github.com/detekt/detekt/releases">Detekt Releases</a>
 */
val detektVersion = "2.0.0-alpha.1"

/**
 * The version of Google Artifact Registry used by `buildSrc`.
 *
 * The version `2.1.5` is the latest before `2.2.0`, which introduces breaking changes.
 */
val googleAuthToolVersion = "2.1.5"

configurations.all {
    resolutionStrategy {
        force(
            "com.google.protobuf:protobuf-gradle-plugin:$protobufPluginVersion",

            // Force Kotlin lib versions avoiding using those bundled with Gradle.
            "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion"
        )
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    dependOnAuthCommon()

    implementation("com.google.guava:guava:$guavaVersion")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:${errorPronePluginVersion}")

    // Add explicit dependency to avoid warning on different Kotlin runtime versions.
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")

    implementation("dev.detekt:detekt-gradle-plugin:$detektVersion")
    implementation("com.google.protobuf:protobuf-gradle-plugin:$protobufPluginVersion")

    // https://github.com/srikanth-lingala/zip4j
    implementation("net.lingala.zip4j:zip4j:2.10.0")
}

/**
 * Includes the `implementation` dependency on `artifactregistry-auth-common`,
 * with the version defined in [googleAuthToolVersion].
 *
 * `artifactregistry-auth-common` has transitive dependency on Gson and Apache `commons-codec`.
 * Gson from version `2.8.6` until `2.8.9` is vulnerable to Deserialization of Untrusted Data
 * (https://devhub.checkmarx.com/cve-details/CVE-2022-25647/).
 *
 *  Apache `commons-codec` before 1.13 is vulnerable to information exposure
 * (https://devhub.checkmarx.com/cve-details/Cxeb68d52e-5509/).
 *
 * We use Gson `2.10.1` and we force it in `forceProductionDependencies()`.
 * We use `commons-code` with version `1.16.0`, forcing it in `forceProductionDependencies()`.
 *
 * So, we should be safe with the current version `artifactregistry-auth-common` until
 * we migrate to a later version.
 */
fun DependencyHandlerScope.dependOnAuthCommon() {
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    implementation(
        "com.google.cloud.artifactregistry:artifactregistry-auth-common:$googleAuthToolVersion"
    ) {
        exclude(group = "com.google.guava")
    }
}
