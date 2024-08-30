/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.JUnit
import io.spine.internal.dependency.Spine

// Apply ProtoData directly, without Spine's Model Compiler.
plugins {
    id("io.spine.protodata")
}

repositories {
    mavenLocal()
}

dependencies {
    // All the following libraries are required,
    // since they provide Proto files, which are used
    // as dependencies in Proto sources we process.
    implementation(Spine.CoreJava.server_1_9)
    protoData(project(":message-fields"))
    testImplementation(JUnit.runner)
}

protoData {
    plugins(
        "io.spine.chords.protodata.plugin.MessageFieldsPlugin"
    )
}

// Read `sourceModuleDir` from project properties.
// This causes the build to fail if this property is not set.
val sourceModuleDir = project.properties["sourceModuleDir"] as String

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
