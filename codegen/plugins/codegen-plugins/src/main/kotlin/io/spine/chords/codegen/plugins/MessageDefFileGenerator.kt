/*
 * Copyright 2025, TeamDev. All rights reserved.
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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.asClassName
import io.spine.chords.codegen.plugins.MessageDefFileGenerator.Companion.GENERATED_ANNOTATION_TEXT
import io.spine.chords.runtime.MessageDef
import io.spine.chords.runtime.MessageDef.Companion.MESSAGE_DEF_CLASS_SUFFIX
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageOneof
import io.spine.protodata.ast.Field
import io.spine.protodata.ast.TypeName
import io.spine.protodata.type.TypeSystem
import io.spine.string.camelCase
import javax.annotation.Generated

/**
 * Implementation of [FileFragmentGenerator] that combines several other
 * generators to create a separate Kotlin file with implementations of
 * [MessageDef], [MessageField], and [MessageOneof] for a Proto message.
 *
 * @param messageTypeName The [TypeName] of the message to generate the code for.
 * @param fields The collection of [Field]s to generate the code for.
 * @param typeSystem The [TypeSystem] to read external Proto messages.
 */
internal class MessageDefFileGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    private val typeSystem: TypeSystem
) : FileFragmentGenerator {

    companion object {
        const val GENERATED_ANNOTATION_TEXT = "by Spine Chords codegen plugins"
    }

    /**
     * The list of [FileFragmentGenerator]s which are used to generate the file.
     */
    private val codeGenerators = listOf(
        MessageDefObjectGenerator(messageTypeName, fields, typeSystem),
        MessageFieldObjectGenerator(messageTypeName, fields, typeSystem),
        MessageOneofObjectGenerator(messageTypeName, fields, typeSystem),
        BuilderExtensionGenerator(messageTypeName, typeSystem)
    )

    /**
     * Uses [codeGenerators] to build the content of the generated file.
     */
    override fun generateCode(fileBuilder: FileSpec.Builder) {
        codeGenerators.forEach { generator ->
            generator.generateCode(fileBuilder)
        }
    }
}

/**
 * Converts a Proto field name to a "property" name,
 * e.g. "domain_name" -> "domainName".
 */
internal val String.propertyName
    get() = camelCase().replaceFirstChar { it.lowercase() }

/**
 * Generates a simple class name for the implementation of [MessageField].
 */
internal fun TypeName.messageFieldClassName(fieldName: String): String {
    return generateClassName(fieldName, "Field")
}

/**
 * Generates a simple class name for the implementation of [MessageOneof].
 */
internal fun TypeName.messageOneofClassName(fieldName: String): String {
    return generateClassName(fieldName, "Oneof")
}

/**
 * Generates a simple class name for the [TypeName].
 */
internal fun TypeName.messageDefClassName(): String {
    return generateClassName("", MESSAGE_DEF_CLASS_SUFFIX)
}

/**
 * Generates a simple class name for the [fieldName]
 * and [classNameSuffix] provided taking into account nesting types.
 */
internal fun TypeName.generateClassName(
    fieldName: String,
    classNameSuffix: String
): String {
    return nestingTypeNameList.joinToString(
        "",
        "",
        "${simpleName}${fieldName.camelCase()}$classNameSuffix"
    )
}

/**
 * Returns simple class name for the [TypeName]
 * taking into account nesting types.
 */
internal val TypeName.simpleClassName: String
    get() = if (nestingTypeNameList.isNotEmpty())
        nestingTypeNameList.joinToString(
            ".",
            "",
            ".$simpleName"
        )
    else simpleName

/**
 * Builds `@Generated` annotation with the corresponding notes.
 */
internal fun generatedAnnotation() =
    buildAnnotation(
        Generated::class.asClassName(),
        GENERATED_ANNOTATION_TEXT
    )

/**
 * Builds [AnnotationSpec] with given [parameters].
 */
internal fun buildAnnotation(
    annotationClassName: ClassName,
    vararg parameters: String
) = AnnotationSpec.builder(annotationClassName).also { builder ->
    parameters.forEach { parameter ->
        builder.addMember("%S", parameter)
    }
}.build()
