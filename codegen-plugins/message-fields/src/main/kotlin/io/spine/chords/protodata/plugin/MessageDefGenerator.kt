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
import java.nio.file.Path
import kotlin.reflect.KClass

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
    typeSystem: TypeSystem
) : FileGenerator(messageTypeName, fields, typeSystem) {

    private val fileNameSuffix = "Def"

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
        return messageTypeName.filePath(fileNameSuffix)
    }

    override fun buildFileContent(fileBuilder: FileSpec.Builder) {
        val messageDefObjectBuilder = TypeSpec
            .objectBuilder(messageTypeName.generatedClassName(fileNameSuffix))
            .addSuperinterface(
                messageDefClassName.parameterizedBy(messageTypeName.fullClassName)
            )
        generateMessageDefFieldProperties(messageDefObjectBuilder)
        generateCollectionProperties(messageDefObjectBuilder)
        fileBuilder.addType(messageDefObjectBuilder.build())

        generateFieldsObjectDeclarations(fileBuilder)
        generateKClassProperties(fileBuilder)
    }

    private fun generateCollectionProperties(messageDefObjectBuilder: TypeSpec.Builder) {
        generateMessageDefCollectionProperty(
            messageDefObjectBuilder,
            "fields",
            Collection::class.asClassName().parameterizedBy(
                messageFieldClassName.parameterizedBy(
                    messageTypeName.fullClassName,
                    WildcardTypeName.producerOf(messageFieldValueTypeAlias)
                )
            ),
            fields.map { it.name.value }
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

    private fun generateMessageDefFieldProperties(messageDefObjectBuilder: TypeSpec.Builder) {
        fields.map { it.name.value }.forEach { fieldName ->
            messageDefObjectBuilder.addProperty(
                generateMessageDefFieldProperty(
                    fieldName,
                    messageTypeName.messageFieldClassName(fieldName)
                )
            )
        }
        oneofFieldMap.forEach { oneofNameToFields ->
            messageDefObjectBuilder.addProperty(
                generateMessageDefFieldProperty(
                    oneofNameToFields.key,
                    messageTypeName.messageOneofClassName(oneofNameToFields.key)
                )
            )
        }
    }

    private fun generateMessageDefFieldProperty(
        fieldName: String,
        simpleClassName: String
    ): PropertySpec {
        val fullClassName = ClassName(messageTypeName.javaPackage, simpleClassName)
        return PropertySpec.builder(fieldName.propertyName, fullClassName, PUBLIC)
            .initializer("$simpleClassName()")
            .build()
    }

    private fun generateKClassProperties(fileBuilder: FileSpec.Builder) {
        fields.map { it.name.value }.forEach { fieldName ->
            fileBuilder.addProperty(
                buildKClassPropertyDeclaration(
                    fieldName,
                    messageTypeName.messageFieldClassName(fieldName)
                )
            )
        }
        oneofFieldMap.forEach { oneofNameToFields ->
            fileBuilder.addProperty(
                buildKClassPropertyDeclaration(
                    oneofNameToFields.key,
                    messageTypeName.messageOneofClassName(oneofNameToFields.key)
                )
            )
        }
    }

    private fun generateFieldsObjectDeclarations(fileBuilder: FileSpec.Builder) {
        fields.forEach { field ->
            fileBuilder.addType(
                buildMessageFieldClass(field)
            )
        }
        oneofFieldMap.forEach { oneofNameToFields ->
            fileBuilder.addType(
                buildMessageOneofClass(
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
    private fun buildMessageFieldClass(field: Field): TypeSpec {
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
    private fun buildMessageOneofClass(
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
     * Builds a property declaration for the given field
     * that looks like the following:
     * ```
     *     public val KClass<RegistrationInfo>.domainName:
     *         RegistrationInfoDomainName get() = RegistrationInfoDomainName()
     * ```
     */
    private fun buildKClassPropertyDeclaration(
        fieldName: String,
        simpleClassName: String
    ): PropertySpec {
        val propertyType = ClassName(
            messageTypeName.javaPackage,
            simpleClassName
        )
        val receiverType = KClass::class.asClassName()
            .parameterizedBy(messageTypeName.fullClassName)
        val getterCode = FunSpec.getterBuilder()
            .addCode("return $simpleClassName()")
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
