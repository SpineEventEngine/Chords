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

package io.spine.chords.proto.value.net

import io.spine.net.Url
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import kotlin.reflect.KClass

/**
 * Parses the given human-readable string [Url] value.
 *
 * @param str
 *         a string that should be parsed as [Url].
 * @return the respective [Url] value.
 * @throws IllegalArgumentException
 *         if the given string cannot be parsed as a [Url] value.
 */
// The receiver is needed to specify a "static" context for the function.
@Suppress("UnusedReceiverParameter")
public fun KClass<Url>.parse(str: String): Url {
    require(isValidUrl(str)) {
        "Couldn't parse url value: $str"
    }

    return Url.newBuilder()
        .setSpec(str)
        .vBuild()
}

/**
 * Validates whether the given string represents a valid URL.
 *
 * @param str
 *         a URL string.
 * @return [Boolean], which indicates if passed url string is valid.
 */
private fun isValidUrl(str: String): Boolean {
    var isValid: Boolean
    try {
        URL(str).toURI()
        isValid = true
    } catch (e: MalformedURLException) {
        isValid = false
    } catch (
        // Exception value itself is not needed.
        @Suppress("SwallowedException")
        e: URISyntaxException
    ) {
        isValid = false
    }
    return isValid
}
