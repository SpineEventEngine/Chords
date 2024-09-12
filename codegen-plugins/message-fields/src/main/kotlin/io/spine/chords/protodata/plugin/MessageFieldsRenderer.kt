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

package io.spine.chords.protodata.plugin

import io.spine.protodata.renderer.Renderer
import io.spine.protodata.renderer.SourceFileSet
import io.spine.tools.code.Kotlin

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
     * Gathers available [FieldMetadata]s for command messages
     * and performs code generation.
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
                    MessageDefFileGenerator(typeName, fields, typeSystem!!)
                        .let { generator ->
                            sources.createFile(
                                generator.filePath(),
                                generator.fileContent()
                            )
                        }
                }
            }
    }
}
