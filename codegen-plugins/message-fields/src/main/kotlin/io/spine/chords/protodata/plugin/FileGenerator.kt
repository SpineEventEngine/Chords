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

import com.google.protobuf.ByteString
import com.google.protobuf.StringValue
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import io.spine.chords.runtime.MessageDef
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageFieldValue
import io.spine.chords.runtime.MessageOneof
import io.spine.protodata.Field
import io.spine.protodata.PrimitiveType
import io.spine.protodata.PrimitiveType.PT_UNKNOWN
import io.spine.protodata.PrimitiveType.TYPE_BOOL
import io.spine.protodata.PrimitiveType.TYPE_BYTES
import io.spine.protodata.PrimitiveType.TYPE_DOUBLE
import io.spine.protodata.PrimitiveType.TYPE_FIXED32
import io.spine.protodata.PrimitiveType.TYPE_FIXED64
import io.spine.protodata.PrimitiveType.TYPE_FLOAT
import io.spine.protodata.PrimitiveType.TYPE_INT32
import io.spine.protodata.PrimitiveType.TYPE_INT64
import io.spine.protodata.PrimitiveType.TYPE_SFIXED32
import io.spine.protodata.PrimitiveType.TYPE_SFIXED64
import io.spine.protodata.PrimitiveType.TYPE_SINT32
import io.spine.protodata.PrimitiveType.TYPE_SINT64
import io.spine.protodata.PrimitiveType.TYPE_STRING
import io.spine.protodata.PrimitiveType.TYPE_UINT32
import io.spine.protodata.PrimitiveType.TYPE_UINT64
import io.spine.protodata.PrimitiveType.UNRECOGNIZED
import io.spine.protodata.ProtoFileHeader
import io.spine.protodata.Type
import io.spine.protodata.TypeName
import io.spine.protodata.find
import io.spine.protodata.isPrimitive
import io.spine.protodata.isRepeated
import io.spine.protodata.type.TypeSystem
import io.spine.protodata.type.findHeader
import io.spine.protodata.typeName
import io.spine.string.Indent
import io.spine.string.camelCase
import java.lang.System.lineSeparator
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * Package and class name of the `io.spine.protobuf.ValidatingBuilder`.
 *
 * It is not in the classpath and cannot be used directly.
 */
private object ValidatingBuilder {
    const val PACKAGE = "io.spine.protobuf"
    const val CLASS = "ValidatingBuilder"
}

/**
 *
 *
 * @param messageTypeName a [TypeName] of the message to generate the code for.
 * @param fields a collection of [Field]s  to generate the code for.
 * @param typeSystem a [TypeSystem] to read external Proto messages.
 */
