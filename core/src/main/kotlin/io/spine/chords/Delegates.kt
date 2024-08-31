/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Creates a delegate for defining a property that can be set only once.
 *
 * Usage example:
 * ```
 *   var prop: String by writeOnce()
 *
 *   // print(prop) would throw IllegalStateException here, when
 *   // the property is read before it is set for the first time
 *
 *   prop = "Hello"
 *   print(prop) // prints "Hello" as expected
 *
 *   prop = "Something else" // throws IllegalStateException if set again
 *
 *   // ...
 *   print(prop) // still prints "Hello" despite additional set attempt(s)
 * ```
 *
 * @param T
 *         the property's type.
 * @return a respective [ReadWriteProperty], which has to be used as
 *         a property delegate.
 */
public fun <T : Any> writeOnce(): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
    private var value: T? = null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        check(value != null) { "Value is being read but it hasn't been set yet." }
        return value!!
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        check(this.value == null) {
            "Value has already been set earlier and cannot be set more than once."
        }
        this.value = value
    }
}
