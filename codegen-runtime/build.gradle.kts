/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.onedam.dependency.Chords
import io.onedam.dependency.Protobuf
import io.onedam.dependency.Spine

plugins {
    `kotlin-settings`
    `maven-publish`
}

dependencies {
    implementation(Protobuf.Kotlin.lib)
    implementation(Spine.Base.lib)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.name
            groupId = Chords.group
            version = Chords.version

            from(components["java"])
        }
    }
}
