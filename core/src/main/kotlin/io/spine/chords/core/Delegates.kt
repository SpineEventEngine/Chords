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

package io.spine.chords.core

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Creates a delegate for defining a property that can be set only once.
 *
 * Usage example:
 * ```
 *   var prop: String by writeOnce()
 *
 *   // `print(prop)` would throw `IllegalStateException` here, when
 *   // the property is read before it is set for the first time.
 *
 *   prop = "Hello"
 *   print(prop) // Prints "Hello" as expected.
 *
 *   prop = "Something else" // Throws `IllegalStateException` if set again.
 *
 *   // ...
 *   print(prop) // Still prints "Hello" despite additional set attempt(s).
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
