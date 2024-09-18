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

import io.spine.internal.dependency.Chords
import io.spine.internal.dependency.Kotest
import io.spine.internal.dependency.Spine
import io.spine.internal.gradle.standardToSpineSdk

// Apply ProtoData directly, without Spine's Model Compiler.
plugins {
    id("io.spine.protodata")
}

repositories {
    standardToSpineSdk()
    mavenLocal()
}

dependencies {
    // All the following libraries are required,
    // since they provide Proto files, which are used
    // as dependencies in Proto sources we process.
    implementation(Spine.CoreJava.server_1_9)
    protoData(Chords.CodegenPlugins.lib(project.properties["codegenPluginsVersion"] as String))
    testImplementation(Kotest.runnerJUnit5)
}

protoData {
    plugins(
        "io.spine.chords.codegen.plugins.MessageFieldsPlugin"
    )
}

// Read `sourceModuleDir` from project properties.
// Disabled now: This causes the build to fail if this property is not set.
//val sourceModuleDir = project.properties["sourceModuleDir"] as String
val sourceModuleDir = if (project.hasProperty("sourceModuleDir"))
    project.properties["sourceModuleDir"] as String
else {
    logger.warn(
        """
        WARNING! Property `sourceModuleDir` is not set.
        There are no sources to generate the code for.
        """.trimIndent()
    )
    ""
}

// Add passed dependencies to the module classpath.
if (project.hasProperty("dependencyItems")) {
    val dependencyItems = project.properties["dependencyItems"] as String
    dependencyItems
        .split(";")
        .forEach {
            project.dependencies.add("implementation", it)
        }
}

// Disable "compileKotlin" and "compileTestKotlin" tasks because Kotlin
// sources are not compilable in this module due to dependency on
// `ValidatingBuilder` from Spine 1.9.x.
tasks.named("compileKotlin") {
    enabled = false
}

tasks.named("compileTestKotlin") {
    enabled = false
}

// Copy Proto sources from the source module.
val copyProtoSourcesTask = tasks.register("copyProtoSources") {
    doLast {
        copy {
            from("${sourceModuleDir}/src/main/proto")
            into("src/main/proto")
        }
    }
    dependsOn(deleteCopiedSourcesTask)
}

// Copy Proto sources before the `generateProto` task.
tasks.named("generateProto") {
    dependsOn(copyProtoSourcesTask)
}

// Copy test Proto sources from the source module.
val copyTestProtoSourcesTask = tasks.register("copyTestProtoSources") {
    doLast {
        copy {
            from("${sourceModuleDir}/src/test/proto")
            into("src/test/proto")
        }
    }
    dependsOn(deleteCopiedTestSourcesTask)
}

// Copy test Proto sources before the `generateTestProto` task.
tasks.named("generateTestProto") {
    dependsOn(copyTestProtoSourcesTask)
}

// Copy generated sources back to the source module.
tasks.named("launchProtoData") {
    doLast {
        copy {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            from("generated/main/kotlin")
            into("${sourceModuleDir}/generated/main/kotlin")
        }
    }
}

// Copy generated test sources back to the source module.
tasks.named("launchTestProtoData") {
    doLast {
        copy {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            from("generated/test/kotlin")
            into("${sourceModuleDir}/generated/test/kotlin")
        }
    }
}

// Delete copied Proto sources.
val deleteCopiedSourcesTask = tasks.register("deleteCopiedSources") {
    doLast {
        delete("src/main/proto")
    }
}

// Delete copied Proto sources.
val deleteCopiedTestSourcesTask = tasks.register("deleteCopiedTestSources") {
    doLast {
        delete("src/test/proto")
    }
}

// Delete copied Proto sources on `clean`.
tasks.named("clean") {
    dependsOn(
        deleteCopiedSourcesTask,
        deleteCopiedTestSourcesTask
    )
}
