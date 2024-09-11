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
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.isPartOfOneof
import io.spine.protodata.type.TypeSystem
import io.spine.string.Indent
import java.lang.System.lineSeparator
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * Suffix of the generated file name.
 */
private const val ClassNameSuffix = "Def"

/**
 * Generates a separate Kotlin file that contains `MessageField`
 * and `MessageOneof` implementations for the fields of a Proto message.
 *
 * Below is generated `MessageField` implementation for the field `domain_name`
 * of the `RegistrationInfo` message:
 *
 * ```
 *     public val KClass<RegistrationInfo>.domainName: RegistrationInfoDomainName
 *         get() = RegistrationInfoDomainName()
 *
 *     public class RegistrationInfoDomainName:
 *         MessageField<RegistrationInfo, InternetDomain> {
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
 * Generated implementation of `MessageOneof` for the `value` field
 * of the `IpAddress` message:
 *
 * ```
 *     public val KClass<IpAddress>.`value`: IpAddressValue
 *         get() = IpAddressValue()
 *
 *     public class IpAddressValue : MessageOneof<IpAddress> {
 *         private val fieldMap: Map<Int, MessageField<IpAddress, *>> = mapOf(
 *             1 to IpAddress::class.ipv4,
 *             2 to IpAddress::class.ipv6)
 *
 *         public override val name: String = "value"
 *
 *         public override val fields: Iterable<MessageField<IpAddress, *>>
 *             = fieldMap.values
 *
 *         public override fun selectedField(message: IpAddress):
 *             MessageField<IpAddress, *>? = fieldMap[message.valueCase.number]
 *     }
 *
 * ```
 *
 * Generated field usage example:
 *
 * ```
 *     // Read field value for the given instance of the message.
 *     val domain = RegistrationInfo::class.domainName
 *         .valueIn(registrationInfo)
 *
 *     // Set field value for the message builder provided.
 *     RegistrationInfo::class.domainName.setValue(
 *         registrationInfoBuilder,
 *         domain
 *     )
 * ```
 *
 * @param messageTypeName a [TypeName] of the message to generate the code for.
 * @param fields a collection of [Field]s to generate the code for.
 * @param typeSystem a [TypeSystem] to read external Proto messages.
 */
