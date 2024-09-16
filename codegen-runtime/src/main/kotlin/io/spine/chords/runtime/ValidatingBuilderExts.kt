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
import io.spine.chords.runtime.MessageDef.Companion.MESSAGE_DEF_CLASS_SUFFIX
import io.spine.protobuf.ValidatingBuilder
import io.spine.protodata.java.ClassName

/**
 * Returns the generated implementation of [MessageDef] for the [M] Proto message.
 *
 * Uses Reflection API to read the `messageDef` property on a message builder instance.
 *
 * @param M The type of Proto message.
 */
@Suppress("Unchecked_cast")
public fun <M : Message> ValidatingBuilder<M>.messageDef(): MessageDef<M> {
    val builderClass = this::class.java
    val messageDefClassName = ClassName.guess(builderClass.name)
        .outer()!!.binary
        .replace("$", "")
        .plus(MESSAGE_DEF_CLASS_SUFFIX)
        .plus("Kt")
    val messageDefClass = Class.forName(messageDefClassName)
    val getMessageDefMethod = messageDefClass
        .getMethod("getMessageDef", builderClass)
    return getMessageDefMethod.invoke(builderClass, this) as MessageDef<M>
}
