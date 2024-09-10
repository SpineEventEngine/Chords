package io.spine.chords.protodata.plugin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
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
 * @param fields a collection of [Field]s  to generate the code for.
 * @param typeSystem a [TypeSystem] to read external Proto messages.
 */
internal class MessageDefGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    typeSystem: TypeSystem
) : FileGenerator(messageTypeName, fields, typeSystem) {

    override val fileNameSuffix: String get() = "Def"

    override fun buildFileContent(fileBuilder: FileSpec.Builder) {
        val messageTypeClassName = messageTypeName.generatedClassName(fileNameSuffix)
        val superInterface = messageDefClassName
            .parameterizedBy(messageTypeName.fullClassName)
        val classBuilder = TypeSpec.objectBuilder(messageTypeClassName)
            .addSuperinterface(superInterface)

        val oneofFieldMap = fields.onEach { field ->
            val simpleClassName = messageTypeName.messageFieldClassName(field.name.value)
            val fullClassName = ClassName(messageTypeName.javaPackage, simpleClassName)
            val propName = field.name.value.propertyName
            classBuilder.addProperty(
                PropertySpec.builder(propName, fullClassName, PUBLIC)
                    .initializer("$simpleClassName()")
                    .build()
            )
        }.filter { field ->
            field.isPartOfOneof
        }.groupBy { oneofField ->
            oneofField.oneofName.value
        }.onEach { oneofNameToFields ->
            val simpleClassName = messageTypeName.messageOneofClassName(oneofNameToFields.key)
            val fullClassName = ClassName(messageTypeName.javaPackage, simpleClassName)
            val propName = oneofNameToFields.key.propertyName
            classBuilder.addProperty(
                PropertySpec.builder(propName, fullClassName, PUBLIC)
                    .initializer("$simpleClassName()")
                    .build()
            )
        }
        val fieldsReturnType = Collection::class.asClassName().parameterizedBy(
            messageFieldClassName
                .parameterizedBy(
                    messageTypeName.fullClassName,
                    WildcardTypeName.producerOf(messageFieldValueType)
                )
        )
        val oneofsReturnType = Collection::class.asClassName().parameterizedBy(
            messageOneofClassName
                .parameterizedBy(messageTypeName.fullClassName)
        )
        classBuilder
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
        fileBuilder.addType(classBuilder.build())

        generateClasses(fileBuilder)
        generateKClassProperties(fileBuilder)
    }

    private fun generateKClassProperties(fileBuilder: FileSpec.Builder) {
        fields.onEach { field ->
            fileBuilder.addProperty(
                buildFieldProperty(field.name.value)
            )
        }.filter { field ->
            field.isPartOfOneof
        }.groupBy { oneofField ->
            oneofField.oneofName.value
        }.forEach { oneofNameToFields ->
            fileBuilder.addProperty(
                buildOneofProperty(oneofNameToFields.key)
            )
        }
    }

    private fun generateClasses(fileBuilder: FileSpec.Builder) {
        fields.onEach { field ->
            fileBuilder.addType(
                buildMessageFieldClass(field)
            )
        }.filter { field ->
            field.isPartOfOneof
        }.groupBy { oneofField ->
            oneofField.oneofName.value
        }.forEach { oneofNameToFields ->
            fileBuilder.addType(
                buildMessageOneofClass(
                    oneofNameToFields.key,
                    oneofNameToFields.value
                )
            )
        }
    }
}
