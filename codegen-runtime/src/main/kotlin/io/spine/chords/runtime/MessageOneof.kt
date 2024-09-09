/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.runtime

import com.google.protobuf.Message

/**
 * Allows to access the value of the oneof field in a Proto message at runtime.
 *
 * The codegen plugin for 1DAM relies onto this interface as well.
 *
 * Implementations of this interface for the oneof fields of Proto messages
 * are generated automatically during the build. It is not expected
 * that end-users manually create any descendants.
 *
 *
 * @param T a type of the Proto message, containing the accessed oneof field.
 */
public interface MessageOneof<T> where T : Message {

    /**
     * The name of the oneof field in a message as it is defined in Proto file.
     */
    public val name: String

    /**
     * Returns collection of [MessageField]s declared as options of this oneof.
     */
    public val fields: Collection<MessageField<T, MessageFieldValue>>

    /**
     * Returns [MessageField] that is currently set in this oneof.
     */
    public fun selectedField(message: T): MessageField<T, MessageFieldValue>?
}
