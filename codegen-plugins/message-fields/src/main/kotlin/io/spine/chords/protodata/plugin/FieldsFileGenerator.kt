/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.protodata.plugin

import com.google.protobuf.BoolValue
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
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageOneof
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
import io.spine.protodata.ProtoFileHeader
import io.spine.protodata.Type
import io.spine.protodata.TypeName
import io.spine.protodata.find
import io.spine.protodata.isEnum
import io.spine.protodata.isPartOfOneof
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
 * Generates a separate Kotlin file that contains [MessageField]
 * and [MessageOneof] implementations for the fields of a Proto message.
 *
 * Below is generated [MessageField] implementation for the field `domain_name`
 * of the `RegistrationInfo` message:
 *
 * ```
 *     public val KClass<RegistrationInfo>.domainName:
 *         RegistrationInfo_DomainName
 *         get() {
 *             return RegistrationInfo_DomainName()
 *         }
 *
 *     public class RegistrationInfo_DomainName:
 *         MessageField<
 *             RegistrationInfo,
 *             InternetDomain> {
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
 *
 * Generated implementation of [MessageOneof] for the `value` field
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
 *     val domain = RegistrationInfo::class.domainName.valueIn(registrationInfo)
 *
 *     // Set field value for the message builder provided.
 *     RegistrationInfo::class.domainName.setValue(
 *         registrationInfoBuilder,
 *         domain
 *     )
 * ```
 *
 * @param messageTypeName a [TypeName] of the message to generate the code for.
 * @param fields a collection of [Field]s  to generate the code for.
 * @param typeSystem a [TypeSystem] to read external Proto messages.
 */
