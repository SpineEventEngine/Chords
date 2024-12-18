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

package io.spine.chords.proto.net

import io.spine.chords.core.ComponentUsage
import io.spine.chords.core.InputField
import io.spine.chords.core.InputReviser.Companion.NonWhitespaces
import io.spine.chords.core.exceptionBasedParser
import io.spine.net.Url
import io.spine.chords.proto.value.net.parse

/**
 * A field that allows entering a URL.
 */
public class UrlField : InputField<Url>() {
    public companion object : ComponentUsage<UrlField>({ UrlField() })

    init {
        label = "URL"
        promptText = "https://domain.com/path"
        inputReviser = NonWhitespaces
    }

    override fun parseValue(rawText: String): Url = exceptionBasedParser(
        IllegalArgumentException::class,
        "Enter a valid URL value"
    ) {
        Url::class.parse(rawText)
    }

    override fun formatValue(value: Url): String = value.spec ?: ""
}
