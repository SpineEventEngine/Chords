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

import io.spine.dependency.lib.KotlinPoet
import io.spine.dependency.local.Chords
import io.spine.dependency.local.Compiler

dependencies {
    // To use Spine Compiler API in code generation plugin.
    implementation(Compiler.api)
    // To use `PrimitiveType` extensions.
    implementation(Compiler.jvm)
    // To generate Kotlin sources.
    implementation(KotlinPoet.lib)
    // To use `spine-chords-runtime` published to Maven local.
    implementation(Chords.Runtime.lib(version.toString()))
}

/**
 * Path to `codegen-workspace` folder in the module resources.
 *
 * This folder will be packaged into the resulting jar file and then used by
 * the Chords Gradle plugin to create a placeholder module in which to perform
 * code generation.
 */
val workspaceDir = "src/main/resources/codegen-workspace"

/**
 * Copies `buildSrc` and Gradle wrapper files to `workspaceDir`.
 */
val copyWorkspaceResources = tasks.register("copyWorkspaceResources") {
    doLast {
        copy {
            from("$rootDir") {
                include(
                    "buildSrc/**",
                    "gradle/**",
                    "gradlew",
                    "gradlew.bat",
                    "gradle.properties"
                )
                exclude(
                    "buildSrc/.gradle",
                    "buildSrc/.kotlin",
                    "buildSrc/build",
                    "buildSrc/aus.weis"
                )
            }
            into(workspaceDir)
        }
    }
}

tasks.matching { it.name == "processResources" || it.name == "sourcesJar" }.configureEach {
    dependsOn(copyWorkspaceResources)
}

tasks.named("compileKotlin") {
    dependsOn("kspKotlin")
}

tasks.named("compileTestKotlin") {
    dependsOn("kspTestKotlin")
}

tasks.matching { it.name == "kspKotlin" }.configureEach {
    dependsOn("launchSpineCompiler")
}

tasks.matching { it.name == "kspTestKotlin" }.configureEach {
    dependsOn("launchTestSpineCompiler")
}

/**
 * Removes copied `buildSrc` and Gradle wrapper files.
 */
val cleanWorkspaceResources = tasks.register("cleanWorkspaceResources") {
    doLast {
        delete("$workspaceDir/buildSrc")
        delete("$workspaceDir/gradle")
        delete("$workspaceDir/gradlew")
        delete("$workspaceDir/gradlew.bat")
        delete("$workspaceDir/gradle.properties")
    }
}

copyWorkspaceResources.configure {
    dependsOn(cleanWorkspaceResources)
}

tasks.named("clean") {
    dependsOn(cleanWorkspaceResources)
}
