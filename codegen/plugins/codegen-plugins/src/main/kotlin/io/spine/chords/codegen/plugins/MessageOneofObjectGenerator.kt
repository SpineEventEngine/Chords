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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.spine.chords.runtime.MessageOneof
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.isPartOfOneof
import io.spine.protodata.java.javaCase
import io.spine.protodata.type.TypeSystem
import java.lang.System.lineSeparator

/**
 * Implementation of [FileFragmentGenerator] that generates implementation
 * of [MessageOneof] for the `oneof` fields of a Proto message.
 *
 *
 * @param messageTypeName The [TypeName] of the message to generate the code for.
 * @param fields The collection of [Field]s to generate the code for.
 * @param typeSystem The [TypeSystem] to read external Proto messages.
 */
internal class MessageOneofObjectGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    private val typeSystem: TypeSystem
) : FileFragmentGenerator {

    /**
     * Returns a fully qualified [ClassName] of the given [messageTypeName].
     */
    private val messageFullClassName = messageTypeName.fullClassName(typeSystem)

    /**
     * Generates implementations of [MessageOneof] for the `oneof` fields
     * of a Proto message.
     */
    override fun generateCode(fileBuilder: FileSpec.Builder) {
        fields.filter { field ->
            field.isPartOfOneof
        }.groupBy { oneofField ->
            oneofField.oneofName.value
        }.forEach { filedMap ->
            fileBuilder.addType(
                buildMessageOneofObject(filedMap.key, filedMap.value)
            )
        }
    }

    /**
     * Generates implementation of [MessageOneof] for the given `oneof` field.
     *
     * The generated code for some `oneof` filed looks like the following:
     * ```
     *     public object IpAddressValueOneof : MessageOneof<IpAddress> {
     *         private val fieldMap: Map<Int, MessageField<IpAddress, Any>> =
     *             mapOf(
     *                 1 to IpAddressDef.ipv4 as MessageField<IpAddress, Any>,
     *                 2 to IpAddressDef.ipv6 as MessageField<IpAddress, Any>
     *             )
     *
     *         public override val name: String = "value"
     *
     *         public override val fields: Iterable<MessageField<IpAddress, *>>
     *             = fieldMap.values
     *
     *         public override fun selectedField(message: IpAddress):
     *             MessageField<IpAddress, *>? = fieldMap[message.valueCase.number]
     *     }
     * ```
     */
    private fun buildMessageOneofObject(
        oneofName: String,
        oneofFields: List<Field>
    ): TypeSpec {
        val messageFieldType = messageFieldClassName.parameterizedBy(
            messageFullClassName,
            messageFieldValueTypeAlias
        )
        val fieldMapType = Map::class.asClassName().parameterizedBy(
            Int::class.asClassName(),
            messageFieldType
        )
        val stringType = String::class.asClassName()
        val fieldsReturnType = Collection::class.asClassName()
            .parameterizedBy(messageFieldType)
        val superInterface = messageOneofClassName
            .parameterizedBy(messageFullClassName)
        val propName = oneofName.propertyName
        val generatedClassName = messageTypeName
            .messageOneofClassName(oneofName)

        return TypeSpec.objectBuilder(generatedClassName)
            .addSuperinterface(superInterface)
            .addProperty(
                PropertySpec
                    .builder("fieldMap", fieldMapType, PRIVATE)
                    .addAnnotation(
                        buildSuppressAnnotation(
                            "UNCHECKED_CAST",
                            "RemoveRedundantQualifierName"
                        )
                    )
                    .initializer(
                        fieldMapInitializer(
                            oneofFields,
                            messageTypeName.messageDefClassName(),
                            messageFieldType
                        )
                    )
                    .build()
            )
            .addProperty(
                PropertySpec
                    .builder("name", stringType, PUBLIC, OVERRIDE)
                    .initializer("\"$oneofName\"")
                    .build()
            )
            .addProperty(
                PropertySpec
                    .builder("fields", fieldsReturnType, PUBLIC, OVERRIDE)
                    .initializer("fieldMap.values")
                    .build()
            )
            .addFunction(
                FunSpec.builder("selectedField")
                    .addModifiers(PUBLIC, OVERRIDE)
                    .returns(messageFieldType.copy(nullable = true))
                    .addParameter("message", messageFullClassName)
                    .addCode("return fieldMap[message.${propName}Case.number]")
                    .build()
            )
            .build()
    }
}

/**
 * Builds `@Suppress` annotation with given [warnings].
 */
@Suppress("SameParameterValue")
private fun buildSuppressAnnotation(vararg warnings: String) =
    AnnotationSpec.builder(Suppress::class.asClassName())
        .also { builder ->
            warnings.forEach { warning ->
                builder.addMember("%S", warning)
            }
        }
        .build()

/**
 * Generates initialization code for the `fieldMap` property
 * of the [MessageOneof] implementation.
 *
 * The generated code looks like the following:
 * ```
 * mapOf(
 *     1 to IpAddressDef.ipv4 as MessageField<IpAddressDef, Any>,
 *     2 to IpAddressDef.ipv6 as MessageField<IpAddressDef, Any>
 * )
 * ```
 */
private fun fieldMapInitializer(
    fields: Iterable<Field>,
    messageDefClassName: String,
    messageField: ParameterizedTypeName
): String {
    return fields.joinToString(
        ",${lineSeparator()}",
        "mapOf(${lineSeparator()}",
        ")"
    ) {
        "${it.number} to $messageDefClassName.${it.name.javaCase()} as $messageField"
    }
}
