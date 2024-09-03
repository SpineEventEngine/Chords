package io.spine.chords.protodata.plugin

import com.squareup.kotlinpoet.FileSpec
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.type.TypeSystem

internal class MessageTypeGenerator(
    messageTypeName: TypeName,
    fields: Iterable<Field>,
    typeSystem: TypeSystem
) : FileGenerator(messageTypeName, fields, typeSystem) {

    override val fileSuffix: String get() = "Type"

    @Suppress("UNUSED_PARAMETER")
    override fun generateContent(fileBuilder: FileSpec.Builder) {
        // Todo: implement
    }
}
