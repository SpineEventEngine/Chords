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

import com.google.protobuf.BoolValue
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
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
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageFieldValue
import io.spine.chords.runtime.MessageOneof
import io.spine.protobuf.AnyPacker.unpack
import io.spine.protodata.ast.Field
import io.spine.protodata.ast.TypeName
import io.spine.protodata.ast.isPartOfOneof
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
        }.forEach { nameToFields ->
            fileBuilder.addType(
                buildMessageOneofObject(nameToFields.key, nameToFields.value)
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
     *                 1 to IpAddressDef.ipv4.safeCast<MessageField<IpAddressDef, Any>>(),
     *                 2 to IpAddressDef.ipv6.safeCast<MessageField<IpAddressDef, Any>>()
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
        val fieldType = MessageField::class.asClassName().parameterizedBy(
            messageFullClassName,
            MessageFieldValue::class.asClassName()
        )
        val generatedClassName = messageTypeName
            .messageOneofClassName(oneofName)
        val superInterface = MessageOneof::class.asClassName()
            .parameterizedBy(messageFullClassName)

        return TypeSpec.objectBuilder(generatedClassName)
            .addSuperinterface(superInterface)
            .addAnnotation(generatedAnnotation())
            .addKdoc(generateKDoc(oneofName))
            .addProperty(fieldMapProperty(oneofFields, fieldType))
            .addProperty(nameProperty(oneofName))
            .addProperty(requiredProperty(oneofName))
            .addProperty(fieldsProperty(fieldType))
            .addFunction(selectedFieldFunction(oneofName, fieldType))
            .build()
    }

    /**
     * Generates the `fieldMap` property.
     */
    private fun fieldMapProperty(
        oneofFields: List<Field>,
        fieldType: ParameterizedTypeName
    ): PropertySpec {
        val fieldMapType = Map::class.asClassName().parameterizedBy(
            Int::class.asClassName(),
            fieldType
        )
        return PropertySpec
            .builder("fieldMap", fieldMapType, PRIVATE)
            .initializer(
                fieldMapInitializer(
                    oneofFields,
                    messageTypeName.messageDefClassName(),
                    fieldType
                )
            ).build()
    }

    /**
     * Builds the `required` property of [MessageOneof] implementation.
     */
    private fun requiredProperty(oneofName: String) =
        PropertySpec
            .builder("required", Boolean::class.asClassName(), PUBLIC, OVERRIDE)
            .initializer("${isOneofRequired(oneofName)}")
            .build()

    /**
     * Returns a value of the `is_required` option if it is applied to the
     * oneof group with the given [oneofName].
     *
     * Returns `false` if the option `is_required` is not set.
     */
    private fun isOneofRequired(oneofName: String) =
        typeSystem.findMessage(messageTypeName)!!
            .first.oneofGroupList.find {
                it.name.value == oneofName
            }!!.optionList.any { option ->
                option.name == "is_required" &&
                        unpack(option.value, BoolValue::class.java).value
            }

    /**
     * Generates the `name` property.
     */
    private fun nameProperty(oneofName: String) =
        PropertySpec
            .builder("name", String::class.asClassName(), PUBLIC, OVERRIDE)
            .initializer(CodeBlock.of("%S", oneofName))
            .build()

    /**
     * Generates the `fields` property.
     */
    private fun fieldsProperty(fieldType: ParameterizedTypeName): PropertySpec {
        val fieldsReturnType = Collection::class.asClassName()
            .parameterizedBy(fieldType)
        return PropertySpec
            .builder("fields", fieldsReturnType, PUBLIC, OVERRIDE)
            .initializer("fieldMap.values")
            .build()
    }

    /**
     * Generates the `selectedField` function.
     */
    private fun selectedFieldFunction(
        oneofName: String,
        messageFieldType: ParameterizedTypeName
    ) = FunSpec.builder("selectedField")
        .addModifiers(PUBLIC, OVERRIDE)
        .returns(messageFieldType.copy(nullable = true))
        .addParameter("message", messageFullClassName)
        .addCode("return fieldMap[message.${oneofName.propertyName}Case.number]")
        .build()

    /**
     * Builds the KDoc section for the generated implementation of [MessageOneof].
     */
    private fun generateKDoc(oneofName: String) = CodeBlock.of(
        "A [%T] implementation that allows access to the `%L` oneof field " +
                "of the [%T] message at runtime.",
        MessageOneof::class.asClassName(),
        oneofName,
        messageTypeName.fullClassName(typeSystem)
    )
}

/**
 * Generates initialization code for the `fieldMap` property
 * of the [MessageOneof] implementation.
 *
 * The generated code looks like the following:
 * ```
 * mapOf(
 *     1 to IpAddressDef.ipv4.safeCast<MessageField<IpAddressDef, Any>>(),
 *     2 to IpAddressDef.ipv6.safeCast<MessageField<IpAddressDef, Any>>()
 * )
 * ```
 */
@Suppress("SpreadOperator")
private fun fieldMapInitializer(
    fields: List<Field>,
    messageDefClassName: String,
    messageField: ParameterizedTypeName
) = CodeBlock.of(
    fields.joinToString(
        ",${lineSeparator()}",
        "mapOf(${lineSeparator()}",
        ")"
    ) {
        "${it.number} to $messageDefClassName.${it.name.javaCase()}.safeCast<%T>()"
    }, *fields.map { messageField }.toTypedArray()
)
