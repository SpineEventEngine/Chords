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

import com.google.protobuf.StringValue
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.asClassName
import io.spine.chords.runtime.MessageDef
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageFieldValue
import io.spine.chords.runtime.MessageOneof
import io.spine.protodata.Field
import io.spine.protodata.ProtoFileHeader
import io.spine.protodata.TypeName
import io.spine.protodata.find
import io.spine.protodata.type.TypeSystem
import io.spine.string.Indent
import io.spine.string.camelCase
import java.nio.file.Path

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
 * @param messageTypeName a [TypeName] of the message to generate the code for.
 * @param fields a collection of [Field]s to generate the code for.
 * @param typeSystem a [TypeSystem] to read external Proto messages.
 */
internal class MessageDefFileGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    private val typeSystem: TypeSystem
) {
    private val javaPackage = messageTypeName.javaPackage(typeSystem)

    private val messageFullClassName = ClassName(
        javaPackage,
        messageTypeName.simpleClassName
    )

    companion object {
        /**
         * Suffix of the generated [MessageDef] class name.
         */
        internal const val CLASS_NAME_SUFFIX = "Def"
    }

    /**
     * Returns a [Path] to the generated file that is relative
     * to the source root.
     */
    fun filePath(): Path {
        return Path.of(
            javaPackage.replace('.', '/'),
            messageTypeName.fileName(CLASS_NAME_SUFFIX)
        )
    }

    /**
     * Generates a content of the file.
     */
    fun fileContent(): String {
        return FileSpec.builder(messageFullClassName)
            .indent(Indent.defaultJavaIndent.toString())
            .also { fileBuilder ->

                MessageDefObjectGenerator(messageTypeName, fields, typeSystem)
                    .generateCode(fileBuilder)

                MessageFieldObjectGenerator(messageTypeName, fields, typeSystem)
                    .generateCode(fileBuilder)

                MessageOneofObjectGenerator(messageTypeName, fields, typeSystem)
                    .generateCode(fileBuilder)

                KClassPropertiesGenerator(messageTypeName, fields, typeSystem)
                    .generateCode(fileBuilder)

            }
            .build()
            .toString()
    }
}

/**
 * Returns a name of the generated file for this [TypeName].
 */
internal fun TypeName.fileName(suffix: String): String {
    return nestingTypeNameList.joinToString(
        "", "", "${simpleName}$suffix.kt"
    )
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
internal val messageFieldValueTypeAlias: ClassName
    get() = MessageFieldValue::class.asClassName()

/**
 * Returns a Java package for the [TypeName].
 */
internal fun TypeName.javaPackage(typeSystem: TypeSystem): String {
    val messageToHeader = typeSystem.findMessage(this)
    checkNotNull(messageToHeader) {
        "Cannot determine file header for TypeName `$this`"
    }
    return messageToHeader.second.javaPackage
}

/**
 * Returns a fully qualified [ClassName] for the [TypeName].
 */
internal fun TypeName.fullClassName(typeSystem: TypeSystem): ClassName {
    return ClassName(javaPackage(typeSystem), simpleClassName)
}

/**
 * Converts a Proto field name to "property" name,
 * e.g. "domain_name" -> "domainName".
 */
internal val String.propertyName
    get() = camelCase().replaceFirstChar { it.lowercase() }

/**
 * Returns a Java package declared in [ProtoFileHeader].
 */
internal val ProtoFileHeader.javaPackage: String
    get() {
        val optionName = "java_package"
        val option = optionList.find(optionName, StringValue::class.java)
        checkNotNull(option) {
            "Cannot find option `$optionName` in file header `${this}`."
        }
        return option.value
    }

/**
 * Generates a simple class name for the implementation of `MessageField`.
 */
internal fun TypeName.messageFieldClassName(fieldName: String): String {
    return generateClassName(fieldName, "Field")
}

/**
 * Generates a simple class name for the implementation of `MessageOneof`.
 */
internal fun TypeName.messageOneofClassName(fieldName: String): String {
    return generateClassName(fieldName, "Oneof")
}

/**
 * Generates a simple class name for the `fieldName` provided.
 */
internal fun TypeName.generateClassName(suffix: String): String {
    return generateClassName("", suffix)
}

/**
 * Generates a simple class name for the [fieldName] provided.
 */
internal fun TypeName.generateClassName(
    fieldName: String,
    classNameSuffix: String
): String {
    return nestingTypeNameList.joinToString(
        "",
        "",
        "${simpleName}${fieldName.camelCase()}$classNameSuffix"
    )
}

/**
 * Returns simple class name for the [TypeName].
 */
internal val TypeName.simpleClassName: String
    get() = if (nestingTypeNameList.isNotEmpty())
        nestingTypeNameList.joinToString(
            ".",
            "",
            ".$simpleName"
        )
    else
        simpleName
