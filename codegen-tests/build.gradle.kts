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

import io.spine.internal.dependency.Kotest
import io.spine.internal.dependency.Spine
import io.spine.internal.gradle.RunCodegenPlugins

dependencies {
    implementation(Spine.base_1_9)
    implementation(project(":codegen-runtime"))
    testImplementation(Kotest.runnerJUnit5)
}

configurations {
    all {
        resolutionStrategy {
            force(Spine.base_1_9)
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
    pluginsDir = "${rootDir}/codegen-workspace"
    pluginsVersion = version as String
    sourceModuleDir = "${rootDir}/codegen-tests"

    dependsOn(
        project(":codegen-runtime")
            .tasks.named("publishToMavenLocal")
    )
}

// Generate the code before the `compileKotlin` task.
tasks.named("compileKotlin") {
    dependsOn(
        runCodegenPlugins
    )
}
