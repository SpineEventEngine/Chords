package io.spine.chords.protodata.plugin

import com.squareup.kotlinpoet.FileSpec
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.type.TypeSystem
import io.spine.string.Indent
import java.nio.file.Path

public class MessageTypeGenerator(
    messageTypeName: TypeName,
    fields: Iterable<Field>,
    typeSystem: TypeSystem
) : FieldsFileGenerator(messageTypeName, fields, typeSystem) {

    /**
     * Returns a [Path] to the generated file that is relative
     * to the source root.
     */
    override fun filePath(): Path {
        return Path.of(
            javaPackage.replace('.', '/'),
            fileName(messageTypeName, "Type")
        )
    }

    /**
     * Builds content of the file with `MessageType` implementation.
     */
    override fun fileContent(): String {
        FileSpec.builder(messageClass)
            .indent(Indent.defaultJavaIndent.toString())
            .let { fileBuilder ->
                return fileBuilder.build().toString()
            }
    }
}
