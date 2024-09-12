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
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import io.spine.chords.protodata.plugin.MessageDefFileGenerator.Companion.CLASS_NAME_SUFFIX
import io.spine.chords.runtime.MessageDef
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.isPartOfOneof
import io.spine.protodata.type.TypeSystem
import java.lang.System.lineSeparator

/**
 * Generates implementation of [MessageDef] for a Proto message.
 *
 * The generated code looks like the following:
 * ```
 * public object IpAddressDef : MessageDef<IpAddress> {
 *
 *     public val ipv4: IpAddressIpv4Field = IpAddressIpv4Field
 *
 *     public val ipv6: IpAddressIpv6Field = IpAddressIpv6Field
 *
 *     public val `value`: IpAddressValueOneof = IpAddressValueOneof
 *
 *     public override val fields: Collection<MessageField<IpAddress, out Any>>
 *         = listOf(ipv4, ipv6)
 *
 *     public override val oneofs: Collection<MessageOneof<IpAddress>>
 *         = listOf(value)
 * }
 * ```
 *
 * @param messageTypeName a [TypeName] of the message to generate the code for.
 * @param fields a collection of [Field]s to generate the code for.
 * @param typeSystem a [TypeSystem] to read external Proto messages.
 */
internal class MessageDefObjectGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    private val typeSystem: TypeSystem
) {

    private val javaPackage = messageTypeName.javaPackage(typeSystem)

    private val messageFullClassName = ClassName(
        javaPackage,
        messageTypeName.simpleClassName
    )

    /**
     * Collection of names of [fields].
     */
    private val fieldNames = fields.map { it.name.value }

    /**
     * Contains the fields which are parts of some `oneof` grouped by its name.
     */
    private val oneofMap = fields.filter { field ->
        field.isPartOfOneof
    }.groupBy { oneofField ->
        oneofField.oneofName.value
    }

    /**
     * Generates implementation of [MessageDef] for a Proto message.
     *
     * The generated code looks like the following:
     * ```
     * public object IpAddressDef : MessageDef<IpAddress> {
     *
     *     public val ipv4: IpAddressIpv4Field = IpAddressIpv4Field
     *
     *     public val ipv6: IpAddressIpv6Field = IpAddressIpv6Field
     *
     *     public val `value`: IpAddressValueOneof = IpAddressValueOneof
     *
     *     public override val fields: Collection<MessageField<IpAddress, out Any>>
     *         = listOf(ipv4, ipv6)
     *
     *     public override val oneofs: Collection<MessageOneof<IpAddress>>
     *         = listOf(value)
     * }
     * ```
     */
    internal fun generateCode(fileBuilder: FileSpec.Builder) {
        fileBuilder.addType(
            TypeSpec.objectBuilder(
                messageTypeName.generateClassName(CLASS_NAME_SUFFIX)
            ).addSuperinterface(
                messageDefClassName.parameterizedBy(messageFullClassName)
            ).also { builder ->
                generateFieldProperties(builder)
                generateOneofProperties(builder)
                generateFieldsProperty(builder)
                generateOneofsProperty(builder)
            }.build()
        )
    }

    private fun generateFieldsProperty(objectBuilder: TypeSpec.Builder) {
        val propType = Collection::class.asClassName().parameterizedBy(
            messageFieldClassName.parameterizedBy(
                messageFullClassName,
                WildcardTypeName.producerOf(messageFieldValueTypeAlias)
            )
        )
        objectBuilder.addProperty(
            PropertySpec.builder("fields", propType, PUBLIC, OVERRIDE)
                .initializer(fieldListInitializer(fieldNames))
                .build()
        )
    }

    private fun generateOneofsProperty(objectBuilder: TypeSpec.Builder) {
        val propType = Collection::class.asClassName().parameterizedBy(
            messageOneofClassName.parameterizedBy(messageFullClassName)
        )
        objectBuilder.addProperty(
            PropertySpec.builder("oneofs", propType, PUBLIC, OVERRIDE)
                .initializer(fieldListInitializer(oneofMap.keys))
                .build()
        )
    }

    private fun generateFieldProperties(objectBuilder: TypeSpec.Builder) {
        fieldNames.forEach { fieldName ->
            objectBuilder.addProperty(
                generateFieldProperty(
                    fieldName,
                    messageTypeName.messageFieldClassName(fieldName)
                )
            )
        }
    }

    private fun generateOneofProperties(objectBuilder: TypeSpec.Builder) {
        oneofMap.keys.forEach { fieldName ->
            objectBuilder.addProperty(
                generateFieldProperty(
                    fieldName,
                    messageTypeName.messageOneofClassName(fieldName)
                )
            )
        }
    }

    private fun generateFieldProperty(
        fieldName: String,
        simpleClassName: String
    ): PropertySpec {
        return PropertySpec
            .builder(
                fieldName.propertyName,
                ClassName(javaPackage, simpleClassName),
                PUBLIC
            )
            .initializer(simpleClassName)
            .build()
    }
}

/**
 * Generates initialization code for the `fields` property
 * of the `MessageDef` implementation.
 */
private fun fieldListInitializer(
    fieldNames: Iterable<String>
): String {
    return fieldNames.joinToString(
        ",${lineSeparator()}",
        "listOf(${lineSeparator()}",
        ")"
    ) {
        it.propertyName
    }
}
