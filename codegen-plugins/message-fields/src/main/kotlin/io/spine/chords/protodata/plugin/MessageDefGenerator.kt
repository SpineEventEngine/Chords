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
