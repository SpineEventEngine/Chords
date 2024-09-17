/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

plugins {
    id("io.spine.tools.gradle.bootstrap")
    id("org.jetbrains.compose") version "1.5.12"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    spineSnapshots()
}

dependencies {
    implementation(Kotlin.Reflect.lib)
    implementation(compose.desktop.currentOs)
    implementation(Material3.Desktop.lib)
    implementation(Spine.Server.lib)
    implementation(Projects.Users.lib)
    implementation(Spine.Chords.core)
    implementation(project(":chords-proto"))
    implementation(project(":chords-proto-ext"))
    implementation(Spine.Chords.CodegenRuntime.lib)
}