public class FieldsFileGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    private val typeSystem: TypeSystem
) {
    /**
     * Package and class name of the `io.spine.protobuf.ValidatingBuilder`.
     *
     * It is not in the classpath and cannot be used directly.
     */
    private companion object {
        const val VALIDATING_BUILDER_PACKAGE = "io.spine.protobuf"
        const val VALIDATING_BUILDER_CLASS = "ValidatingBuilder"
    }

    /**
     * A Java package of the Proto message.
     */
    private val javaPackage = messageTypeName.javaPackage

    /**
     * A [ClassName] of the Proto message.
     */
    private val messageClass = messageTypeName.fullClassName

    /**
     * Returns a [Path] to the generated file that is relative
     * to the source root.
     */
    internal fun filePath(): Path {
        return Path.of(
            javaPackage.replace('.', '/'),
            fileName(messageTypeName)
        )
    }

    /**
     * Builds content of the file with [MessageField]
     * and [MessageOneof] implementations.
     */
    internal fun fileContent(): String {
        FileSpec.builder(messageClass)
            .indent(Indent.defaultJavaIndent.toString())
            .let { fileBuilder ->
                fields.onEach { field ->
                    fileBuilder.addProperty(
                        buildPropertyDeclaration(field.name.value)
                    )
                }.onEach { field ->
                    fileBuilder.addType(
                        buildMessageFieldClass(field)
                    )
                }.filter { field ->
                    field.isPartOfOneof
                }.groupBy { oneofField ->
                    oneofField.oneofName.value
                }.forEach { oneofNameToFields ->
                    fileBuilder.addProperty(
                        buildPropertyDeclaration(oneofNameToFields.key)
                    )
                    fileBuilder.addType(
                        buildMessageOneofClass(
                            oneofNameToFields.key,
                            oneofNameToFields.value
                        )
                    )
                }
                return fileBuilder.build().toString()
            }
    }

    /**
     * Generates a property declaration for the given field
     * that looks like the following:
     *
     * ```
     *     public val KClass<RegistrationInfo>.domainName:
     *         RegistrationInfoDomainName get() = RegistrationInfoDomainName()
     * ```
     */
    private fun buildPropertyDeclaration(fieldName: String): PropertySpec {
        generatedClassName(messageTypeName, fieldName).let { shortClassName ->
            val propertyName = fieldName.propertyName
            val propertyType = ClassName(javaPackage, shortClassName)
            val receiverType = KClass::class.asClassName()
                .parameterizedBy(messageClass)
            val getterCode = FunSpec.getterBuilder()
                .addCode("return $shortClassName()")
                .build()

            return PropertySpec.builder(propertyName, propertyType)
                .receiver(receiverType)
                .getter(getterCode)
                .build()
        }
    }

    /**
     * Generates implementation of [MessageOneof] for the given `oneof` field
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
    private fun buildMessageOneofClass(
        oneofName: String,
        oneofFields: List<Field>
    ): TypeSpec {
        val messageFieldType = MessageField::class.asClassName()
            .parameterizedBy(messageClass, STAR)
        val fieldMapType = Map::class.asClassName().parameterizedBy(
            Int::class.asClassName(),
            messageFieldType
        )
        val stringType = String::class.asClassName()
        val fieldsType = Collection::class.asClassName()
            .parameterizedBy(messageFieldType)
        val superType = MessageOneof::class.asClassName().parameterizedBy(messageClass)
        val oneofPropName = oneofName.propertyName

        return TypeSpec.classBuilder(generatedClassName(messageTypeName, oneofName))
            .addSuperinterface(superType)
            .addProperty(
                PropertySpec.builder("fieldMap", fieldMapType, PRIVATE)
                    .initializer(fieldMapInitializer(messageTypeName, oneofFields))
                    .build()
            ).addProperty(
                PropertySpec.builder("name", stringType, PUBLIC, OVERRIDE)
                    .initializer("\"$oneofName\"")
                    .build()
            ).addProperty(
                PropertySpec.builder("fields", fieldsType, PUBLIC, OVERRIDE)
                    .initializer("fieldMap.values")
                    .build()
            ).addFunction(
                FunSpec.builder("selectedField")
                    .addModifiers(PUBLIC, OVERRIDE)
                    .returns(messageFieldType.copy(nullable = true))
                    .addParameter("message", messageClass)
                    .addCode("return fieldMap[message.${oneofPropName}Case.number]")
                    .build()
            ).build()
    }

    /**
     * Generates implementation of [MessageField] for the given field.
     *
     * The generated code looks like the following:
     *
     * ```
     *     public class RegistrationInfo_DomainName:
     *         MessageField<
     *             RegistrationInfo,
     *             InternetDomain> {
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
    private fun buildMessageFieldClass(field: Field): TypeSpec {
        val className = generatedClassName(messageTypeName, field.name.value)
        val superType = MessageField::class.asClassName()
            .parameterizedBy(messageClass, field.valueClassName)
        val stringType = String::class.asClassName()
        val boolType = Boolean::class.asClassName()
        val builderType = ClassName(
            VALIDATING_BUILDER_PACKAGE,
            VALIDATING_BUILDER_CLASS
        ).parameterizedBy(messageClass)

        return TypeSpec.classBuilder(className)
            .addSuperinterface(superType)
            .addProperty(
                PropertySpec.builder("name", stringType, PUBLIC, OVERRIDE)
                    .initializer("\"${field.name.value}\"")
                    .build()
            ).addProperty(
                PropertySpec.builder("required", boolType, PUBLIC, OVERRIDE)
                    .initializer("${field.required}")
                    .build()
            ).addFunction(
                FunSpec.builder("valueIn")
                    .addModifiers(PUBLIC, OVERRIDE)
                    .returns(field.valueClassName)
                    .addParameter("message", messageClass)
                    .addCode("return message.${field.getterInvocation}")
                    .build()
            ).addFunction(
                FunSpec.builder("hasValue")
                    .addModifiers(PUBLIC, OVERRIDE)
                    .returns(boolType)
                    .addParameter("message", messageClass)
                    .addCode("return ${field.hasValueInvocation()}")
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
     * Returns [ClassName] for the [Type].
     */
    private val Type.className
        get() = if (isPrimitive)
            primitiveClassName()
        else
            nonPrimitiveClassName()

    /**
     * Returns [ClassName] for the [Type] that is not primitive.
     */
    private fun Type.nonPrimitiveClassName(): ClassName {
        check(!isPrimitive)
        val fileHeader = typeSystem.findHeader(this)
        checkNotNull(fileHeader) {
            "Cannot determine file header for type `$this`"
        }
        return ClassName(fileHeader.javaPackage, typeName.simpleClassName)
    }

    /**
     * Returns a fully qualified [ClassName] for the [TypeName].
     */
    private val TypeName.fullClassName
        get() = ClassName(javaPackage, simpleClassName)

    /**
     * Returns a Java package for the [TypeName].
     */
    private val TypeName.javaPackage: String
        get() {
            val messageToHeader = typeSystem.findMessage(this)
            checkNotNull(messageToHeader) {
                "Cannot determine file header for TypeName `$this`"
            }
            return messageToHeader.second.javaPackage
        }

    /**
     * Returns [ClassName] of the value of the [Field].
     */
    private val Field.valueClassName
        get() = if (isRepeated)
            Iterable::class.asClassName()
                .parameterizedBy(type.className)
        else type.className
}

