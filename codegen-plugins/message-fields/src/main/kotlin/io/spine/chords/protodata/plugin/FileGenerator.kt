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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import io.spine.chords.protodata.plugin.CodegenRuntime.MESSAGE_DEF_CLASS
import io.spine.chords.protodata.plugin.CodegenRuntime.MESSAGE_FIELD_CLASS
import io.spine.chords.protodata.plugin.CodegenRuntime.MESSAGE_FIELD_VALUE
import io.spine.chords.protodata.plugin.CodegenRuntime.MESSAGE_ONEOF_CLASS
import io.spine.chords.protodata.plugin.CodegenRuntime.PACKAGE
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
 * Package and class names of the `io.spine.chords.runtime.MessageField`
 * and `io.spine.chords.runtime.MessageOneof`.
 *
 * It is not in the classpath and cannot be used directly.
 */
private object CodegenRuntime {
    const val PACKAGE = "io.spine.chords.runtime"
    const val MESSAGE_FIELD_CLASS = "MessageField"
    const val MESSAGE_ONEOF_CLASS = "MessageOneof"
    const val MESSAGE_DEF_CLASS = "MessageDef"
    const val MESSAGE_FIELD_VALUE = "MessageFieldValue"
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
     * Returns a fully qualified [ClassName] for a [TypeName].
     */
    internal val TypeName.fullClassName
        get() = ClassName(javaPackage, simpleClassName)

    /**
     * Returns a [ClassName] of the value of a [Field].
     */
    internal val Field.valueClassName
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
 * Returns [ClassName] of `MessageField`.
 */
internal val messageFieldClassName: ClassName
    get() = ClassName(PACKAGE, MESSAGE_FIELD_CLASS)

/**
 * Returns [ClassName] of `MessageOneof`.
 */
internal val messageOneofClassName: ClassName
    get() = ClassName(PACKAGE, MESSAGE_ONEOF_CLASS)

/**
 * Returns [ClassName] of `MessageDef`.
 */
internal val messageDefClassName: ClassName
    get() = ClassName(PACKAGE, MESSAGE_DEF_CLASS)

/**
 * Returns [ClassName] of `MessageDef`.
 */
internal val messageFieldValueType: com.squareup.kotlinpoet.TypeName
    get() = ClassName(PACKAGE, MESSAGE_FIELD_VALUE)

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
