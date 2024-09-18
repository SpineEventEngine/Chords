package io.spine.chords.codegen.plugins

import com.squareup.kotlinpoet.FileSpec

/**
 * An abstract base for code generators designed
 * to add some code fragments to the generated file.
 */
internal interface FileFragmentGenerator {

    /**
     * Adds a piece of code to the given [FileSpec.Builder] file builder.
     */
    fun generateCode(fileBuilder: FileSpec.Builder)
}
