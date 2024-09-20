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

package io.spine.chords.runtime

import com.google.protobuf.Message
import io.spine.protobuf.ValidatingBuilder

/**
 * A type of values that Protobuf message fields can have.
 *
 * Ideally this should have been a union type that would list actual types
 * supported by Protobuf, if union types were supported by Kotlin (see
 * https://youtrack.jetbrains.com/issue/KT-13108/Denotable-union-and-intersection-types).
 *
 * Nevertheless, this type alias is still declared for the code that deals with
 * generic protobuf field values to be more self-explainable.
 */
public typealias MessageFieldValue = Any


/**
 * Allows to access the value of the field in a Proto message at runtime.
 *
 * The codegen plugin relies on this interface as well.
 *
 * Implementations of this interface for the fields of Proto messages
 * are generated automatically during the build. It is not expected
 * that end-users manually create any descendants.
 *
 * @param T a type of the Proto message, containing the accessed field.
 * @param V a type of the field value.
 */
public interface MessageField<T : Message, V : MessageFieldValue> {

    /**
     * The name of the field as it is defined in Proto file.
     */
    public val name: String

    /**
     * Indicates if the `required` option is applied to the field.
     */
    public val required: Boolean

    /**
     * Returns a value of the field for the given message.
     */
    public fun valueIn(message: T): V

    /**
     * Returns `true` if a value was set for this field in the given message.
     *
     * In the generated implementations, it always returns `true` if a field
     * is repeated, is an enum, or a primitive. This is required to be
     * compatible with the design approach of `protoc`-generated Java code.
     * There, `hasValue` methods are not being generated for the fields
     * of such kinds.
     */
    public fun hasValue(message: T): Boolean

    /**
     * Sets a new field value for the given message builder.
     */
    public fun setValue(builder: ValidatingBuilder<T>, newValue: V)

}
