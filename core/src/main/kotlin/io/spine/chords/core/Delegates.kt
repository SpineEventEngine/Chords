/*
 * Copyright 2025, TeamDev. All rights reserved.
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
 * More precisely, it has two variants of functioning, depending on the value of
 * [identicalRewritesAllowed]. In either mode, reading a property before it has been
 * assigned for the first time throws [IllegalStateException], and the first
 * write of the property defines the value that the property will take for its
 * entire lifetime.
 *
 * What [identicalRewritesAllowed] affects is it is set to `true` (the default
 * value), then writing the property after it has been written for the first
 * time can be performed as long as the value being set is referentially equal
 * to the value written to it for the first time. Any attempt to write a value,
 * which is not referentially equal to the first written value leads
 * to throwing [IllegalStateException].
 *
 * If [identicalRewritesAllowed] is set to `false` though, any attempt to write
 * the property after it has been written for the first time leads
 * to [IllegalStateException].
 *
 * Usage example (with `identicalRewritesAllowed` equal to `true`):
 * ```
 *   var prop: String by writeOnce() // `identicalRewritesAllowed` is `true` by default.
 *
 *   // `print(prop)` would throw `IllegalStateException` here, when
 *   // the property is read before it is set for the first time.
 *
 *   prop = "Hello"
 *   print(prop) // Prints "Hello" as expected.
 *
 *   prop = "Hello" // Nothing happens.
 *   prop = "Hello" // Nothing happens.
 *   prop = "Something else" // Throws `IllegalStateException`.
 *
 *   // ... if the property survives the exception ...
 *   print(prop) // Still prints "Hello" despite additional set attempt(s).
 * ```
 *
 * Usage example (with `identicalRewritesAllowed` equal to `false`):
 * ```
 *   var prop: String by writeOnce(false)
 *
 *   // `print(prop)` would throw `IllegalStateException` here, when
 *   // the property is read before it is set for the first time.
 *
 *   prop = "Hello"
 *   print(prop) // Prints "Hello" as expected.
 *
 *   prop = "Something else" // Throws `IllegalStateException` if set again.
 *
 *   // ... if the property survives the exception ...
 *   print(prop) // Still prints "Hello" despite additional set attempt(s).
 * ```
 *
 * @param T The property's type.
 *
 * @param identicalRewritesAllowed If `true` (default), repeated writes of
 *   the same value that was assigned initially are allowed. Otherwise, any
 *   attempt to write a property with any value after it has been written for
 *   the first time throw [IllegalStateException].
 * @return A respective [ReadWriteProperty], which has to be used as
 *   a property delegate.
 */
public fun <T : Any> writeOnce(
    identicalRewritesAllowed: Boolean = true
): ReadWriteProperty<Any?, T> =
    object : ReadWriteProperty<Any?, T> {
        private var value: T? = null

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            check(value != null) { "Value is being read but it hasn't been set yet." }
            return value!!
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            check(this.value == null || (identicalRewritesAllowed && this.value === value)) {
                if (identicalRewritesAllowed) {
                    "A `writeOnce` property cannot be rewritten with another value."
                } else {
                    "A `writeOnce` property has already been written earlier " +
                            "and cannot be written more than once."
                }
            }
            this.value = value
        }
    }
