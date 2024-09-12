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

import com.google.protobuf.BoolValue
import com.google.protobuf.ByteString
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.spine.chords.runtime.MessageField
import io.spine.protobuf.AnyPacker.unpack
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
import io.spine.protodata.Type
import io.spine.protodata.TypeName
import io.spine.protodata.isEnum
import io.spine.protodata.isPrimitive
import io.spine.protodata.isRepeated
import io.spine.protodata.type.TypeSystem
import io.spine.protodata.type.findHeader
import io.spine.protodata.typeName
import io.spine.string.camelCase
import kotlin.reflect.KClass

/**
 * Package and class name of the `io.spine.protobuf.ValidatingBuilder`.
 *
 * It is not in the classpath of this ProtoData plugin and cannot be used directly.
 */
private object ValidatingBuilder {
    const val PACKAGE = "io.spine.protobuf"
    const val CLASS = "ValidatingBuilder"
}

/**
 * Implementation of [FileFragmentGenerator] that generates implementations
 * of [MessageField] for the fields of a Proto message.
 *
 * @param messageTypeName The [TypeName] of the message to generate the code for.
 * @param fields The collection of [Field]s to generate the code for.
 * @param typeSystem The [TypeSystem] to read external Proto messages.
 */
internal class MessageFieldObjectGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    private val typeSystem: TypeSystem
) : FileFragmentGenerator {

    /**
     * Returns a fully qualified [ClassName] of the given [messageTypeName].
     */
    private val messageFullClassName = messageTypeName.fullClassName(typeSystem)

    /**
     * Generates implementations of [MessageField] for the given [fields].
     */
    override fun generateCode(fileBuilder: FileSpec.Builder) {
        fields.forEach { field ->
            fileBuilder.addType(
                buildMessageFieldObject(field)
            )
        }
    }

    /**
     * Builds implementation of [MessageField] for the given [field].
     *
     * The generated code for some field looks like the following:
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
     */
    private fun buildMessageFieldObject(field: Field): TypeSpec {
        val fieldName = field.name.value
        val generatedClassName = messageTypeName.messageFieldClassName(fieldName)
        val fieldValueClassName = field.valueClassName(typeSystem)
        val superType = messageFieldClassName
            .parameterizedBy(
                messageFullClassName,
                fieldValueClassName
            )
        val stringType = String::class.asClassName()
        val boolType = Boolean::class.asClassName()
        val builderType = validatingBuilderClassName
            .parameterizedBy(messageFullClassName)

        return TypeSpec.objectBuilder(generatedClassName)
            .superclass(superType)
            .addProperty(
                PropertySpec
                    .builder("name", stringType, PUBLIC, OVERRIDE)
                    .initializer("\"$fieldName\"")
                    .build()
            ).addProperty(
                PropertySpec
                    .builder("required", boolType, PUBLIC, OVERRIDE)
                    .initializer("${field.required}")
                    .build()
            ).addFunction(
                FunSpec.builder("valueIn")
                    .addModifiers(PUBLIC, OVERRIDE)
                    .returns(fieldValueClassName)
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
                    .addParameter("newValue", fieldValueClassName)
                    .addCode(field.generateSetValueCode(messageTypeName))
                    .build()
            ).build()
    }
}

/**
 * Returns a fully qualified [ClassName] of `io.spine.protobuf.ValidatingBuilder`.
 */
private val validatingBuilderClassName = ClassName(
    ValidatingBuilder.PACKAGE,
    ValidatingBuilder.CLASS
)

/**
 * Returns a piece of code that sets a new value for the [Field].
 */
private fun Field.generateSetValueCode(messageTypeName: TypeName): String {
    val messageSimpleClassName = messageTypeName.simpleClassName
    val builderCast = "(builder as $messageSimpleClassName.Builder)"
    val setterCall = "$setterInvocation(newValue)"
    return if (isRepeated) {
        "$builderCast.clear${name.value.camelCase()}().$setterCall"
    } else {
        "$builderCast.$setterCall"
    }
}

/**
 * Returns a "setter" invocation code for the [Field].
 */
private val Field.setterInvocation: String
    get() = if (isRepeated)
        "addAll${name.value.camelCase()}"
    else
        "set${name.value.camelCase()}"

/**
 * Returns a [ClassName] of the value of a [Field].
 */
private fun Field.valueClassName(typeSystem: TypeSystem)
        : com.squareup.kotlinpoet.TypeName {
    val valueClassName = type.className(typeSystem)
    return if (isRepeated)
        Iterable::class.asClassName().parameterizedBy(valueClassName)
    else
        valueClassName
}

/**
 * Returns a [ClassName] for the [Type].
 */
private fun Type.className(typeSystem: TypeSystem): ClassName {
    return if (isPrimitive)
        primitiveClassName
    else
        messageClassName(typeSystem)
}

/**
 * Returns a [ClassName] for the [Type] that is a primitive.
 */
private val Type.primitiveClassName: ClassName
    get() {
        check(isPrimitive)
        return primitive.primitiveClass().asClassName()
    }

/**
 * Returns a [ClassName] for the [Type] that is a message.
 */
private fun Type.messageClassName(typeSystem: TypeSystem): ClassName {
    check(!isPrimitive)
    val fileHeader = typeSystem.findHeader(this)
    checkNotNull(fileHeader) {
        "Cannot determine file header for type `$this`"
    }
    return ClassName(fileHeader.javaPackage, typeName.simpleClassName)
}

/**
 * Indicates if the `required` option is applied to the [Field].
 */
private val Field.required: Boolean
    get() = optionList.any { option ->
        option.name == "required" &&
                unpack(option.value, BoolValue::class.java).value
    }

/**
 * Returns a "getter" invocation code for the [Field].
 */
private val Field.getterInvocation
    get() = if (isRepeated)
        name.value.propertyName + "List"
    else
        name.value.propertyName

/**
 * Returns a "hasValue" invocation code for the [Field].
 *
 * The generated code returns `true` if a field is repeated, is an enum,
 * or a primitive. This is required to be compatible with the design approach
 * of `protoc`-generated Java code. There, `hasValue` methods are not being
 * generated for the fields of such kinds.
 */
private val Field.hasValueInvocation: String
    get() = if (isRepeated || type.isEnum || type.isPrimitive)
        "true"
    else
        "message.has${name.value.camelCase()}()"

/**
 * Obtains a Kotlin class which corresponds to the [PrimitiveType].
 */
private fun PrimitiveType.primitiveClass(): KClass<*> {
    return when (this) {
        TYPE_DOUBLE -> Double::class
        TYPE_FLOAT -> Float::class
        TYPE_INT64, TYPE_UINT64, TYPE_SINT64, TYPE_FIXED64, TYPE_SFIXED64 -> Long::class
        TYPE_INT32, TYPE_UINT32, TYPE_SINT32, TYPE_FIXED32, TYPE_SFIXED32 -> Int::class
        TYPE_BOOL -> Boolean::class
        TYPE_STRING -> String::class
        TYPE_BYTES -> ByteString::class
        UNRECOGNIZED, PT_UNKNOWN -> error("Unknown primitive type: `$this`.")
    }
}
