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

package io.spine.chords.proto.net

import io.spine.chords.core.ComponentSetup
import io.spine.chords.core.InputField
import io.spine.chords.core.InputReviser.Companion.NonWhitespaces
import io.spine.chords.core.InputReviser.Companion.ToLowerCase
import io.spine.chords.core.InputReviser.Companion.then
import io.spine.chords.core.exceptionBasedParser
import io.spine.net.InternetDomain
import io.spine.net.InternetDomains

/**
 * A field that allows entering an [InternetDomain] value.
 */
public class InternetDomainField : InputField<InternetDomain>() {
    public companion object : ComponentSetup<InternetDomainField>(
        { InternetDomainField() }
    )

    init {
        inputReviser = NonWhitespaces then ToLowerCase
    }

    override fun parseValue(rawText: String): InternetDomain = exceptionBasedParser(
        IllegalArgumentException::class,
        "Invalid domain syntax"
    ) {
        InternetDomains.valueOf(rawText)
    }

    override fun formatValue(value: InternetDomain): String = value.value
}
