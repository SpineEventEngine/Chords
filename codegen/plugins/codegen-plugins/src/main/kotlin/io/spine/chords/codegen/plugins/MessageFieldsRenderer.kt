/*
 * Copyright 2024, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.chords.codegen.plugins

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import io.spine.chords.runtime.MessageDef.Companion.MESSAGE_DEF_CLASS_SUFFIX
import io.spine.protodata.ast.Field
import io.spine.protodata.ast.TypeName
import io.spine.protodata.java.javaPackage
import io.spine.protodata.render.Renderer
import io.spine.protodata.render.SourceFileSet
import io.spine.protodata.type.TypeSystem
import io.spine.string.Indent
import io.spine.tools.code.Kotlin
import java.nio.file.Path

/**
 * ProtoData [Renderer] that generates `MessageField` and `MessageOneof`
 * implementations for the fields of Proto messages.
 *
 * The renderer prepares all the required data for code generation
 * and runs [MessageDefFileGenerator] to generate Kotlin files.
 */
public class MessageFieldsRenderer : Renderer<Kotlin>(Kotlin.lang()) {

    private companion object {
        private const val KOTLIN_SOURCE_ROOT = "kotlin"
        private const val REJECTIONS_PROTO_FILE_NAME = "rejections.proto"
    }

    /**
     * Gathers available [FieldMetadata]s for Proto messages
     * and performs the code generation.
     */
    override fun render(sources: SourceFileSet) {
        // Generate code for Kotlin output root only.
        if (!sources.outputRoot.endsWith(KOTLIN_SOURCE_ROOT)) {
            return
        }
        checkNotNull(typeSystem) {
            "`TypeSystem` is not initialized."
        }

        select(FieldMetadata::class.java).all()
            .filter {
                // Exclude fields that are declared in rejections.
                !it.id.file.path.endsWith(REJECTIONS_PROTO_FILE_NAME)
            }.map {
                it.id.typeName
            }.forEach { typeName ->
                val messageToHeader = typeSystem!!.findMessage(typeName)
                checkNotNull(messageToHeader) {
                    "Message not found for type `$typeName`."
                }
                messageToHeader.first.fieldList.let { fields ->
                    sources.createFile(
                        typeName.generateFilePath(),
                        typeName.generateFileContent(fields)
                    )
                }
            }
    }

    /**
     * Runs [MessageDefFileGenerator] to generate a content of the file
     * for the [fields] provided.
     */
    private fun TypeName.generateFileContent(
        fields: Iterable<Field>
    ) = FileSpec.builder(fullClassName(typeSystem!!))
        .indent(Indent.defaultJavaIndent.toString())
        .also { fileBuilder ->
            MessageDefFileGenerator(this, fields, typeSystem!!)
                .generateCode(fileBuilder)
        }
        .build()
        .toString()

    /**
     * Returns a [Path] to the generated file for the given [TypeName].
     */
    private fun TypeName.generateFilePath(): Path {
        return Path.of(
            javaPackage(typeSystem!!).replace('.', '/'),
            generateFileName()
        )
    }

    /**
     * Returns a name of the generated file for the [TypeName].
     */
    private fun TypeName.generateFileName(): String {
        return nestingTypeNameList.joinToString(
            "", "", "${simpleName}$MESSAGE_DEF_CLASS_SUFFIX.kt"
        )
    }
}

/**
 * Returns a Java package for the [TypeName].
 */
internal fun TypeName.javaPackage(typeSystem: TypeSystem): String {
    val messageToHeader = typeSystem.findMessage(this)
    checkNotNull(messageToHeader) {
        "Cannot determine file header for TypeName `$this`"
    }
    return messageToHeader.second.javaPackage()
}

/**
 * Returns a fully qualified [ClassName] for the [TypeName].
 */
internal fun TypeName.fullClassName(typeSystem: TypeSystem): ClassName {
    return ClassName(javaPackage(typeSystem), simpleClassName)
}
