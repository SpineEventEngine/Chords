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

package io.spine.chords.proto.value.protobuf

import com.google.protobuf.Message
import java.beans.Introspector

/**
 * Returns a value of `true` if this [Message] instance is the same as
 * the default instance for this message type, and `false` otherwise.
 */
public fun Message.isDefault(): Boolean = (defaultInstanceForType == this)

/**
 * Given a name of the message's oneof, returns the number of the field, which
 * is set in this oneof.
 *
 * @param oneofName
 *         name of the oneof whose field number should be returned.
 * @return a number of field that is selected in the specified oneof.
 * @throws IllegalArgumentException
 *         if no oneof with the given name was found in this message.
 *
 * TODO:2024-01-20:dmitry.pikhulya:
 *      Try replacing the reflection with some dedicated APIs:
 *      some API for working with message's oneofs would actually be useful to
 *      get a "built-in" support for the functionality implemented in
 *      this method.
 *      See https://github.com/Projects-tm/1DAM/issues/201 (see point [2])
 */
public fun Message.getOneofFieldNumber(oneofName: String): Int {

    // Java classes compiled from respective Protobuf declarations have
    // respective properties named like `<oneofName>Case` where the number of
    // the selected oneof's field can be obtained.
    return getJavaPropertyValue("${oneofName}Case")
        .getJavaPropertyValue("number") as Int
}

/**
 * When invoked on the Java object, locates the Java property with the specified
 * name (as a respective getter function), and returns the value of that
 * property by invoking the respective getter method.
 *
 * @receiver an object whose property should be read.
 * @param propertyName
 *         name of the Java property to read.
 * @return the value that was obtained from the property reader.
 * @throw IllegalArgumentException
 *         if the Java property with the given name was not found.
 */
private fun Any.getJavaPropertyValue(propertyName: String): Any {
    val beanInfo = Introspector.getBeanInfo(this::class.java)
    val propertyDesc = beanInfo.propertyDescriptors.firstOrNull {
        it.name == propertyName
    } ?: throw IllegalArgumentException("Couldn't find a property with name: $propertyName")
    return propertyDesc.readMethod.invoke(this)
}
