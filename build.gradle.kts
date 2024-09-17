import io.spine.internal.dependency.Dokka
import io.spine.internal.dependency.KotlinX
import io.spine.internal.gradle.publish.ChordsPublishing
import io.spine.internal.gradle.publish.PublishingRepos
import io.spine.internal.gradle.publish.spinePublishing
import io.spine.internal.gradle.report.license.LicenseReporter
import io.spine.internal.gradle.report.pom.PomGenerator
import io.spine.internal.gradle.standardToSpineSdk

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

import io.spine.internal.dependency.Dokka
import io.spine.internal.dependency.KotlinX
import io.spine.internal.gradle.RunBuild
import io.spine.internal.gradle.RunGradle
import io.spine.internal.gradle.publish.ChordsPublishing
import io.spine.internal.gradle.publish.PublishingRepos
import io.spine.internal.gradle.publish.spinePublishing
import io.spine.internal.gradle.report.license.LicenseReporter
import io.spine.internal.gradle.report.pom.PomGenerator
import io.spine.internal.gradle.standardToSpineSdk

buildscript {
    standardSpineSdkRepositories()
}

plugins {
    idea
    jacoco
    `project-report`
}

allprojects {
    apply(plugin = Dokka.GradlePlugin.id)
    apply(from = "$rootDir/version.gradle.kts")
    group = "io.spine.chords"
    version = extra["chordsVersion"]!!

    repositories.standardToSpineSdk()

    configurations.all {
        resolutionStrategy {
            force(
                KotlinX.Coroutines.core,
                KotlinX.Coroutines.bom,
                KotlinX.Coroutines.jdk8,
                KotlinX.Coroutines.test,
                KotlinX.Coroutines.testJvm,
                KotlinX.Coroutines.debug,
                KotlinX.AtomicFu.lib,
                Guava.lib
            )
        }
    }

    // See https://youtrack.jetbrains.com/issue/CMP-6640.
    configurations.configureEach {
        attributes.attribute(
            Attribute.of(KotlinPlatformType.attribute.name, KotlinPlatformType::class.java),
            KotlinPlatformType.jvm
        )
    }
}

subprojects {
    apply {
        plugin("jvm-module")
    }
    apply<JavaPlugin>()
}

spinePublishing {
    modules = productionModules
        .map { project -> project.name }
        .toSet()

    destinations = setOf(
        PublishingRepos.gitHub("Chords"),
        PublishingRepos.cloudArtifactRegistry
    )
    artifactPrefix = ChordsPublishing.artifactPrefix
}

PomGenerator.applyTo(project)
LicenseReporter.mergeAllReports(project)

/**
 * The task below executes a separate Gradle build of the `codegen-plugins`
 * project. It is needed because the ProtoData plugin, that helps to generate
 * the Kotlin extensions for the Proto messages, requires the newer version
 * of Gradle.
 *
 * Avery module that requires the code generation should add dependency on this task.
 *
 * See the [RunGradle] for detail.
 */
val buildCodegenPlugins = tasks.register<RunBuild>("buildCodegenPlugins") {
    directory = "${rootDir}/codegen-plugins"

    dependsOn(
        project(":codegen-runtime")
            .tasks.named("publishToMavenLocal")
    )
}
