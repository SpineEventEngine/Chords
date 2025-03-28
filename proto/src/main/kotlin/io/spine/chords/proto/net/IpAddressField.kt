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

import androidx.compose.ui.input.key.KeyEvent
import io.spine.chords.core.ComponentSetup
import io.spine.chords.core.InputField
import io.spine.chords.core.InputReviser
import io.spine.chords.core.RawTextContent
import io.spine.chords.core.exceptionBasedParser
import io.spine.chords.core.keyboard.KeyRange.Companion.Digit
import io.spine.chords.core.keyboard.key
import io.spine.chords.core.keyboard.matches
import io.spine.chords.proto.value.net.IpAddress
import io.spine.chords.proto.value.net.IpAddressKt
import io.spine.chords.proto.value.net.format
import io.spine.chords.proto.value.net.parse

/**
 * A field for entering an IP address.
 */
public class IpAddressField : InputField<IpAddress>() {
    public companion object : ComponentSetup<IpAddressField>({ IpAddressField() })

    init {
        label = "IP address"
        inputReviser = DigitsAndDotsOnly
    }

    override fun parseValue(rawText: String): IpAddress = exceptionBasedParser(
        IllegalArgumentException::class,
        "Enter a valid IP address"
    ) {
        IpAddressKt.parse(rawText)
    }

    override fun formatValue(value: IpAddress): String = value.format()
}

/**
 * An [InputReviser] that accepts only digits and dots.
 */
private val DigitsAndDotsOnly: InputReviser = object : InputReviser {
    override fun reviseRawTextContent(
        current: RawTextContent,
        candidate: RawTextContent
    ): RawTextContent = candidate.copy(
        candidate.text.filter { it.isDigit() || it == '.' }
    )

    override fun filterKeyEvent(keyEvent: KeyEvent): Boolean =
        keyEvent matches (!Digit and !'.'.key).typed
}
