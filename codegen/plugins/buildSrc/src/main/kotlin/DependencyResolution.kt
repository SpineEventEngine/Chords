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

import io.spine.dependency.build.CheckerFramework
import io.spine.dependency.build.ErrorProne
import io.spine.dependency.build.FindBugs
import io.spine.dependency.lib.Asm
import io.spine.dependency.lib.AutoCommon
import io.spine.dependency.lib.AutoService
import io.spine.dependency.lib.AutoValue
import io.spine.dependency.lib.CommonsCli
import io.spine.dependency.lib.CommonsCodec
import io.spine.dependency.lib.CommonsLogging
import io.spine.dependency.lib.Gson
import io.spine.dependency.lib.Guava
import io.spine.dependency.lib.J2ObjC
import io.spine.dependency.lib.Jackson
import io.spine.dependency.lib.JavaDiffUtils
import io.spine.dependency.lib.Kotlin
import io.spine.dependency.lib.KotlinX
import io.spine.dependency.lib.Protobuf
import io.spine.dependency.lib.Slf4J
import io.spine.dependency.test.Hamcrest
import io.spine.dependency.test.JUnit
import io.spine.dependency.test.Kotest
import io.spine.dependency.test.OpenTest4J
import io.spine.dependency.test.Truth
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.invoke

/**
 * The function to be used in `buildscript` when a fully-qualified call must be made.
 */
@Suppress("unused")
fun doForceVersions(configurations: ConfigurationContainer) {
    configurations.forceVersions()

    val validation = io.spine.dependency.local.Validation
    val protoData = io.spine.dependency.local.ProtoData
    val logging = io.spine.dependency.local.Logging
    val reflect = io.spine.dependency.local.Reflect
    val base = io.spine.dependency.local.Base
    val toolBase = io.spine.dependency.local.ToolBase
    val coreJava = io.spine.dependency.local.CoreJava
    val time = io.spine.dependency.local.Time

    configurations {
        all {
            exclude(group = "io.spine", module = "spine-logging-backend")

            resolutionStrategy {
                force(
                    io.spine.dependency.lib.Grpc.api,
                    reflect.lib,
                    base.lib,
                    toolBase.lib,
                    coreJava.server,
                    protoData.pluginLib,
                    protoData.lib,
                    logging.lib,
                    logging.middleware,
                    time.lib,
                    validation.runtime,
                    validation.javaBundle
                )
            }
        }
    }
}

/**
 * Forces dependencies used in the project.
 */
fun NamedDomainObjectContainer<Configuration>.forceVersions() {
    all {
        resolutionStrategy {
            failOnVersionConflict()
            cacheChangingModulesFor(0, "seconds")
            forceProductionDependencies()
            forceTestDependencies()
            forceTransitiveDependencies()
        }
    }
}

private fun ResolutionStrategy.forceProductionDependencies() {
    @Suppress("DEPRECATION") // Force versions of SLF4J and Kotlin libs.
    force(
        AutoCommon.lib,
        AutoService.annotations,
        CheckerFramework.annotations,
        ErrorProne.annotations,
        ErrorProne.core,
        FindBugs.annotations,
        Gson.lib,
        Guava.lib,
        Kotlin.reflect,
        Kotlin.stdLib,
        Kotlin.stdLibCommon,
        Kotlin.stdLibJdk7,
        Kotlin.stdLibJdk8,
        KotlinX.Coroutines.core,
        KotlinX.Coroutines.jvm,
        KotlinX.Coroutines.jdk8,
        KotlinX.Coroutines.bom,
        KotlinX.Coroutines.slf4j,
        Protobuf.GradlePlugin.lib,
        Protobuf.libs,
        Slf4J.lib
    )
}

private fun ResolutionStrategy.forceTestDependencies() {
    force(
        Guava.testLib,
        JUnit.api,
        JUnit.bom,
        JUnit.Platform.commons,
        JUnit.Platform.launcher,
        JUnit.legacy,
        Truth.libs,
        Kotest.assertions,
    )
}

/**
 * Forces transitive dependencies of 3rd party components that we don't use directly.
 */
private fun ResolutionStrategy.forceTransitiveDependencies() {
    force(
        Asm.lib,
        AutoValue.annotations,
        CommonsCli.lib,
        CommonsCodec.lib,
        CommonsLogging.lib,
        Gson.lib,
        Hamcrest.core,
        J2ObjC.annotations,
        JUnit.Platform.engine,
        JUnit.Platform.suiteApi,
        JUnit.runner,
        Jackson.annotations,
        Jackson.bom,
        Jackson.core,
        Jackson.databind,
        Jackson.dataformatXml,
        Jackson.dataformatYaml,
        Jackson.moduleKotlin,
        JavaDiffUtils.lib,
        Kotlin.jetbrainsAnnotations,
        OpenTest4J.lib,
    )
}

@Suppress("unused")
fun NamedDomainObjectContainer<Configuration>.excludeProtobufLite() {

    fun excludeProtoLite(configurationName: String) {
        named(configurationName).get().exclude(
            mapOf(
                "group" to "com.google.protobuf",
                "module" to "protobuf-lite"
            )
        )
    }

    excludeProtoLite("runtimeOnly")
    excludeProtoLite("testRuntimeOnly")
}
