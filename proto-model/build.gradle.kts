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

import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc
import io.onedam.dependency.JavaX
import io.onedam.dependency.Kotest
import io.onedam.dependency.Protobuf
import io.onedam.dependency.Spine
import io.onedam.gradle.RunCodegenPlugins

plugins {
    id("io.spine.tools.gradle.bootstrap")
    id("java-library")
    id("com.google.protobuf")
    `maven-publish`
}

spine {
    // Spine Model Compiler is enabled only for generating validation code for
    // error messages.
    assembleModel()

    enableJava()
}

dependencies {
    implementation(Spine.Base.lib)
    implementation(Spine.Chords.CodegenRuntime.lib)
    api(Spine.Money.lib)
    implementation(JavaX.annotations)
    testImplementation(Kotest.Runner.lib)
}

protobuf {
    protoc {
        artifact = Protobuf.Protoc.lib
    }
    generateProtoTasks {
        for (task in all()) {
            task.builtins {
                id("kotlin")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.name

            from(components["java"])
        }
    }
}

/**
 * The task below executes a separate Gradle build of the `codegen-plugins`
 * project. It is needed because the ProtoData plugin, that helps to generate
 * the Kotlin extensions for the Proto messages, requires the newer version
 * of Gradle.
 *
 * See the [RunCodegenPlugins] for details.
 */
val runCodegenPlugins = tasks.register<RunCodegenPlugins>("runCodegenPlugins") {
    pluginsDir = "${rootDir}/codegen/codegen-plugins"
    sourceModuleDir = "${rootDir}/proto-model"

    // Dependencies that are required to load the Proto files from.
    dependencies(
        // A list the external dependencies,
        // onto which the processed Proto sources depend.
        Spine.Money.lib
    )
}

// Run the code generation before `compileKotlin` task.
tasks.named("compileKotlin") {
    dependsOn(
        runCodegenPlugins
    )
}
