package io.spine.chords.protodata.plugin

import com.squareup.kotlinpoet.FileSpec

internal interface CodeGenerator {

    fun generateCode(fileBuilder: FileSpec.Builder)
}
