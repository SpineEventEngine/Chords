/*
 * Copyright 2025, TeamDev. All rights reserved.
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

/**
 * This script uses two declarations of the constant [licenseReportVersion] because
 * currently there is no way to define a constant _before_ a build script of `buildSrc`.
 * We cannot use imports or do something else before the `buildscript` or `plugin` clauses.
 */

plugins {
    java
    groovy
    `kotlin-dsl`
    kotlin("jvm") version "1.8.20" apply false

    // https://github.com/jk1/Gradle-License-Report/releases
    id("com.github.jk1.dependency-license-report").version("1.16")
}

repositories {
    mavenLocal()
    gradlePluginPortal()
    mavenCentral()
}

/**
 * The version of Spine Bootstrap plugin,
 * applied within this `buildSrc`.
 *
 * It is expected that this version is the same,
 * as the versions of Spine server- and client-related libraries
 * on top of which the Spine application is running.
 *
 * See `Spine` dependency object.
 */
val spineVersion = "1.9.0"

/**
 * The version of Jackson used by `buildSrc`.
 *
 * Please keep this value in sync with [io.spine.internal.dependency.Jackson.version].
 * It is not a requirement but would be good in terms of consistency.
 */
val jacksonVersion = "2.14.3"

/**
 * The version of Google Artifact Registry used by `buildSrc`.
 *
 * The version `2.1.5` is the latest before `2.2.0`, which introduces breaking changes.
 *
 * @see <a href="https://mvnrepository.com/artifact/com.google.cloud.artifactregistry/artifactregistry-auth-common">
 *     Google Artifact Registry at Maven</a>
 */
val googleAuthToolVersion = "2.1.5"

val licenseReportVersion = "1.16"

val grGitVersion = "4.1.1"

/**
 * The version of the Kotlin Gradle plugin and Kotlin binaries used by the build process.
 *
 * This version may change from the [version of Kotlin][io.spine.internal.dependency.Kotlin.version]
 * used by the project.
 */
val kotlinVersion = "1.8.22"

/**
 * The version of Guava used in `buildSrc`.
 *
 * Always use the same version as the one specified in [io.spine.internal.dependency.Guava].
 * Otherwise, when testing Gradle plugins, clashes may occur.
 */
val guavaVersion = "31.1-jre"

/**
 * The version of ErrorProne Gradle plugin.
 *
 * Please keep in sync. with [io.spine.internal.dependency.ErrorProne.GradlePlugin.version].
 *
 * @see <a href="https://github.com/tbroyer/gradle-errorprone-plugin/releases">
 *     Error Prone Gradle Plugin Releases</a>
 */
val errorPronePluginVersion = "3.1.0"

/**
 * The version of Protobuf Gradle Plugin.
 *
 * Please keep in sync. with [io.spine.internal.dependency.Protobuf.GradlePlugin.version].
 *
 * @see <a href="https://github.com/google/protobuf-gradle-plugin/releases">
 *     Protobuf Gradle Plugins Releases</a>
 */
//val protobufPluginVersion = "0.8.19"

/**
 * The version of Dokka Gradle Plugins.
 *
 * Please keep in sync with [io.spine.internal.dependency.Dokka.version].
 *
 * @see <a href="https://github.com/Kotlin/dokka/releases">
 *     Dokka Releases</a>
 */
val dokkaVersion = "1.9.20"

/**
 * The version of Detekt Gradle Plugin.
 *
 * @see <a href="https://github.com/detekt/detekt/releases">Detekt Releases</a>
 */
val detektVersion = "1.23.0"

/**
 * @see [io.spine.internal.dependency.Kotest]
 */
val kotestJvmPluginVersion = "0.3.8"

/**
 * @see [io.spine.internal.dependency.Kover]
 */
val koverVersion = "0.6.1"

configurations.all {
    resolutionStrategy {
        force(
            "com.google.guava:guava:${guavaVersion}",

            // Force Kotlin lib versions avoiding using those bundled with Gradle.
            "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion"
        )
    }
}

val jvmVersion = JavaLanguageVersion.of(11)

java {
    toolchain.languageVersion.set(jvmVersion)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = jvmVersion.toString()
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
}

dependencies {
    api("com.github.jk1:gradle-license-report:$licenseReportVersion")
    dependOnAuthCommon()

    listOf(
        "io.spine.tools:spine-bootstrap:$spineVersion",
        "com.fasterxml.jackson.core:jackson-databind:$jacksonVersion",
        "com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion",
        "com.github.jk1:gradle-license-report:$licenseReportVersion",
        "com.google.guava:guava:$guavaVersion",
        "io.gitlab.arturbosch.detekt:detekt-gradle-plugin:$detektVersion",
        "io.kotest:kotest-gradle-plugin:$kotestJvmPluginVersion",
        // https://github.com/srikanth-lingala/zip4j
        "net.lingala.zip4j:zip4j:2.10.0",
        "net.ltgt.gradle:gradle-errorprone-plugin:${errorPronePluginVersion}",
        "org.ajoberstar.grgit:grgit-core:${grGitVersion}",
        "org.jetbrains.dokka:dokka-base:${dokkaVersion}",
        "org.jetbrains.dokka:dokka-gradle-plugin:${dokkaVersion}",
        "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion",
        "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion",
        "org.jetbrains.kotlinx.kover:org.jetbrains.kotlinx.kover.gradle.plugin:$koverVersion",
        "org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:$kotlinVersion"
    ).forEach {
        implementation(it)
    }
}

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    /**
     * The version of the Shadow Plugin.
     *
     * `6.1.0` is the last version compatible with Gradle 6.9. Newer versions require Gradle v7+.
     *
     * @see <a href="https://github.com/johnrengelman/shadow/releases">Shadow Plugin releases</a>
     */
    val shadowVersion = "6.1.0"

    /**
     * @see [io.spine.internal.dependency.Kover]
     */
    val koverVersion = "0.6.1"

    dependencies {
        classpath("com.github.jengelman.gradle.plugins:shadow:$shadowVersion")
        classpath("org.jetbrains.kotlinx.kover:org.jetbrains.kotlinx.kover.gradle.plugin:$koverVersion")
    }
}

apply(plugin = "com.github.johnrengelman.shadow")

dependOnBuildSrcJar()

/**
 * Adds a dependency on a `buildSrc.jar`, iff:
 *  1) the `src` folder is missing, and
 *  2) `buildSrc.jar` is present in `buildSrc/` folder instead.
 *
 * This approach is used in the scope of integration testing.
 */
fun Project.dependOnBuildSrcJar() {
    val srcFolder = this.rootDir.resolve("src")
    val buildSrcJar = rootDir.resolve("buildSrc.jar")
    if (!srcFolder.exists() && buildSrcJar.exists()) {
        logger.info("Adding the pre-compiled 'buildSrc.jar' to 'implementation' dependencies.")
        dependencies {
            implementation(files("buildSrc.jar"))
        }
    }
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
