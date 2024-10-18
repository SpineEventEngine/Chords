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
import com.google.protobuf.Descriptors.FieldDescriptor
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
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
import io.spine.protodata.ast.Field
import io.spine.protodata.ast.Type
import io.spine.protodata.ast.TypeName
import io.spine.protodata.ast.isEnum
import io.spine.protodata.ast.isPrimitive
import io.spine.protodata.ast.isRepeated
import io.spine.protodata.ast.typeName
import io.spine.protodata.java.getterName
import io.spine.protodata.java.javaPackage
import io.spine.protodata.java.primarySetterName
import io.spine.protodata.java.primitiveClass
import io.spine.protodata.type.TypeSystem
import io.spine.protodata.type.findHeader
import io.spine.string.camelCase

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
            fileBuilder.addType(buildMessageFieldObject(field))
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
     *         public override val descriptor: Descriptors.FieldDescriptor =
     *             RegistrationInfo.getDescriptor().findFieldByName(name)
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
        val messageFieldClassName = messageTypeName
            .messageFieldClassName(field.name.value)
        val superinterface = MessageField::class.asClassName()
            .parameterizedBy(
                messageFullClassName,
                field.valueClassName(typeSystem)
            )
        return TypeSpec
            .objectBuilder(messageFieldClassName)
            .addSuperinterface(superinterface)
            .addAnnotation(generatedAnnotation())
            .addKdoc(generateKDoc(field))
            .addProperty(theNameProperty(field))
            .addProperty(theRequiredProperty(field))
            .addProperty(theDescriptorProperty())
            .addFunction(theValueInFunction(field))
            .addFunction(theHasValueFunction(field))
            .addFunction(theSetValueFunction(field))
            .build()
    }

    /**
     * Builds the `setValue` function of [MessageField] implementation.
     */
    private fun theSetValueFunction(field: Field) =
        FunSpec.builder("setValue")
            .addModifiers(PUBLIC, OVERRIDE)
            .addParameter(
                "builder",
                validatingBuilderClassName
                    .parameterizedBy(messageFullClassName)
            )
            .addParameter("newValue", field.valueClassName(typeSystem))
            .addCode(field.generateSetValueCode(messageTypeName))
            .build()

    /**
     * Builds the `hasValue` function of [MessageField] implementation.
     */
    private fun theHasValueFunction(field: Field) =
        FunSpec.builder("hasValue")
            .addModifiers(PUBLIC, OVERRIDE)
            .returns(Boolean::class.asClassName())
            .addParameter("message", messageFullClassName)
            .addCode("return ${field.hasValueInvocation}")
            .build()

    /**
     * Builds the `valueIn` function of [MessageField] implementation.
     */
    private fun theValueInFunction(field: Field) =
        FunSpec.builder("valueIn")
            .addModifiers(PUBLIC, OVERRIDE)
            .returns(field.valueClassName(typeSystem))
            .addParameter("message", messageFullClassName)
            .addCode("return message.${field.getterName}()")
            .build()

    /**
     * Builds the `descriptor` property of [MessageField] implementation.
     */
    private fun theDescriptorProperty() =
        PropertySpec.builder(
            "descriptor",
            FieldDescriptor::class.asClassName(),
            PUBLIC, OVERRIDE
        ).initializer(
            CodeBlock.of(
                "%T.getDescriptor().findFieldByName(name)",
                messageFullClassName
            )
        ).build()

    /**
     * Builds the `required` property of [MessageField] implementation.
     */
    private fun theRequiredProperty(field: Field) =
        PropertySpec
            .builder("required", Boolean::class.asClassName(), PUBLIC, OVERRIDE)
            .initializer("${field.required}")
            .build()

    /**
     * Builds the `name` property of [MessageField] implementation.
     */
    private fun theNameProperty(field: Field) =
        PropertySpec
            .builder("name", String::class.asClassName(), PUBLIC, OVERRIDE)
            .initializer(CodeBlock.of("%S", field.name.value))
            .build()

    /**
     * Builds the KDoc section for the generated implementation of [MessageField].
     */
    private fun generateKDoc(field: Field) =
        CodeBlock.of(
            "A [%T] implementation that allows access to the `%L` field " +
                    "of the [%T] message at runtime.",
            MessageField::class.asClassName(),
            field.name.value,
            messageTypeName.fullClassName(typeSystem)
        )
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
    val setterCall = "$primarySetterName(newValue)"
    return if (isRepeated) {
        "$builderCast.clear${name.value.camelCase()}().$setterCall"
    } else {
        "$builderCast.$setterCall"
    }
}

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
    return ClassName(fileHeader.javaPackage(), typeName.simpleClassName)
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
