package io.spine.chords.protodata.plugin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.isPartOfOneof
import io.spine.protodata.type.TypeSystem

internal class MessageTypeGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    typeSystem: TypeSystem
) : FileGenerator(messageTypeName, fields, typeSystem) {

    override val fileNameSuffix: String get() = "Type"

    override fun buildFileContent(fileBuilder: FileSpec.Builder) {
        val messageTypeClassName = messageTypeName.generatedClassName("Type")
        val classBuilder = TypeSpec.classBuilder(messageTypeClassName)
        val objectBuilder = TypeSpec.companionObjectBuilder("Fields")

        val oneofFieldMap = fields.onEach { field ->
            val fullClassName = ClassName(
                messageTypeName.javaPackage,
                messageTypeName.messageFieldClassName(field.name.value)
            )
            val propName = field.name.value.propertyName
            objectBuilder.addProperty(
                PropertySpec.builder(propName, fullClassName, PUBLIC)
                    .initializer("$fullClassName()")
                    .build()
            )
        }.filter { field ->
            field.isPartOfOneof
        }.groupBy { oneofField ->
            oneofField.oneofName.value
        }.onEach { oneofNameToFields ->
            val fullClassName = ClassName(
                messageTypeName.javaPackage,
                messageTypeName.messageOneofClassName(oneofNameToFields.key)
            )
            val propName = oneofNameToFields.key.propertyName
            objectBuilder.addProperty(
                PropertySpec.builder(propName, fullClassName, PUBLIC)
                    .initializer("$fullClassName()")
                    .build()
            )
        }

        val fieldsReturnType = Collection::class.asClassName()
            .parameterizedBy(
                messageFieldClassName
                    .parameterizedBy(messageTypeName.fullClassName, STAR)
            )
        val oneofsReturnType = Collection::class.asClassName()
            .parameterizedBy(
                messageOneofClassName
                    .parameterizedBy(messageTypeName.fullClassName)
            )

        fileBuilder.addType(
            classBuilder
                .addType(objectBuilder.build())
                .addProperty(
                    PropertySpec
                        .builder("fields", fieldsReturnType, PUBLIC)
                        .initializer(fieldListInitializer(fields.map { it.name.value }))
                        .build()
                )
                .addProperty(
                    PropertySpec
                        .builder("oneofs", oneofsReturnType, PUBLIC)
                        .initializer(fieldListInitializer(oneofFieldMap.keys))
                        .build()
                )
                .build()
        )
    }
}
