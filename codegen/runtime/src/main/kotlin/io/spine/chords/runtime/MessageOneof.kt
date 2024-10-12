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

/**
 * Allows to access the value of the oneof field in a Proto message at runtime.
 *
 * The codegen plugin relies onto this interface as well.
 *
 * Implementations of this interface for the oneof fields of Proto messages
 * are generated automatically during the build. It is not expected
 * that end-users manually create any descendants.
 *
 * @param T a type of the Proto message, containing the accessed oneof field.
 */
public interface MessageOneof<T : Message> {

    /**
     * The name of the oneof field in a message as it is defined in Proto file.
     */
    public val name: String

    /**
     * Returns collection of [MessageField]s declared by this `oneof`.
     */
    public val fields: Collection<MessageField<T, MessageFieldValue>>

    /**
     * Returns [MessageField] that is currently set in this oneof.
     */
    public fun selectedField(message: T): MessageField<T, MessageFieldValue>?
}
