/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Asm
import io.spine.internal.dependency.AutoCommon
import io.spine.internal.dependency.AutoService
import io.spine.internal.dependency.AutoValue
import io.spine.internal.dependency.CheckerFramework
import io.spine.internal.dependency.CommonsCli
import io.spine.internal.dependency.CommonsCodec
import io.spine.internal.dependency.CommonsLogging
import io.spine.internal.dependency.ErrorProne
import io.spine.internal.dependency.FindBugs
import io.spine.internal.dependency.Gson
import io.spine.internal.dependency.Guava
import io.spine.internal.dependency.Hamcrest
import io.spine.internal.dependency.J2ObjC
import io.spine.internal.dependency.JUnit
import io.spine.internal.dependency.Jackson
import io.spine.internal.dependency.JavaDiffUtils
import io.spine.internal.dependency.Kotest
import io.spine.internal.dependency.Kotlin
import io.spine.internal.dependency.KotlinX
import io.spine.internal.dependency.OpenTest4J
import io.spine.internal.dependency.Protobuf
import io.spine.internal.dependency.Slf4J
import io.spine.internal.dependency.Truth
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

    val spine = io.spine.internal.dependency.Spine
    val validation = io.spine.internal.dependency.Validation
    val protoData = io.spine.internal.dependency.ProtoData
    val logging = io.spine.internal.dependency.Spine.Logging

    configurations {
        all {
            exclude(group = "io.spine", module = "spine-logging-backend")

            resolutionStrategy {
                force(
                    io.spine.internal.dependency.Grpc.api,
                    spine.reflect,
                    spine.base,
                    spine.toolBase,
                    spine.server,
                    protoData.pluginLib,
                    protoData.lib,
                    logging.lib,
                    validation.runtime
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
