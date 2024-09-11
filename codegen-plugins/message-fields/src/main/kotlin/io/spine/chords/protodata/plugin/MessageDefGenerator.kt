package io.spine.chords.protodata.plugin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.isPartOfOneof
import io.spine.protodata.type.TypeSystem
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

    /**
     * Contains the fields which are parts of some `oneof` grouped by its name.
     */
    private val oneofFieldMap = fields.filter { field ->
        field.isPartOfOneof
    }.groupBy { oneofField ->
        oneofField.oneofName.value
    }

    override val fileNameSuffix: String get() = "Def"

    override fun buildFileContent(fileBuilder: FileSpec.Builder) {
        val messageDefObjectBuilder = TypeSpec
            .objectBuilder(messageTypeName.generatedClassName(fileNameSuffix))
            .addSuperinterface(
                messageDefClassName.parameterizedBy(messageTypeName.fullClassName)
            )

        generateFieldProperties(messageDefObjectBuilder)
        generateCollectionProperties(messageDefObjectBuilder)

        fileBuilder.addType(messageDefObjectBuilder.build())

        generateObjectDeclarations(fileBuilder)
        generateKClassProperties(fileBuilder)
    }

    private fun generateCollectionProperties(
        messageDefObjectBuilder: TypeSpec.Builder
    ) {
        val fieldsReturnType = Collection::class.asClassName().parameterizedBy(
            messageFieldClassName.parameterizedBy(
                messageTypeName.fullClassName,
                WildcardTypeName.producerOf(messageFieldValueTypeAlias)
            )
        )
        val oneofsReturnType = Collection::class.asClassName().parameterizedBy(
            messageOneofClassName.parameterizedBy(messageTypeName.fullClassName)
        )

        messageDefObjectBuilder
            .addProperty(
                PropertySpec.builder("fields", fieldsReturnType, PUBLIC, OVERRIDE)
                    .initializer(fieldListInitializer(fields.map { it.name.value }))
                    .build()
            )
            .addProperty(
                PropertySpec.builder("oneofs", oneofsReturnType, PUBLIC, OVERRIDE)
                    .initializer(fieldListInitializer(oneofFieldMap.keys))
                    .build()
            )
    }

    private fun generateFieldProperties(messageDefObjectBuilder: TypeSpec.Builder) {
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

    private fun generateObjectDeclarations(fileBuilder: FileSpec.Builder) {
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
