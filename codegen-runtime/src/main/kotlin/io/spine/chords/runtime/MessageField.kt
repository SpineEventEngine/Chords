/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
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
 * The codegen plugin for 1DAM relies on this interface as well.
 *
 * Implementations of this interface for the fields of Proto messages
 * are generated automatically during the build. It is not expected
 * that end-users manually create any descendants.
 *
 *
 * @param T a type of the Proto message, containing the accessed field.
 * @param V a type of the field value.
 */
public abstract class MessageField<T, V> where T : Message, V: MessageFieldValue {

    /**
     * The name of the field as it is defined in Proto file.
     */
    public abstract val name: String

    /**
     * Indicates if the `required` option is applied to the field.
     */
    public abstract val required: Boolean

    /**
     * Returns a value of the field for the given message.
     */
    public abstract fun valueIn(message: T): V

    /**
     * Returns `true` if a value was set for this field in the given message.
     *
     * In the generated implementations, it always returns `true` if a field
     * is repeated, is an enum, or a primitive. This is required to be
     * compatible with the design approach of `protoc`-generated Java code.
     * There, `hasValue` methods are not being generated for the fields
     * of such kinds.
     */
    public abstract fun hasValue(message: T): Boolean

    /**
     * Sets a new field value for the given message builder.
     */
    public abstract fun setValue(builder: ValidatingBuilder<T>, newValue: V)

    override fun equals(other: Any?): Boolean {
        // Since each field has its own implementation class, it is enough to
        // check the equality of classes to identify the equality of instances.
        if (this === other) return true
        return javaClass == other?.javaClass
    }

    override fun hashCode(): Int {
        // Since each field has its own implementation class, the instance's
        // hash code can be identified as a hash code of the
        // implementation class.
        return javaClass.hashCode()
    }
}
