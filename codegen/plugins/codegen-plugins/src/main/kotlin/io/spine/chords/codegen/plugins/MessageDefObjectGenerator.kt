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
import com.squareup.kotlinpoet.CodeBlock
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
import io.spine.chords.runtime.MessageFieldValue
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
     *             ipv4.safeCast<MessageField<IpAddressDef, Any>>(),
     *             ipv6.safeCast<MessageField<IpAddressDef, Any>>()
     *         )
     *
     *     public override val oneofs: Collection<MessageOneof<IpAddress>>
     *         = listOf(value)
     * }
     * ```
     */
    override fun generateCode(fileBuilder: FileSpec.Builder) {
        fields.ifNotEmpty {
            fileBuilder.addImport(MessageDef::class.java.packageName, "safeCast")
        }
        val messageDefClassName = messageTypeName.messageDefClassName()
        val superinterface = MessageDef::class.asClassName().parameterizedBy(
            messageTypeName.fullClassName(typeSystem)
        )
        fileBuilder.addType(
            TypeSpec.objectBuilder(messageDefClassName)
                .addSuperinterface(superinterface)
                .addKdoc(buildKDoc())
                .addAnnotation(generatedAnnotation())
                .also { objectBuilder ->
                    fieldsInstances(objectBuilder)
                    oneofsInstances(objectBuilder)
                }
                .addProperty(fieldsProperty())
                .addProperty(oneofsProperty())
                .build()
        )
    }

    /**
     * Generates the properties with oneofs instances.
     */
    private fun oneofsInstances(objectBuilder: TypeSpec.Builder) {
        oneofNames.forEach { fieldName ->
            objectBuilder.addProperty(
                fieldInstanceProperty(
                    fieldName,
                    messageTypeName.messageOneofClassName(fieldName)
                )
            )
        }
    }

    /**
     * Generates the properties with fields instances.
     */
    private fun fieldsInstances(objectBuilder: TypeSpec.Builder) {
        fieldNames.forEach { fieldName ->
            objectBuilder.addProperty(
                fieldInstanceProperty(
                    fieldName,
                    messageTypeName.messageFieldClassName(fieldName)
                )
            )
        }
    }

    /**
     * Builds the KDoc section for the generated implementation of [MessageDef].
     */
    private fun buildKDoc() = CodeBlock.of(
        "A [%T] implementation that allows access to the [%T] message fields at runtime.",
        MessageDef::class.asClassName(),
        messageTypeName.fullClassName(typeSystem)
    )

    /**
     * Builds the `fields` property of [MessageDef] implementation.
     */
    private fun fieldsProperty(): PropertySpec {
        val messageType = messageTypeName.fullClassName(typeSystem)
        val messageDefClassName = MessageField::class.asClassName().parameterizedBy(
            messageType,
            MessageFieldValue::class.asClassName()
        )
        val type = Collection::class.asClassName().parameterizedBy(
            messageDefClassName
        )
        return PropertySpec.builder("fields", type, PUBLIC, OVERRIDE)
            .initializer(
                fieldsPropertyInitializer(fieldNames, messageDefClassName)
            ).build()
    }

    /**
     * Builds the `oneofs` property of [MessageDef] implementation.
     */
    private fun oneofsProperty(): PropertySpec {
        val propType = Collection::class.asClassName().parameterizedBy(
            MessageOneof::class.asClassName().parameterizedBy(
                messageTypeName.fullClassName(typeSystem)
            )
        )
        return PropertySpec.builder("oneofs", propType, PUBLIC, OVERRIDE)
            .initializer(oneofsPropertyInitializer(oneofNames))
            .build()
    }

    /**
     * Builds a property that references the instance of [MessageField]
     * or [MessageOneof] implementation.
     */
    private fun fieldInstanceProperty(
        fieldName: String,
        implSimpleClassName: String
    ): PropertySpec {
        val fullClassName = ClassName(
            messageTypeName.javaPackage(typeSystem),
            implSimpleClassName
        )
        return PropertySpec
            .builder(fieldName.propertyName, fullClassName, PUBLIC)
            .initializer(implSimpleClassName)
            .build()
    }
}

/**
 * Executes the given [action] if this [Iterable] is not empty.
 */
private fun <E> Iterable<E>.ifNotEmpty(action: (Iterable<E>) -> Unit) {
    if (iterator().hasNext()) action(this)
}

/**
 * Generates initialization code for the `fields` property
 * of the [MessageDef] implementation.
 *
 * The generated code looks like the following:
 * ```
 * listOf(
 *     ipv4.safeCast<MessageField<IpAddressDef, Any>>(),
 *     ipv6.safeCast<MessageField<IpAddressDef, Any>>()
 * )
 * ```
 */
@Suppress("SpreadOperator")
private fun fieldsPropertyInitializer(
    fieldNames: Iterable<String>,
    messageField: ParameterizedTypeName
) = CodeBlock.of(
    fieldNames.joinToString(
        ",${lineSeparator()}",
        "listOf(${lineSeparator()}",
        ")"
    ) { fieldName ->
        "${fieldName.propertyName}.safeCast<%T>()"
    }, *fieldNames.map { messageField }.toTypedArray()
)

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
private fun oneofsPropertyInitializer(
    fieldNames: Iterable<String>
): String {
    return fieldNames.joinToString(
        ",${lineSeparator()}",
        "listOf(${lineSeparator()}",
        ")"
    ) { fieldName ->
        fieldName.propertyName
    }
}
