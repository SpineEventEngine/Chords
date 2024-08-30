/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.dependency

// https://github.com/FasterXML/jackson/wiki/Jackson-Releases
@Suppress("unused", "ConstPropertyName")
object Jackson {
    const val version = "2.15.2"
    private const val databindVersion = "2.15.2"

    private const val coreGroup = "com.fasterxml.jackson.core"
    private const val dataformatGroup = "com.fasterxml.jackson.dataformat"
    private const val moduleGroup = "com.fasterxml.jackson.module"

    // https://github.com/FasterXML/jackson-core
    const val core = "$coreGroup:jackson-core:${version}"
    // https://github.com/FasterXML/jackson-databind
    const val databind = "$coreGroup:jackson-databind:${databindVersion}"
    // https://github.com/FasterXML/jackson-annotations
    const val annotations = "$coreGroup:jackson-annotations:${version}"

    // https://github.com/FasterXML/jackson-dataformat-xml/releases
    const val dataformatXml = "$dataformatGroup:jackson-dataformat-xml:${version}"
    // https://github.com/FasterXML/jackson-dataformats-text/releases
    const val dataformatYaml = "$dataformatGroup:jackson-dataformat-yaml:${version}"

    // https://github.com/FasterXML/jackson-module-kotlin/releases
    const val moduleKotlin = "$moduleGroup:jackson-module-kotlin:${version}"

    // https://github.com/FasterXML/jackson-bom
    const val bom = "com.fasterxml.jackson:jackson-bom:${version}"
}
