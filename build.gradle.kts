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
import io.spine.internal.dependency.Guava
import io.spine.internal.dependency.KotlinX
import io.spine.internal.dependency.Spine
import io.spine.internal.gradle.BuildCodegenPlugins
import io.spine.internal.gradle.publish.ChordsPublishing
import io.spine.internal.gradle.publish.PublishingRepos
import io.spine.internal.gradle.publish.spinePublishing
import io.spine.internal.gradle.report.license.LicenseReporter
import io.spine.internal.gradle.report.pom.PomGenerator
import io.spine.internal.gradle.standardToSpineSdk
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

buildscript {
    standardSpineSdkRepositories()

    plugins {
        id("io.spine.chords") version "1.9.20"
    }
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

spinePublishing {
    modules = productionModules
        .map { project -> project.name }.toSet()

    destinations = setOf(
        PublishingRepos.gitHub("Chords"),
        PublishingRepos.cloudArtifactRegistry
    )
    artifactPrefix = ChordsPublishing.artifactPrefix
}

val publishCodegenPluginsToMavenLocal = tasks.register<BuildCodegenPlugins>(
    "publishCodegenPluginsToMavenLocal"
) {
    directory = "${rootDir}/codegen/plugins"
    task("publishToMavenLocal")
    dependsOn(
        project(":runtime").tasks.named("publishToMavenLocal")
    )
}

val publishCodegenPlugins = tasks.register<BuildCodegenPlugins>(
    "publishCodegenPlugins"
) {
    directory = "${rootDir}/codegen/plugins"
    task("publish")
    dependsOn(
        project(":runtime").tasks.named("publishToMavenLocal")
    )
}

// The set of modules that require Chords code generation.
val modulesWithChordsCodegen = setOf("proto-values", "codegen-tests")

subprojects {
    apply {
        plugin("jvm-module")
    }
    apply<JavaPlugin>()

    if (modulesWithChordsCodegen.contains(name)) {
        // Apply codegen Gradle plugin to modules that require code generation.
        applyGradleCodegenPlugin()
    }
}

// Applies and configures `io.spine.chords` Gradle plugin.
//
fun Project.applyGradleCodegenPlugin() {
    apply {
        plugin(Spine.Chords.GradlePlugin.id)
    }
    // Publish `codegen-plugins` to `mavenLocal` repo before it can be applied.
    tasks.named("createCodegenWorkspace") {
        dependsOn(publishCodegenPluginsToMavenLocal)
    }
    // Configure the plugin with the current version of `codegen-plugins`.
    chordsGradlePlugin.codegenPluginsArtifact =
        Spine.Chords.CodegenPlugins.artifact(version.toString())
}

tasks.named("publishToMavenLocal") {
    dependsOn(publishCodegenPluginsToMavenLocal)
}

tasks.named("publish") {
    dependsOn(publishCodegenPlugins)
}

PomGenerator.applyTo(project)
LicenseReporter.mergeAllReports(project)
