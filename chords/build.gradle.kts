/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Material3
import io.spine.internal.dependency.Kotest

plugins {
    id("org.jetbrains.compose") version "1.5.12"
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(Material3.Desktop.lib)
    testImplementation(Kotest.runnerJUnit5)
}
