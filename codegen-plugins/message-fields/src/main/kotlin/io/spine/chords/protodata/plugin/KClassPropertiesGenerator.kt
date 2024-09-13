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
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.isPartOfOneof
import io.spine.protodata.type.TypeSystem
import kotlin.reflect.KClass

/**
 * Implementation of [FileFragmentGenerator] that generates property declarations
 * for the given collection of the Proto message fields.
 *
 * The generated code looks like the following:
 * ```
 *     public val KClass<RegistrationInfo>.domainName:
 *         RegistrationInfoDomainName get() = RegistrationInfoDomainName
 *
 *     public val KClass<RegistrationInfo>.resourceKeeper:
 *         RegistrationInfoResourceKeeperField get() = RegistrationInfoResourceKeeperField
 *
 *     public val KClass<IRegistrationInfo>.registrator:
 *         RegistrationInfoRegistratorField get() = RegistrationInfoRegistratorField
 * ```
 *
 * @param messageTypeName The [TypeName] of the message to generate the code for.
 * @param fields The collection of [Field]s to generate the code for.
 * @param typeSystem The [TypeSystem] to read external Proto messages.
 */
internal class KClassPropertiesGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    private val typeSystem: TypeSystem
) : FileFragmentGenerator {

    override fun generateCode(fileBuilder: FileSpec.Builder) {
        fields.map {
            it.name.value
        }.forEach { fieldName ->
            fileBuilder.addProperty(
                buildKClassProperty(
                    fieldName,
                    messageTypeName.messageFieldClassName(fieldName)
                )
            )
        }

        fields.filter { field ->
            field.isPartOfOneof
        }.groupBy { oneofField ->
            oneofField.oneofName.value
        }.keys.forEach { oneofFieldName ->
            fileBuilder.addProperty(
                buildKClassProperty(
                    oneofFieldName,
                    messageTypeName.messageOneofClassName(oneofFieldName)
                )
            )
        }
    }

    /**
     * Builds a [PropertySpec] for the given `oneof` field
     * that looks like the following:
     * ```
     *     public val KClass<RegistrationInfo>.domainName:
     *         RegistrationInfoDomainName get() = RegistrationInfoDomainName
     * ```
     */
    private fun buildKClassProperty(
        fieldName: String,
        simpleClassName: String
    ): PropertySpec {
        val propertyType = ClassName(
            messageTypeName.javaPackage(typeSystem),
            simpleClassName
        )
        val receiverType = KClass::class.asClassName()
            .parameterizedBy(messageTypeName.fullClassName(typeSystem))

        val getterCode = FunSpec.getterBuilder()
            .addCode("return $simpleClassName")
            .build()

        return PropertySpec.builder(fieldName.propertyName, propertyType)
            .receiver(receiverType)
            .getter(getterCode)
            .build()
    }
}
