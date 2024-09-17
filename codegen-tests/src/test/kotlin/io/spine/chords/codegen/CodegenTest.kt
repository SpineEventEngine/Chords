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

package io.spine.chords.codegen

import com.google.protobuf.Message
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.spine.chords.codegen.command.TestCommand.EnumType
import io.spine.chords.codegen.command.TestCommandDef
import io.spine.chords.codegen.command.TestCommandOneOfTypeDef
import io.spine.chords.codegen.command.TestCommandPrimitivesDef
import io.spine.chords.runtime.MessageDef
import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageFieldValue
import io.spine.chords.runtime.MessageOneof
import io.spine.chords.runtime.messageDef
import io.spine.protobuf.ValidatingBuilder
import java.util.stream.Stream
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Checks various use-cases on code generation for [MessageField]
 * and [MessageOneof] implementations.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class `CodegenPlugin should` {

    /**
     * Checks generated [MessageField] implementations.
     */
    @ParameterizedTest
    @MethodSource("messageFieldTestData")
    @Suppress("LongParameterList")
    fun <T : Message, V : MessageFieldValue>
            `generate 'MessageField' implementations`(
        field: MessageField<T, V>,
        builder: ValidatingBuilder<T>,
        fieldValues: Iterable<V>,
        protoFieldName: String,
        fieldIsRequired: Boolean,
        initialHasValue: Boolean
    ) {
        withClue("Wrong Proto field name was generated.") {
            field.name shouldBe protoFieldName
        }
        withClue("Wrong value of `required` property was generated.") {
            field.required shouldBe fieldIsRequired
        }
        withClue("Wrong result of `hasValue()` if value is not set.") {
            field.hasValue(builder.build()) shouldBe initialHasValue
        }
        fieldValues.forEach { newValue ->
            field.setValue(builder, newValue)
            val message = builder.build()
            withClue("Wrong result of `hasValue()` when new value is set.") {
                field.hasValue(message) shouldBe true
            }
            withClue("`valueIn()` returns the wrong value.") {
                field.valueIn(message) shouldBe newValue
            }
        }
    }

    /**
     * Checks generated [MessageOneof] implementations.
     */
    @ParameterizedTest
    @MethodSource("messageOneofTestData")
    fun <T : Message, V>
            `generate 'MessageOneof' implementations`(
        oneof: MessageOneof<T>,
        message: T,
        selectedFieldValue: V,
        protoOneofName: String,
        fieldsCount: Int
    ) {
        withClue("Property `name` was not correctly generated.") {
            oneof.name shouldBe protoOneofName
        }
        withClue("Property `fields` was not correctly generated.") {
            oneof.fields.size shouldBe fieldsCount
        }
        withClue("Method `selectedField()` was not correctly generated.") {
            oneof.selectedField(message)
                ?.valueIn(message) shouldBe selectedFieldValue
        }
    }

    /**
     * Checks generated [MessageDef] implementations.
     */
    @ParameterizedTest
    @MethodSource("messageDefTestData")
    fun <T : Message> `generate 'MessageDef' implementation`(
        messageDef: MessageDef<T>,
        builder: ValidatingBuilder<T>,
        fieldCount: Int,
        oneofCount: Int,
    ) {
        withClue("The `fields` property was not correctly generated.") {
            messageDef.fields.size shouldBe fieldCount
        }
        withClue("The `oneofs` property was not correctly generated.") {
            messageDef.oneofs.size shouldBe oneofCount
        }
        withClue("The `messageDef` builder extension was not correctly generated.") {
            builder.messageDef() shouldBe messageDef
        }
    }

    /**
     * Provides test data on various use-cases supported
     * in code generation of [MessageDef] implementations.
     */
    @Suppress("unused")
    private fun messageDefTestData(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(TestCommandDef, testCommandBuilder(), 10, 1),
            Arguments.of(TestCommandOneOfTypeDef, oneOfTypeBuilder(), 3, 1),
            Arguments.of(TestCommandPrimitivesDef, primitivesBuilder(), 7, 0),
            Arguments.of(ExternalTypeDef, externalTypeBuilder(), 1, 0)
        )
    }

    /**
     * Provides test data on various use-cases supported
     * in code generation of [MessageField] implementations.
     */
    @Suppress("unused")
    private fun messageOneofTestData(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                TestCommandDef.oneOfPlainField,
                testCommandBuilder().setOneOfOption1(true).build(),
                true, "one_of_plain_field", 3
            ),
            Arguments.of(
                TestCommandOneOfTypeDef.value,
                oneOfTypeBuilder().setOption3(100).build(),
                100, "value", 3
            )
        )
    }

    /**
     * Provides test data on various use-cases supported
     * in code generation of [MessageField] implementations.
     */
    @Suppress("unused", "LongMethod")
    private fun messageFieldTestData(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                TestCommandDef.time, testCommandBuilder(),
                listOf(timestamp(1024), timestamp(2048)),
                "time", true, false
            ),
            Arguments.of(
                TestCommandDef.users, testCommandBuilder(),
                listOf(
                    listOf(userId("user1"), userId("user2")),
                    listOf(userId("user3"), userId("user4"))
                ),
                "users", false, true
            ),
            Arguments.of(
                TestCommandDef.domain, testCommandBuilder(),
                listOf(domain("spine.io"), domain("onedam.com")),
                "domain", false, false
            ),
            Arguments.of(
                TestCommandDef.enumField, testCommandBuilder(),
                listOf(
                    EnumType.ET_UNDEFINED,
                    EnumType.ENUM_FIELD_1,
                    EnumType.ENUM_FIELD_2
                ),
                "enum_field", false, true
            ),
            Arguments.of(
                TestCommandDef.oneOfOption1, testCommandBuilder(),
                listOf(true, false), "one_of_option_1", false, true
            ),
            Arguments.of(
                TestCommandDef.oneOfOption2, testCommandBuilder(),
                listOf("string", "value"), "one_of_option_2", false, true
            ),
            Arguments.of(
                TestCommandDef.oneOfOption3, testCommandBuilder(),
                listOf(1, 0, -1), "one_of_option_3", false, true
            ),
            Arguments.of(
                TestCommandDef.oneOfTypeField, testCommandBuilder(),
                listOf(
                    oneOfTypeBuilder().setOption1(true).build(),
                    oneOfTypeBuilder().setOption2("text").build(),
                    oneOfTypeBuilder().setOption3(0).build()
                ),
                "one_of_type_field", false, false
            ),
            Arguments.of(
                TestCommandDef.primitives, testCommandBuilder(),
                listOf(primitives(true), primitives(false)),
                "primitives", true, false
            ),
            Arguments.of(
                TestCommandPrimitivesDef.bool, primitivesBuilder(),
                listOf(true, false), "bool", false, true
            ),
            Arguments.of(
                TestCommandPrimitivesDef.text, primitivesBuilder(),
                listOf("abracadabra", "barbara"), "text", true, true
            ),
            Arguments.of(
                TestCommandPrimitivesDef.numInt32, primitivesBuilder(),
                listOf(
                    listOf(1, 0, -1),
                    listOf(Int.MAX_VALUE, Int.MIN_VALUE)
                ),
                "num_int_32", false, true
            ),
            Arguments.of(
                TestCommandPrimitivesDef.numInt64, primitivesBuilder(),
                listOf(Long.MAX_VALUE, Long.MIN_VALUE),
                "num_int_64", false, true
            ),
            Arguments.of(
                TestCommandPrimitivesDef.numFloat, primitivesBuilder(),
                listOf(Float.MAX_VALUE, Float.MIN_VALUE),
                "num_float", false, true
            ),
            Arguments.of(
                TestCommandPrimitivesDef.numDoubles, primitivesBuilder(),
                listOf(
                    listOf(1.0, 0.0, -1.0),
                    listOf(Double.MAX_VALUE, Double.MIN_VALUE)
                ),
                "num_doubles", false, true
            ),
            Arguments.of(
                TestCommandPrimitivesDef.data, primitivesBuilder(),
                listOf(byteString("bytes"), byteString("data")),
                "data", false, true
            ),
            Arguments.of(
                TestCommandDef.externalType, testCommandBuilder(),
                listOf(externalType("ET1"), externalType("ET2")),
                "external_type", false, false
            ),
            Arguments.of(
                ExternalTypeDef.id, ExternalType.newBuilder(),
                listOf("id1", "id2"),
                "id", true, true
            )
        )
    }
}
