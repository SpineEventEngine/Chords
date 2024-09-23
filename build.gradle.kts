/*
 * Copyright 2024, TeamDev. All rights reserved.
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

@file:Suppress("RemoveRedundantQualifierName")

import Build_gradle.Module
import io.spine.internal.dependency.Protobuf
import io.spine.internal.gradle.javac.configureJavac
import io.spine.internal.gradle.kotlin.applyJvmToolchain
import io.spine.internal.gradle.kotlin.setFreeCompilerArgs
import io.spine.internal.gradle.standardToSpineSdk
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
    id("com.google.protobuf")
    id("io.spine.protodata") version "0.60.2"
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
        // To access Spine 1.9.x.
        maven {
            url = uri("https://spine.mycloudrepo.io/public/repositories/releases")
            mavenContent {
                releasesOnly()
            }
        }
        // To access Projects Users.
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
        plugin("com.google.protobuf")
        plugin("idea")
    }

    dependencies {
        Protobuf.libs.forEach { implementation(it) }
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

fun Module.configureJava() {
    java {
        toolchain.languageVersion.set(BuildSettings.javaVersion)
    }
    tasks {
        withType<JavaCompile>().configureEach {
            configureJavac()
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
