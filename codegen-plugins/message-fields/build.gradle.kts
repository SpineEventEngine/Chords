/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.dependency.Chords
import io.spine.internal.dependency.KotlinPoet
import io.spine.internal.dependency.ProtoData

repositories {
    mavenLocal()
}

plugins {
    `kotlin-dsl`
}

dependencies {
    // To use ProtoData API in code generation plugin.
    api(ProtoData.backend)

    // To generate Kotlin sources.
    api(KotlinPoet.lib)

    // To access `MessageField` and `MessageOneof` interfaces.
    implementation(Chords.CodegenRuntime.lib)
}

modelCompiler {
    java {
        codegen {
            validation().enabled.set(false)
        }
    }
}