/**
 * Converts a Proto field name to "property" name,
 * e.g. "domain_name" -> "domainName".
 */
private val String.propertyName
    get() = camelCase().replaceFirstChar { it.lowercase() }

/**
 * Returns a piece of code that sets a new value for the [Field].
 */
private fun Field.generateSetterCode(messageClass: TypeName): String {
    val messageShortClassName = messageClass.simpleClassName
    val builderCast = "(builder as $messageShortClassName.Builder)"
    val setterCall = "$setterInvocation(newValue)"
    return if (isRepeated) {
        "$builderCast.${collectionJanitor}.$setterCall"
    } else {
        "$builderCast.$setterCall"
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
 * Indicates if the `required` option is applied to the field.
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
    else name.value.propertyName

/**
 * Returns a "hasValue" invocation code for the [Field].
 *
 * The generated code returns `true` if a field is repeated, is an enum,
 * or a primitive. This is required to be compatible with the design approach
 * of `protoc`-generated Java code. There, `hasValue` methods are not being
 * generated for the fields of such kinds.
 */
private fun Field.hasValueInvocation(): String {
    return if (isRepeated || type.isEnum || type.isPrimitive) "true"
    else "message.has${name.value.camelCase()}()"
}

/**
 * Returns a "setter" invocation code for the [Field].
 */
private val Field.setterInvocation: String
    get() = if (isRepeated)
        "addAll${name.value.camelCase()}"
    else "set${name.value.camelCase()}"


/**
 * Returns invocation code of the method that clears the value
 * of the repeated [Field].
 */
private val Field.collectionJanitor
    get() = if (isRepeated)
        "clear${name.value.camelCase()}()"
    else error(
        """
            Method `collectionJanitor` may be invoked only
            for the instances that refer to the `repeated` fields.
        """
    )

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
 * Returns simple class name for the [TypeName].
 */
private val TypeName.simpleClassName: String
    get() = if (nestingTypeNameList.isNotEmpty())
        nestingTypeNameList.joinToString(
            ".",
            "",
            ".$simpleName"
        ) else simpleName

/**
 * Returns name of the generated file.
 */
private fun fileName(messageTypeName: TypeName): String {
    return messageTypeName.nestingTypeNameList.joinToString(
        "", "", "${messageTypeName.simpleName}Fields.kt"
    )
}

/**
 * Generates a simple class name for the implementation of [MessageField]
 * or [MessageOneof].
 */
private fun generatedClassName(typeName: TypeName, fieldName: String): String {
    return typeName.nestingTypeNameList.joinToString(
        "",
        "",
        "${typeName.simpleName}${fieldName.camelCase()}Field"
    )
}

/**
 * Generates initialization code for the `fieldMap` property
 * of the [MessageOneof] implementation.
 */
private fun fieldMapInitializer(
    typeName: TypeName,
    fields: List<Field>
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
 * Obtains a Kotlin class which corresponds to the [PrimitiveType].
 */
public fun PrimitiveType.primitiveClass(): KClass<*> =
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
