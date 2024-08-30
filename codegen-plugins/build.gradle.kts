/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

@file:Suppress("RemoveRedundantQualifierName")

import Build_gradle.Module
import io.spine.internal.dependency.ErrorProne
import io.spine.internal.dependency.Protobuf
import io.spine.internal.gradle.javac.configureErrorProne
import io.spine.internal.gradle.javac.configureJavac
import io.spine.internal.gradle.kotlin.applyJvmToolchain
import io.spine.internal.gradle.kotlin.setFreeCompilerArgs
import io.spine.internal.gradle.standardToSpineSdk
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    standardSpineSdkRepositories()

    doForceVersions(configurations)

    dependencies {
        classpath(io.spine.internal.dependency.Spine.McJava.pluginLib)
    }
}

plugins {
    kotlin("jvm")
    id("net.ltgt.errorprone")
    id("detekt-code-analysis")
    id("com.google.protobuf")
    id("io.spine.protodata") version "0.50.0"
    idea
}

object BuildSettings {
    private const val JAVA_VERSION = 11
    val javaVersion: JavaLanguageVersion = JavaLanguageVersion.of(JAVA_VERSION)
}

allprojects {
    // Define the repositories universally for all modules, including the root.
    repositories.standardToSpineSdk()

    repositories {
        maven {
            url = uri("https://spine.mycloudrepo.io/public/repositories/releases")
            mavenContent {
                releasesOnly()
            }
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Projects-tm/Server")
            credentials {
                username = System.getenv("GITHUB_USERNAME")
                    ?: System.getenv("PROJECTSTM_PACKAGES_USER")
                password = System.getenv("GITHUB_TOKEN")
                    ?: System.getenv("PROJECTSTM_PACKAGES_TOKEN")
            }
        }
    }
}

// It is assumed that every module in the project requires
// a typical configuration.
subprojects {
    apply {
        plugin("kotlin")
        plugin("net.ltgt.errorprone")
        plugin("detekt-code-analysis")
        plugin("com.google.protobuf")
        plugin("idea")
    }

    if (name.contains("message-fields")) {
        // Only apply this plugin to the project, which code is Spine-based
        // (otherwise, it wouldn't compile).
        apply {
            plugin("io.spine.mc-java")
        }
    }

    dependencies {
        Protobuf.libs.forEach { implementation(it) }

        ErrorProne.apply {
            errorprone(core)
        }
    }

    protobuf {
        protoc {
            artifact = Protobuf.compiler
        }
    }

    // Apply a typical configuration to every module.
    applyConfiguration()
}

/**
 * The alias for typed extensions functions related to modules of this project.
 */
typealias Module = Project

fun Module.applyConfiguration() {
    configureJava()
    configureKotlin()
    setUpTests()
    applyGeneratedDirectories()
}

fun Module.configureKotlin() {
    kotlin {
        explicitApi()
        applyJvmToolchain(BuildSettings.javaVersion.toString())
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = BuildSettings.javaVersion.toString()
        }
        setFreeCompilerArgs()
    }
}

fun Module.setUpTests() {
    tasks.test {
        useJUnitPlatform()

        testLogging {
            events = setOf(
                TestLogEvent.PASSED,
                TestLogEvent.FAILED,
                TestLogEvent.SKIPPED
            )
            showExceptions = true
            showCauses = true
        }
    }
}

fun Module.configureJava() {
    java {
        toolchain.languageVersion.set(BuildSettings.javaVersion)
    }
    tasks {
        withType<JavaCompile>().configureEach {
            configureJavac()
            configureErrorProne()
        }
        withType<org.gradle.jvm.tasks.Jar>().configureEach {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }
}

/**
 * Adds directories with the generated source code to source sets
 * of the project and to IntelliJ IDEA module settings.
 */
fun Module.applyGeneratedDirectories() {

    /* The name of the root directory with the generated code. */
    val generatedDir = "${projectDir}/generated"

    val generatedMain = "$generatedDir/main"
    val generatedJava = "$generatedMain/java"
    val generatedKotlin = "$generatedMain/kotlin"
    val generatedGrpc = "$generatedMain/grpc"
    val generatedSpine = "$generatedMain/spine"

    val generatedTest = "$generatedDir/test"
    val generatedTestJava = "$generatedTest/java"
    val generatedTestKotlin = "$generatedTest/kotlin"
    val generatedTestGrpc = "$generatedTest/grpc"
    val generatedTestSpine = "$generatedTest/spine"

    idea {
        module {
            generatedSourceDirs.addAll(
                files(
                    generatedJava,
                    generatedKotlin,
                    generatedGrpc,
                    generatedSpine,
                )
            )
            testSources.from(
                generatedTestJava,
                generatedTestKotlin,
                generatedTestGrpc,
                generatedTestSpine,
            )
            isDownloadJavadoc = true
            isDownloadSources = true
        }
    }
}
