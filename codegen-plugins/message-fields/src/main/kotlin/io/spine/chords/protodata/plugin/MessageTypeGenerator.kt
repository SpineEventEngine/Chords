package io.spine.chords.protodata.plugin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.isPartOfOneof
import io.spine.protodata.type.TypeSystem

internal class MessageTypeGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    private val typeSystem: TypeSystem
) : FileGenerator(messageTypeName, fields, typeSystem) {

    override val fileNameSuffix: String get() = "Type"

    override fun buildFileContent(fileBuilder: FileSpec.Builder) {
        val messageTypeClassName = messageTypeName.generatedClassName("Type")
        val classBuilder = TypeSpec.classBuilder(messageTypeClassName)
        val objectBuilder = TypeSpec.objectBuilder("Fields")

        fields.onEach { field ->
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
        }.forEach { oneofNameToFields ->
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

        fileBuilder.addType(
            classBuilder
                .addType(objectBuilder.build())
                .build()
        )
    }
}
