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

package io.spine.chords.proto.money

import io.spine.chords.proto.value.money.PaymentCardNumber

/**
 * Validates complete Visa and Mastercard numbers without contacting a payment service.
 *
 * Checks ASCII digits, network prefixes and lengths, and the Luhn checksum.
 * Visa numbers have 13, 16, or 19 digits; Mastercard numbers have 16 digits.
 * Numbers from other networks are rejected.
 * This checks the number's format, not whether the card is issued or can make a payment.
 * [PaymentCardNumberField] removes spaces and separators before invoking this validator.
 *
 * Enable it for a field with `onValidate = PaymentCardNumberValidator::validate`.
 */
public object PaymentCardNumberValidator {

    /**
     * Permitted lengths for Visa account numbers.
     */
    private val visaNumberLengths = setOf(13, 16, 19)

    /**
     * Required length for Mastercard account numbers in either issuer range.
     */
    private const val MastercardNumberLength = 16

    /**
     * Number of leading digits used to identify the supported Mastercard issuer ranges.
     */
    private const val IssuerPrefixLength = 6

    /**
     * Original Mastercard issuer range, including both boundaries.
     */
    private val mastercardOriginalIssuerRange = 510000..559999

    /**
     * Additional Mastercard issuer range, including both boundaries.
     */
    private val mastercardExtendedIssuerRange = 222100..272099

    /**
     * Base used to sum decimal digits and test the Luhn remainder.
     */
    private const val DecimalRadix = 10

    /**
     * Returns feedback for an invalid number, or `null` when the number passes validation.
     * Empty fields remain subject to their containing form's required-value validation.
     */
    public fun validate(cardNumber: PaymentCardNumber): String? {
        val number = cardNumber.value
        return if (number.any { it !in '0'..'9' } ||
            !hasSupportedNetworkAndLength(number) ||
            !hasValidChecksum(number)
        ) {
            "Enter a valid card number"
        } else {
            null
        }
    }

    /**
     * Checks Visa and Mastercard ranges from
     * [Visa's card input guidance](https://design.visa.com/patterns/card-input/).
     * Expects only ASCII digits, as checked by [validate].
     */
    private fun hasSupportedNetworkAndLength(number: String): Boolean {
        return when {
            number.startsWith('4') -> number.length in visaNumberLengths
            number.length == MastercardNumberLength -> {
                val issuerPrefix = number.take(IssuerPrefixLength)
                    .toInt()
                issuerPrefix in mastercardOriginalIssuerRange ||
                        issuerPrefix in mastercardExtendedIssuerRange
            }
            else -> false
        }
    }

    /**
     * Checks the Luhn sum of ASCII digits, excluding the all-zero value.
     * Every second digit from the right is doubled, starting before the final check digit.
     */
    private fun hasValidChecksum(number: String): Boolean {
        var sum = 0
        number.reversed()
            .forEachIndexed { index, character ->
                val digit = character - '0'
                val weightedDigit = if (index % 2 == 1) digit * 2 else digit
                sum += weightedDigit / DecimalRadix + weightedDigit % DecimalRadix
            }
        return sum > 0 && sum % DecimalRadix == 0
    }
}
