/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

import io.gitlab.arturbosch.detekt.Detekt

/**
 * This script-plugin sets up Kotlin code analyzing with Detekt.
 *
 * After applying, Detekt is configured to use `${rootDir}/config/quality/detekt-config.yml` file.
 * Projects can append their own config files to override some parts of the default one or drop
 * it at all in a favor of their own one.
 *
 * An example of appending a custom config file to the default one:
 *
 * ```
 * detekt {
 *     config.from("config/detekt-custom-config.yml")
 * }
 * ```
 *
 * To totally substitute it, just overwrite the corresponding property:
 *
 * ```
 * detekt {
 *     config = files("config/detekt-custom-config.yml")
 * }
 * ```
 *
 * Also, it's possible to suppress Detekt findings using [baseline](https://detekt.dev/docs/introduction/baseline/)
 * file instead of suppressions in source code.
 *
 * An example of passing a baseline file:
 *
 * ```
 * detekt {
 *     baseline = file("config/detekt-baseline.yml")
 * }
 * ```
 */
@Suppress("unused")
private val about = ""

plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    buildUponDefaultConfig = true
    config.from(files("${rootDir}/../quality/detekt-config.yml"))
}

tasks {
    withType<Detekt>().configureEach {
        reports {
            html.required.set(true) // Only HTML report is generated.
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
}
