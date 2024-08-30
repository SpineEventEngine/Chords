/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

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
 * The version of Jackson used by `buildSrc`.
 *
 * Please keep this value in sync. with `io.spine.internal.dependency.Jackson.version`.
 * It's not a requirement, but would be good in terms of consistency.
 */
val jacksonVersion = "2.13.4"

/**
 * The version of the Kotlin Gradle plugin.
 *
 * Please check that this value matches one defined in
 *  [i o.spine.internal.dependency.Kotlin.version].
 */
val kotlinVersion = "1.8.22"

/**
 * The version of Guava used in `buildSrc`.
 *
 * Always use the same version as the one specified in [io.spine.internal.dependency.Guava].
 * Otherwise, when testing Gradle plugins, clashes may occur.
 */
val guavaVersion = "32.1.2-jre"

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
val protobufPluginVersion = "0.9.4"

/**
 * The version of Detekt Gradle Plugin.
 *
 * @see <a href="https://github.com/detekt/detekt/releases">Detekt Releases</a>
 */
val detektVersion = "1.23.0"

/**
 * @see [io.spine.internal.dependency.Kotest]
 */
val kotestJvmPluginVersion = "0.4.10"

/**
 * @see [io.spine.internal.dependency.Kover]
 */
val koverVersion = "0.7.2"

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

val jvmVersion = JavaLanguageVersion.of(11)

java {
    toolchain.languageVersion.set(jvmVersion)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = jvmVersion.toString()
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")

    implementation("com.google.guava:guava:$guavaVersion")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:${errorPronePluginVersion}")

    // Add explicit dependency to avoid warning on different Kotlin runtime versions.
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")

    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:$detektVersion")
    implementation("com.google.protobuf:protobuf-gradle-plugin:$protobufPluginVersion")

    // https://github.com/srikanth-lingala/zip4j
    implementation("net.lingala.zip4j:zip4j:2.10.0")

    implementation("io.kotest:kotest-gradle-plugin:$kotestJvmPluginVersion")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:$koverVersion")
}

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
