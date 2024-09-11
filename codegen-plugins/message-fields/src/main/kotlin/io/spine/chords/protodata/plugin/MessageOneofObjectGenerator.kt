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
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import io.spine.chords.protodata.plugin.MessageDefFileGenerator.Companion.CLASS_NAME_SUFFIX
import io.spine.chords.runtime.MessageOneof
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.type.TypeSystem
import java.lang.System.lineSeparator

/**
 * Generates implementation of [MessageOneof] for an oneof of a Proto message.
 *
 * The generated code looks like the following:
 *
 * ```
 *     public object RegistrationInfoDomainName:
 *         MessageField<RegistrationInfo, InternetDomain>() {
 *
 *         public override val name: String = "domain_name"
 *
 *         public override val required: Boolean = true
 *
 *         public override fun valueIn(message: RegistrationInfo) : InternetDomain {
 *             return message.domainName
 *         }
 *
 *         public override fun hasValue(message: RegistrationInfo) : Boolean {
 *             return message.hasDomainName()
 *         }
 *
 *         override fun setValue(
 *             builder: ValidatingBuilder<RegistrationInfo>,
 *             value: InternetDomain
 *         ) {
 *             (builder as RegistrationInfo.Builder).setDomainName(value)
 *         }
 *     }
 * ```
 *
 * @param messageTypeName a [TypeName] of the message to generate the code for.
 * @param typeSystem a [TypeSystem] to read external Proto messages.
 */
internal class MessageOneofObjectGenerator(
    private val messageTypeName: TypeName,
    private val typeSystem: TypeSystem
) {

    private val javaPackage = messageTypeName.javaPackage(typeSystem)

    private val messageFullClassName = ClassName(
        javaPackage,
        messageTypeName.simpleClassName
    )

    /**
     * Generates implementation of `MessageOneof` for the given `oneof` field
     * that looks like the following:
     *
     * ```
     *     public val KClass<IpAddress>.`value`: IpAddressValue
     *         get() = IpAddressValue()
     *
     *     public class IpAddressValue : MessageOneof<IpAddress> {
     *         private val fieldMap: Map<Int, MessageField<IpAddress, *>> =
     *             mapOf(
     *                 1 to IpAddress::class.ipv4,
     *                 2 to IpAddress::class.ipv6
     *             )
     *
     *         public override val name: String = "value"
     *
     *         public override val fields: Iterable<MessageField<IpAddress, *>>
     *             = fieldMap.values
     *
     *         public override fun selectedField(message: IpAddress):
     *             MessageField<IpAddress, *>? =
     *                 fieldMap[message.valueCase.number]
     *     }
     * ```
     */
    internal fun buildMessageOneofObject(
        oneofName: String,
        oneofFields: List<Field>
    ): TypeSpec {
        val messageFieldType = messageFieldClassName.parameterizedBy(
            messageFullClassName,
            WildcardTypeName.producerOf(messageFieldValueTypeAlias)
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
                    .initializer(
                        fieldMapInitializer(
                            oneofFields,
                            messageTypeName.generateClassName(CLASS_NAME_SUFFIX)
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
 * Generates initialization code for the `fieldMap` property
 * of the [MessageOneof] implementation.
 */
private fun fieldMapInitializer(
    fields: Iterable<Field>,
    generatedClassName: String
): String {
    return fields.joinToString(
        ",${lineSeparator()}",
        "mapOf(${lineSeparator()}",
        ")"
    ) {
        "${it.number} to $generatedClassName.${it.name.value.propertyName}"
    }
}
