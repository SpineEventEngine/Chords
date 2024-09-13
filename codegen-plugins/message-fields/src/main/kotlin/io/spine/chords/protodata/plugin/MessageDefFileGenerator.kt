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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.asClassName
import io.spine.chords.runtime.MessageDef
import io.spine.chords.runtime.MessageDef.Companion.MESSAGE_DEF_CLASS_SUFFIX
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageFieldValue
import io.spine.chords.runtime.MessageOneof
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.type.TypeSystem
import io.spine.string.camelCase

/**
 * Implementation of [FileFragmentGenerator] that combines several other
 * generators to create a separate Kotlin file with implementations of
 * [MessageDef], [MessageField], and [MessageOneof] for a Proto message.
 *
 * Also, the properties for the corresponding message class are
 * generated to provide static-like access to these implementations.
 * See [KClassPropertiesGenerator] for detail.
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

    /**
     * The list of [FileFragmentGenerator]s which are used to generate the file.
     */
    private val codeGenerators = listOf(
        MessageDefObjectGenerator(messageTypeName, fields, typeSystem),
        MessageFieldObjectGenerator(messageTypeName, fields, typeSystem),
        MessageOneofObjectGenerator(messageTypeName, fields, typeSystem),
        KClassPropertiesGenerator(messageTypeName, fields, typeSystem),
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
 * Returns a [ClassName] of [MessageField].
 */
internal val messageFieldClassName: ClassName
    get() = MessageField::class.asClassName()

/**
 * Returns a [ClassName] of [MessageOneof].
 */
internal val messageOneofClassName: ClassName
    get() = MessageOneof::class.asClassName()

/**
 * Returns a [ClassName] of [MessageFieldValue].
 */
internal val messageFieldValueTypeAlias: ClassName
    get() = MessageFieldValue::class.asClassName()

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
    else
        simpleName
