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

package io.spine.chords.proto.money

import io.spine.chords.ComponentCompanion
import io.spine.chords.InputField
import io.spine.chords.InputReviser.Companion.DigitsOnly
import io.spine.chords.InputReviser.Companion.maxLength
import io.spine.chords.InputReviser.Companion.then
import io.spine.chords.ValueParseException
import io.spine.chords.proto.form.vBuildBasedParser
import io.spine.chords.proto.value.money.PaymentCardNumber
import io.spine.chords.proto.value.money.PaymentCardNumberKt

/**
 * Minimum payment card number length (inclusive),
 * see https://en.wikipedia.org/wiki/Payment_card_number#Structure
 */
// The receiver is needed to specify a "static" context for the function.
@Suppress("UnusedReceiverParameter")
private val PaymentCardNumberKt.minValueLength get() = 8

/**
 * Maximum payment card number length (inclusive),
 * see https://en.wikipedia.org/wiki/Payment_card_number#Structure
 * // TODO:2023-11-10:dmitry.pikhulya: this should ideally be replaced with
 * //     adding new kinds of options that would put respective constraints
 * //     for string-typed model fields (min/max length options).
 * //     This appears to require two different aspects to be addressed:
 * //     - A respective option needs to be added:
 * //       See: https://github.com/Projects-tm/1DAM/issues/44
 * //     - A respective API that wouldn't require reflection will be needed
 * //       for the component to be able to perform this check on the client:
 *          https://github.com/Projects-tm/1DAM/issues/41
 */
// The receiver is needed to specify a "static" context for the function.
@Suppress("UnusedReceiverParameter")
private val PaymentCardNumberKt.maxValueLength get() = 19

/**
 * A field that allows entering a payment card number.
 */
public class PaymentCardNumberField : InputField<PaymentCardNumber>() {

    /**
     * Instance declaration API.
     */
    public companion object : ComponentCompanion<PaymentCardNumberField>({
        PaymentCardNumberField()
    })

    init {
        label = "Card no"
        inputReviser = DigitsOnly then maxLength(PaymentCardNumberKt.maxValueLength)
    }

    override fun parseValue(rawText: String): PaymentCardNumber {
        if (rawText.length < PaymentCardNumberKt.minValueLength) {
            // TODO:2023-11-10:dmitry.pikhulya: this should ideally be
            //     replaced with adding new kinds of options that would put
            //     respective constraints for string-typed model fields
            //     (min/max length options).
            //     NOTE: Once this is implemented, no explicit condition
            //           checking and exception throwing branch like this
            //           should be needed, since vBuild() would include
            //           the respective check.
            //     See: https://github.com/Projects-tm/1DAM/issues/44
            throw ValueParseException()
        }
        return vBuildBasedParser {
            PaymentCardNumber.newBuilder()
                .setValue(rawText)
        }
    }

    override fun formatValue(value: PaymentCardNumber): String = value.value
}
