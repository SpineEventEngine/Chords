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
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.spine.chords.runtime.MessageDef
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageOneof
import io.spine.protodata.ast.Field
import io.spine.protodata.ast.TypeName
import io.spine.protodata.ast.isPartOfOneof
import io.spine.protodata.type.TypeSystem
import java.lang.System.lineSeparator

/**
 * Implementation of [FileFragmentGenerator] that generates implementation
 * of [MessageDef] for a Proto message.
 *
 * @param messageTypeName The [TypeName] of the message to generate the code for.
 * @param fields The collection of [Field]s to generate the code for.
 * @param typeSystem The [TypeSystem] to read external Proto messages.
 */
internal class MessageDefObjectGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    private val typeSystem: TypeSystem
) : FileFragmentGenerator {

    /**
     * Collection of names of the given [fields].
     */
    private val fieldNames = fields.map { it.name.value }

    /**
     * Collection of names of the `oneof` [fields].
     */
    private val oneofNames = fields.filter { field ->
        field.isPartOfOneof
    }.groupBy { oneofField ->
        oneofField.oneofName.value
    }.map { it.key }

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
     *     public override val fields: Collection<MessageField<IpAddress, Any>>
     *         = listOf(
     *             ipv4 as MessageField<IpAddress, Any>,
     *             ipv6 as MessageField<IpAddress, Any>
     *         )
     *
     *     public override val oneofs: Collection<MessageOneof<IpAddress>>
     *         = listOf(value)
     * }
     * ```
     */
    override fun generateCode(fileBuilder: FileSpec.Builder) {
        fileBuilder.addType(
            TypeSpec.objectBuilder(
                messageTypeName.messageDefClassName()
            ).addSuperinterface(
                MessageDef::class.asClassName().parameterizedBy(
                    messageTypeName.fullClassName(typeSystem)
                )
            ).also { objectBuilder ->
                fieldNames.forEach { fieldName ->
                    objectBuilder.addProperty(
                        buildFieldInstanceProperty(
                            fieldName,
                            messageTypeName.messageFieldClassName(fieldName)
                        )
                    )
                }
                oneofNames.forEach { fieldName ->
                    objectBuilder.addProperty(
                        buildFieldInstanceProperty(
                            fieldName,
                            messageTypeName.messageOneofClassName(fieldName)
                        )
                    )
                }
                objectBuilder.addProperty(
                    buildFieldsProperty()
                )
                objectBuilder.addProperty(
                    buildOneofsProperty()
                )
            }.build()
        )
    }

    /**
     * Builds the `fields` property of [MessageDef] implementation.
     */
    private fun buildFieldsProperty(): PropertySpec {
        val messageDefClassName = messageFieldClassName.parameterizedBy(
            messageTypeName.fullClassName(typeSystem),
            messageFieldValueTypeAlias
        )
        val propType = Collection::class.asClassName().parameterizedBy(
            messageDefClassName
        )
        return PropertySpec.builder("fields", propType, PUBLIC, OVERRIDE)
            .addAnnotation(
                suppressUncheckedCastAndRedundantQualifier()
            )
            .initializer(fieldListInitializer(fieldNames, messageDefClassName))
            .build()
    }

    /**
     * Builds the `oneofs` property of [MessageDef] implementation.
     */
    private fun buildOneofsProperty(): PropertySpec {
        val propType = Collection::class.asClassName().parameterizedBy(
            messageOneofClassName.parameterizedBy(
                messageTypeName.fullClassName(typeSystem)
            )
        )
        return PropertySpec.builder("oneofs", propType, PUBLIC, OVERRIDE)
            .initializer(fieldListInitializer(oneofNames))
            .build()
    }

    /**
     * Builds a property that references the instance of [MessageField]
     * or [MessageOneof] implementation.
     */
    private fun buildFieldInstanceProperty(
        fieldName: String,
        implSimpleClassName: String
    ): PropertySpec {
        return PropertySpec
            .builder(
                fieldName.propertyName,
                ClassName(
                    messageTypeName.javaPackage(typeSystem),
                    implSimpleClassName
                ),
                PUBLIC
            )
            .initializer(implSimpleClassName)
            .build()
    }
}

/**
 * Generates initialization code for the `fields` property
 * of the [MessageDef] implementation.
 *
 * The generated code looks like the following:
 * ```
 * listOf(
 *     ipv4 as MessageField<IpAddress, Any>,
 *     ipv6 as MessageField<IpAddress, Any>
 * )
 * ```
 */
private fun fieldListInitializer(
    fieldNames: Iterable<String>,
    messageField: ParameterizedTypeName
): String {
    return fieldNames.joinToString(
        ",${lineSeparator()}",
        "listOf(${lineSeparator()}",
        ")"
    ) {
        "${it.propertyName} as $messageField"
    }
}

/**
 * Generates initialization code for the `oneofs` property
 * of the [MessageDef] implementation.
 *
 * The generated code looks like the following:
 * ```
 * listOf(
 *     ipv4,
 *     ipv6
 * )
 * ```
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
