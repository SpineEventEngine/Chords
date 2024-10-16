/*
 * Copyright 2024, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.chords.codegen.plugins

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.PropertySpec
import io.spine.chords.runtime.MessageDef
import io.spine.protodata.ast.TypeName
import io.spine.protodata.type.TypeSystem

/**
 * Implementation of [FileFragmentGenerator] that generates extension
 * for a message builder class that returns the corresponding
 * implementation of [MessageDef] interface.
 *
 * @param messageTypeName The [TypeName] of the message to generate the code for.
 * @param typeSystem The [TypeSystem] to read external Proto messages.
 */
internal class BuilderExtensionGenerator(
    private val messageTypeName: TypeName,
    private val typeSystem: TypeSystem
) : FileFragmentGenerator {

    /**
     * Generates the property `messageDef` for a message builder class
     * that returns the corresponding implementation of [MessageDef].
     *
     * The generated property looks like the following:
     * ```
     *     public val IpAddress.Builder.messageDef: IpAddressDef
     *         get() = IpAddressDef
     * ```
     */
    override fun generateCode(fileBuilder: FileSpec.Builder) {
        val messageClass = messageTypeName.fullClassName(typeSystem)
        val messageDefSimpleName = messageTypeName.messageDefClassName()
        val messageDefClass = ClassName(
            messageTypeName.javaPackage(typeSystem),
            messageDefSimpleName
        )
        fileBuilder.addProperty(
            PropertySpec.builder("messageDef", messageDefClass, PUBLIC)
                .receiver(messageClass.nestedClass("Builder"))
                .getter(
                    FunSpec.getterBuilder()
                        .addCode("return $messageDefSimpleName")
                        .build()
                )
                .build()
        )
    }
}
