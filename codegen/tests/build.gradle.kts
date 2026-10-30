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

import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc
import io.spine.chords.gradle.ApplyCodegenPlugins
import io.spine.internal.dependency.Kotest
import io.spine.internal.dependency.Protobuf
import io.spine.internal.dependency.Spine

plugins {
    id("io.spine.tools.gradle.bootstrap")
    id("com.google.protobuf")
}

apply<JavaPlugin>()

spine {
    // Spine Model Compiler is enabled only for generating validation code for
    // error messages.
    assembleModel()
    enableJava()
}

protobuf {
    protoc {
        artifact = Protobuf.compiler
    }
    generateProtoTasks {
        for (task in all()) {
            task.builtins {
                id("kotlin")
            }
        }
    }
}

dependencies {
    implementation(Spine.base_1_9)
    implementation(Spine.CoreJvm.core)
    implementation(project(":runtime"))
    testImplementation(Kotest.runnerJUnit5)
}

/**
 * Protobuf dependencies declared here apply to generation and its export verification.
 */
val codegenProtoDependencies = emptySet<String>()

chordsGradlePlugin {
    protoDependencies(*codegenProtoDependencies.toTypedArray())
}

/**
 * Verifies that an up-to-date compiler result restores deleted consumer definitions.
 */
val verifyCodegenExport by tasks.registering(ApplyCodegenPlugins::class) {
    val generation = tasks.named<ApplyCodegenPlugins>("applyCodegenPlugins").get()
    dependsOn(generation)
    workspaceDir = generation.workspaceDir
    sourceModuleDir = generation.sourceModuleDir
    codegenPluginsArtifact = generation.codegenPluginsArtifact
    includeGradleProperties = generation.includeGradleProperties.toMutableSet()
    dependencies(codegenProtoDependencies)
    // Root `clean` also reaches nested builds; preserve the compiler output for this check.
    task("build", "--exclude-task=clean")
    doFirst {
        delete(fileTree("generated") {
            include("**/*Def.kt")
        })
    }
}

tasks.matching { it.name == "compileKotlin" || it.name == "compileTestKotlin" }.configureEach {
    dependsOn(verifyCodegenExport)
}
