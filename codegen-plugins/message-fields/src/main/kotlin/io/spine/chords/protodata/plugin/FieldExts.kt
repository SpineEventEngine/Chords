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

package io.spine.chords.protodata.plugin

import com.google.protobuf.BoolValue
import io.spine.protobuf.AnyPacker.unpack
import io.spine.protodata.Field
import io.spine.protodata.TypeName
import io.spine.protodata.isEnum
import io.spine.protodata.isPrimitive
import io.spine.protodata.isRepeated
import io.spine.string.camelCase

/**
 * Returns a piece of code that sets a new value for the [Field].
 */
internal fun Field.generateSetterCode(messageClass: TypeName): String {
    val messageShortClassName = messageClass.simpleClassName
    val builderCast = "(builder as $messageShortClassName.Builder)"
    val setterCall = "$setterInvocation(newValue)"
    return if (isRepeated) {
        "$builderCast.clear${name.value.camelCase()}().$setterCall"
    } else {
        "$builderCast.$setterCall"
    }
}

/**
 * Indicates if the `required` option is applied to the [Field].
 */
internal val Field.required: Boolean
    get() = optionList.any { option ->
        option.name == "required" &&
                unpack(option.value, BoolValue::class.java).value
    }

/**
 * Returns a "getter" invocation code for the [Field].
 */
internal val Field.getterInvocation
    get() = if (isRepeated)
        name.value.propertyName + "List"
    else name.value.propertyName

/**
 * Returns a "hasValue" invocation code for the [Field].
 *
 * The generated code returns `true` if a field is repeated, is an enum,
 * or a primitive. This is required to be compatible with the design approach
 * of `protoc`-generated Java code. There, `hasValue` methods are not being
 * generated for the fields of such kinds.
 */
internal val Field.hasValueInvocation: String
    get() = if (isRepeated || type.isEnum || type.isPrimitive) "true"
    else "message.has${name.value.camelCase()}()"

/**
 * Returns a "setter" invocation code for the [Field].
 */
private val Field.setterInvocation: String
    get() = if (isRepeated)
        "addAll${name.value.camelCase()}"
    else "set${name.value.camelCase()}"