internal abstract class FileGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    private val typeSystem: TypeSystem
) {
    /**
     * A [ClassName] of the Proto message.
     */
    private val messageClass = messageTypeName.fullClassName

    /**
     * Returns a suffix which is used to generate a file name.
     *
     * See [filePath] for detail.
     */
    internal abstract val fileNameSuffix: String

    /**
     * Builds content of the generated file.
     */
    internal abstract fun buildFileContent(fileBuilder: FileSpec.Builder)

    /**
     * Returns a [Path] to the generated file that is relative
     * to the source root.
     */
    internal fun filePath(): Path {
        return messageTypeName.filePath
    }

    /**
     * Generates a content of the file.
     */
    internal fun fileContent(): String {
        return FileSpec.builder(messageClass)
            .indent(Indent.defaultJavaIndent.toString())
            .also { fileBuilder ->
                buildFileContent(fileBuilder)
            }
            .build()
            .toString()
    }

    internal fun buildFieldProperty(fieldName: String): PropertySpec {
        return buildPropertyDeclaration(fieldName, false)
    }

    internal fun buildOneofProperty(fieldName: String): PropertySpec {
        return buildPropertyDeclaration(fieldName, true)
    }

    /**
     * Builds a property declaration for the given field
     * that looks like the following:
     * ```
     *     public val KClass<RegistrationInfo>.domainName:
     *         RegistrationInfoDomainName get() = RegistrationInfoDomainName()
     * ```
     */
    private fun buildPropertyDeclaration(
        fieldName: String,
        isOneof: Boolean
    ): PropertySpec {
        val generatedClassName = if (isOneof)
            messageTypeName.messageOneofClassName(fieldName)
        else messageTypeName.messageFieldClassName(fieldName)
        val propertyType = ClassName(
            messageTypeName.javaPackage,
            generatedClassName
        )
        val receiverType = KClass::class.asClassName()
            .parameterizedBy(messageClass)
        val getterCode = FunSpec.getterBuilder()
            .addCode("return $generatedClassName()")
            .build()

        return PropertySpec.builder(fieldName.propertyName, propertyType)
            .receiver(receiverType)
            .getter(getterCode)
            .build()
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
    internal fun buildMessageFieldClass(field: Field): TypeSpec {
        val generatedClassName = messageTypeName
            .messageFieldClassName(field.name.value)
        val messageFullClassName = messageTypeName.fullClassName
        val superType = messageFieldClassName
            .parameterizedBy(messageFullClassName, field.valueClassName)
        val stringType = String::class.asClassName()
        val boolType = Boolean::class.asClassName()
        val builderType = validatingBuilderClassName
            .parameterizedBy(messageFullClassName)

        return TypeSpec.classBuilder(generatedClassName)
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
    internal fun buildMessageOneofClass(
        oneofName: String,
        oneofFields: List<Field>
    ): TypeSpec {
        val messageFieldType = messageFieldClassName
            .parameterizedBy(
                messageTypeName.fullClassName,
                WildcardTypeName.producerOf(messageFieldValueType)
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

        return TypeSpec.classBuilder(generatedClassName)
            .addSuperinterface(superType)
            .addProperty(
                PropertySpec
                    .builder("fieldMap", fieldMapType, PRIVATE)
                    .initializer(fieldMapInitializer(messageTypeName, oneofFields))
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
     * Returns a fully qualified [ClassName] for a [TypeName].
     */
    internal val TypeName.fullClassName
        get() = ClassName(javaPackage, simpleClassName)

    /**
     * Returns a [ClassName] of the value of a [Field].
     */
    private val Field.valueClassName
        get() = if (isRepeated)
            Iterable::class.asClassName()
                .parameterizedBy(type.className)
        else type.className

    /**
     * Returns [Path] to the generated file for a [TypeName].
     */
    private val TypeName.filePath: Path
        get() = Path.of(
            javaPackage.replace('.', '/'),
            fileName(fileNameSuffix)
        )

    /**
     * Returns [ClassName] for the [Type].
     */
    private val Type.className
        get() = if (isPrimitive)
            primitiveClassName()
        else
            messageClassName()

    /**
     * Returns [ClassName] for the [Type] that is a message.
     */
    private fun Type.messageClassName(): ClassName {
        check(!isPrimitive)
        val fileHeader = typeSystem.findHeader(this)
        checkNotNull(fileHeader) {
            "Cannot determine file header for type `$this`"
        }
        return ClassName(fileHeader.javaPackage, typeName.simpleClassName)
    }

    /**
     * Returns a Java package for the [TypeName].
     */
    internal val TypeName.javaPackage: String
        get() {
            val messageToHeader = typeSystem.findMessage(this)
            checkNotNull(messageToHeader) {
                "Cannot determine file header for TypeName `$this`"
            }
            return messageToHeader.second.javaPackage
        }
}

/**
 * Returns [ClassName] of [MessageField].
 */
internal val messageFieldClassName: ClassName
    get() = MessageField::class.asClassName()

/**
 * Returns [ClassName] of [MessageOneof].
 */
internal val messageOneofClassName: ClassName
    get() = MessageOneof::class.asClassName()

/**
 * Returns [ClassName] of [MessageDef].
 */
internal val messageDefClassName: ClassName
    get() = MessageDef::class.asClassName()

/**
 * Returns [ClassName] of [MessageFieldValue].
 */
internal val messageFieldValueType: ClassName
    get() = MessageFieldValue::class.asClassName()

/**
 * Returns [ClassName] of `ValidatingBuilder`.
 */
internal val validatingBuilderClassName: ClassName
    get() = ClassName(
        ValidatingBuilder.PACKAGE,
        ValidatingBuilder.CLASS
    )

/**
 * Converts a Proto field name to "property" name,
 * e.g. "domain_name" -> "domainName".
 */
internal val String.propertyName
    get() = camelCase().replaceFirstChar { it.lowercase() }

/**
 * Generates initialization code for the `fieldMap` property
 * of the `MessageOneof` implementation.
 */
internal fun fieldMapInitializer(
    typeName: TypeName,
    fields: Iterable<Field>
): String {
    return fields.joinToString(
        ",${lineSeparator()}",
        "mapOf(${lineSeparator()}",
        ")"
    ) {
        "${it.number} to ${typeName.simpleClassName}::class.${it.name.value.propertyName}"
    }
}

/**
 * Generates initialization code for the `fields` property
 * of the `MessageType` implementation.
 */
internal fun fieldListInitializer(
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
 * Returns [ClassName] for the [Type] that is a primitive.
 */
private fun Type.primitiveClassName(): ClassName {
    check(isPrimitive)
    return primitive.primitiveClass().asClassName()
}

/**
 * Returns a Java package declared in [ProtoFileHeader].
 */
private val ProtoFileHeader.javaPackage: String
    get() {
        val optionName = "java_package"
        val option = optionList.find(optionName, StringValue::class.java)
        checkNotNull(option) {
            "Cannot find option `$optionName` in file header `${this}`."
        }
        return option.value
    }

/**
 * Obtains a Kotlin class which corresponds to the [PrimitiveType].
 */
private fun PrimitiveType.primitiveClass(): KClass<*> =
    when (this) {
        TYPE_DOUBLE -> Double::class
        TYPE_FLOAT -> Float::class
        TYPE_INT64, TYPE_UINT64, TYPE_SINT64, TYPE_FIXED64, TYPE_SFIXED64 -> Long::class
        TYPE_INT32, TYPE_UINT32, TYPE_SINT32, TYPE_FIXED32, TYPE_SFIXED32 -> Int::class
        TYPE_BOOL -> Boolean::class
        TYPE_STRING -> String::class
        TYPE_BYTES -> ByteString::class
        UNRECOGNIZED, PT_UNKNOWN -> error("Unknown primitive type: `$this`.")
    }
