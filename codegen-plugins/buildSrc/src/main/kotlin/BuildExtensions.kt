/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.spine.internal.gradle.standardToSpineSdk
import org.gradle.kotlin.dsl.ScriptHandlerScope

/**
 * Applies [standard][standardToSpineSdk] repositories to this `buildscript`.
 */
fun ScriptHandlerScope.standardSpineSdkRepositories() {
    repositories.standardToSpineSdk()
}