internal class MessageDefGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    private val typeSystem: TypeSystem
) : FileGenerator(typeSystem) {

    /**
     * Collection of names of [fields].
     */
    private val fieldNames get() = fields.map { it.name.value }

    /**
     * Contains the fields which are parts of some `oneof` grouped by its name.
     */
    private val oneofFieldMap = fields.filter { field ->
        field.isPartOfOneof
    }.groupBy { oneofField ->
        oneofField.oneofName.value
    }

    /**
     * Returns a [Path] to the generated file that is relative
     * to the source root.
     */
    override fun filePath(): Path {
        return messageTypeName.filePath(ClassNameSuffix)
    }

    /**
     * Generates a content of the file.
     */
    override fun fileContent(): String {
        return FileSpec.builder(messageTypeName.fullClassName)
            .indent(Indent.defaultJavaIndent.toString())
            .also { fileBuilder ->
                val messageDefObjectBuilder = TypeSpec.objectBuilder(
                    messageTypeName.generatedClassName(ClassNameSuffix)
                ).addSuperinterface(
                    messageDefClassName.parameterizedBy(
                        messageTypeName.fullClassName
                    )
                )
                generateMessageDefFieldProperties(messageDefObjectBuilder)
                generateMessageDefCollectionProperties(messageDefObjectBuilder)
                fileBuilder.addType(messageDefObjectBuilder.build())
                generateFieldsObjectDeclarations(fileBuilder)
                generateKClassProperties(fileBuilder)
            }
            .build()
            .toString()
    }

    private fun generateMessageDefCollectionProperties(
        messageDefObjectBuilder: TypeSpec.Builder
    ) {
        generateMessageDefCollectionProperty(
            messageDefObjectBuilder,
            "fields",
            Collection::class.asClassName().parameterizedBy(
                messageFieldClassName.parameterizedBy(
                    messageTypeName.fullClassName,
                    WildcardTypeName.producerOf(messageFieldValueTypeAlias)
                )
            ),
            fieldNames
        )
        generateMessageDefCollectionProperty(
            messageDefObjectBuilder,
            "oneofs",
            Collection::class.asClassName().parameterizedBy(
                messageOneofClassName.parameterizedBy(messageTypeName.fullClassName)
            ),
            oneofFieldMap.keys
        )
    }

    private fun generateMessageDefFieldProperties(
        messageDefObjectBuilder: TypeSpec.Builder
    ) {
        fieldNames.forEach { fieldName ->
            messageDefObjectBuilder.addProperty(
                generateMessageDefFieldProperty(
                    fieldName,
                    messageTypeName.messageFieldClassName(fieldName)
                )
            )
        }
        oneofFieldMap.map { it.key }.forEach { fieldName ->
            messageDefObjectBuilder.addProperty(
                generateMessageDefFieldProperty(
                    fieldName,
                    messageTypeName.messageOneofClassName(fieldName)
                )
            )
        }
    }

    private fun generateMessageDefFieldProperty(
        fieldName: String,
        simpleClassName: String
    ): PropertySpec {
        return PropertySpec
            .builder(fieldName.propertyName, messageTypeName.fullClassName, PUBLIC)
            .initializer(simpleClassName)
            .build()
    }

    private fun generateKClassProperties(fileBuilder: FileSpec.Builder) {
        fieldNames.plus(oneofFieldMap.map { it.key }).forEach { fieldName ->
            fileBuilder.addProperty(
                buildKClassPropertyDeclaration(fieldName, messageTypeName)
            )
        }
    }

    private fun generateFieldsObjectDeclarations(fileBuilder: FileSpec.Builder) {
        fields.forEach { field ->
            fileBuilder.addType(
                buildMessageFieldObject(field)
            )
        }
        oneofFieldMap.forEach { oneofNameToFields ->
            fileBuilder.addType(
                buildMessageOneofObject(
                    oneofNameToFields.key,
                    oneofNameToFields.value
                )
            )
        }
    }

    /**
     * Generates implementation of `MessageField` for the given field.
     *
     * The generated code looks like the following:
     *
     * ```
     *     public class RegistrationInfoDomainName:
     *         MessageField<RegistrationInfo, InternetDomain> {
     *
     *         public override val name: String = "domain_name"
     *
     *         public override val required: Boolean = true
     *
     *         public override fun valueIn(message: RegistrationInfo)
     *             : InternetDomain {
     *             return message.domainName
     *         }
     *
     *         public override fun hasValue(message: RegistrationInfo)
     *             : Boolean {
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
     */
    private fun buildMessageFieldObject(field: Field): TypeSpec {
        val generatedClassName = messageTypeName
            .messageFieldClassName(field.name.value)
        val messageFullClassName = messageTypeName.fullClassName
        val superType = messageFieldClassName
            .parameterizedBy(messageFullClassName, field.valueClassName)
        val stringType = String::class.asClassName()
        val boolType = Boolean::class.asClassName()
        val builderType = validatingBuilderClassName
            .parameterizedBy(messageFullClassName)

        return TypeSpec.objectBuilder(generatedClassName)
            .superclass(superType)
            .addProperty(
                PropertySpec
                    .builder("name", stringType, PUBLIC, OVERRIDE)
                    .initializer("\"${field.name.value}\"")
                    .build()
            ).addProperty(
                PropertySpec
                    .builder("required", boolType, PUBLIC, OVERRIDE)
                    .initializer("${field.required}")
                    .build()
            ).addFunction(
                FunSpec.builder("valueIn")
                    .addModifiers(PUBLIC, OVERRIDE)
                    .returns(field.valueClassName)
                    .addParameter("message", messageFullClassName)
                    .addCode("return message.${field.getterInvocation}")
                    .build()
            ).addFunction(
                FunSpec.builder("hasValue")
                    .addModifiers(PUBLIC, OVERRIDE)
                    .returns(boolType)
                    .addParameter("message", messageFullClassName)
                    .addCode("return ${field.hasValueInvocation}")
                    .build()
            ).addFunction(
                FunSpec.builder("setValue")
                    .addModifiers(PUBLIC, OVERRIDE)
                    .addParameter("builder", builderType)
                    .addParameter("newValue", field.valueClassName)
                    .addCode(field.generateSetterCode(messageTypeName))
                    .build()
            ).build()
    }

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
    private fun buildMessageOneofObject(
        oneofName: String,
        oneofFields: List<Field>
    ): TypeSpec {
        val messageFieldType = messageFieldClassName
            .parameterizedBy(
                messageTypeName.fullClassName,
                WildcardTypeName.producerOf(messageFieldValueTypeAlias)
            )
        val fieldMapType = Map::class.asClassName().parameterizedBy(
            Int::class.asClassName(),
            messageFieldType
        )
        val stringType = String::class.asClassName()
        val fieldsReturnType = Collection::class.asClassName()
            .parameterizedBy(messageFieldType)
        val superType = messageOneofClassName
            .parameterizedBy(messageTypeName.fullClassName)
        val oneofPropName = oneofName.propertyName
        val generatedClassName = messageTypeName
            .messageOneofClassName(oneofName)

        return TypeSpec.objectBuilder(generatedClassName)
            .addSuperinterface(superType)
            .addProperty(
                PropertySpec
                    .builder("fieldMap", fieldMapType, PRIVATE)
                    .initializer(
                        fieldMapInitializer(messageTypeName, oneofFields)
                    )
                    .build()
            ).addProperty(
                PropertySpec
                    .builder("name", stringType, PUBLIC, OVERRIDE)
                    .initializer("\"$oneofName\"")
                    .build()
            ).addProperty(
                PropertySpec
                    .builder("fields", fieldsReturnType, PUBLIC, OVERRIDE)
                    .initializer("fieldMap.values")
                    .build()
            ).addFunction(
                FunSpec.builder("selectedField")
                    .addModifiers(PUBLIC, OVERRIDE)
                    .returns(messageFieldType.copy(nullable = true))
                    .addParameter("message", messageTypeName.fullClassName)
                    .addCode("return fieldMap[message.${oneofPropName}Case.number]")
                    .build()
            ).build()
    }

    /**
     * Builds a property declaration for the given field
     * that looks like the following:
     * ```
     *     public val KClass<RegistrationInfo>.domainName:
     *         RegistrationInfoDomainName get() = RegistrationInfoDomainName()
     * ```
     */
    private fun buildKClassPropertyDeclaration(
        fieldName: String,
        messageTypeName: TypeName
    ): PropertySpec {
        val simpleClassName = messageTypeName.messageFieldClassName(fieldName)
        val propertyType = ClassName(
            messageTypeName.javaPackage(typeSystem),
            simpleClassName
        )
        val receiverType = KClass::class.asClassName()
            .parameterizedBy(messageTypeName.fullClassName)
        val getterCode = FunSpec.getterBuilder()
            .addCode("return $simpleClassName")
            .build()

        return PropertySpec.builder(fieldName.propertyName, propertyType)
            .receiver(receiverType)
            .getter(getterCode)
            .build()
    }
}

private fun generateMessageDefCollectionProperty(
    messageDefObjectBuilder: TypeSpec.Builder,
    propertyName: String,
    propertyType: ParameterizedTypeName,
    fieldNames: Collection<String>
) {
    messageDefObjectBuilder
        .addProperty(
            PropertySpec.builder(propertyName, propertyType, PUBLIC, OVERRIDE)
                .initializer(fieldListInitializer(fieldNames))
                .build()
        )
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

/**
 * Generates initialization code for the `fieldMap` property
 * of the `MessageOneof` implementation.
 */
private fun fieldMapInitializer(
    typeName: TypeName,
    fields: Iterable<Field>
): String {
    val generatedClassName = typeName.generatedClassName(ClassNameSuffix)
    return fields.joinToString(
        ",${lineSeparator()}",
        "mapOf(${lineSeparator()}",
        ")"
    ) {
        "${it.number} to $generatedClassName.${it.name.value.propertyName}"
    }
}
