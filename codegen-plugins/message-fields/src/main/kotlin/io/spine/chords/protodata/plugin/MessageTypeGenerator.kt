package io.spine.chords.protodata.plugin

import com.squareup.kotlinpoet.FileSpec
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.type.TypeSystem

internal class MessageTypeGenerator(
    private val messageTypeName: TypeName,
    private val fields: Iterable<Field>,
    private val typeSystem: TypeSystem
) : FileGenerator(messageTypeName, fields, typeSystem) {

    override val fileNameSuffix: String get() = "Type"

    override fun generateContent(fileBuilder: FileSpec.Builder) {
        // Todo: implement
    }
}
