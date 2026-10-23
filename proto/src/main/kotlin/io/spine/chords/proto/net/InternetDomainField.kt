/*
 * Copyright 2026, TeamDev. All rights reserved.
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 *
 * Whitespace is removed and input is converted to lowercase before parsing.
 * Bind [value] to caller-owned state to receive valid domains, or `null` for
 * empty or invalid input. By default, validation messages appear below the field.
 * Set [supportingText] to include additional content without replacing domain editing.
 *
 * For example, an application can supply an action that opens domain help:
 *
 * ```kotlin
 * @Composable
 * fun DomainEditor(onOpenHelp: () -> Unit) {
 *     val domain = remember { mutableStateOf<InternetDomain?>(null) }
 *     InternetDomainField {
 *         value = domain
 *         supportingText = { message ->
 *             Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
 *                 Text(message)
 *                 Text(
 *                     text = "Domain help",
 *                     modifier = Modifier.clickable(onClick = onOpenHelp),
 *                     textDecoration = TextDecoration.Underline
 *                 )
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * The clickable text is reachable with Tab and activated with Enter. Custom
 * content is responsible for displaying the supplied validation message and
 * providing accessible actions, as in this example.
 */
public class InternetDomainField : InputField<InternetDomain>() {

    /**
     * Declares and configures a domain field in a composition.
     */
    public companion object : ComponentSetup<InternetDomainField>()

    /**
     * Content below the field, receiving the current validation message or an empty string.
     * Parsing and [onValidate] errors take precedence over [externalValidationMessage].
     * The default renders the message as plain text. Replacing this lambda triggers recomposition.
     *
     * Content uses the existing supporting area and its text style and color. When
     * [reserveValidationSpace] is `false`, it is composed only while a validation message exists.
     */
    public override var supportingText: @Composable (String) -> Unit by
        mutableStateOf(super.supportingText)

    init {
        inputReviser = NonWhitespaces then ToLowerCase
    }

    /**
     * Parses revised domain text and reports invalid domain names as validation feedback.
     */
    override fun parseValue(rawText: String): InternetDomain = exceptionBasedParser(
        IllegalArgumentException::class,
        "Enter a valid domain name."
    ) {
        InternetDomains.valueOf(rawText)
    }

    /**
     * Displays the domain name stored in the bound value.
     */
    override fun formatValue(value: InternetDomain): String = value.value
}
