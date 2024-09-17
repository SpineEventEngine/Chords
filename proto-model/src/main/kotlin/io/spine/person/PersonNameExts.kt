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

package io.spine.person

import io.spine.people.PersonName
import kotlin.reflect.KClass

/**
 * Parses a string as a [PersonName] value.
 *
 * This implementation assumes that both given name and family name must be
 * specified and no other fields of [PersonName] are supported.
 *
 * @param nameString
 *         a string that should be parsed, which is expected to contain person's
 *         given name and family name (in this order).
 * @return the respective [PersonName] object that was parsed from [nameString].
 *
 * @throws IllegalArgumentException
 *         if the given string could not be parsed a [PersonName] value.
 */
// The receiver is needed to specify a "static" context for the function.
@Suppress("UnusedReceiverParameter")
public fun KClass<PersonName>.parse(nameString: String): PersonName {
    val words = nameString.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .map { it.trim() }
    if (words.size != 2) {
        throw IllegalArgumentException(
            "A person name must contain two words (given name, and family name), " +
                    "but it has ${words.size}: $nameString"
        )
    }
    val (givenName, familyName) = words
    return PersonName.newBuilder()
        .setGivenName(givenName)
        .setFamilyName(familyName)
        .vBuild()
}

/**
 * Formats `PersonName` as a human-readable string.
 *
 * This implementation supports only the `givenName` and `familyName` fields
 * of [PersonName]. Values of other fields are not taken into account.
 *
 * @receiver a person name that should be formatted as a string.
 * @return a string which contains the given name and family name from
 *         the [PersonName] value on which this function was invoked.
 */
public fun PersonName.format(): String {
    return "$givenName $familyName"
}
