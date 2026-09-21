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

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.spine.chords.proto.value.money.PaymentCardNumber
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Verifies Visa and Mastercard prefix, length, character, and checksum rules independently of UI.
 */
@DisplayName("`PaymentCardNumberValidator` should")
internal class PaymentCardNumberValidatorSpec {

    /**
     * Accepts supported Visa lengths and both Mastercard issuer ranges, including their boundaries.
     */
    @ParameterizedTest
    @MethodSource("validNumbers")
    fun `accept supported card numbers with a valid checksum`(number: String) {
        val card = PaymentCardNumber.newBuilder()
            .setValue(number)
            .vBuild()

        val feedback = PaymentCardNumberValidator.validate(card)

        feedback.shouldBeNull()
    }

    /**
     * Preserves checksum validation for every supported prefix and length.
     */
    @ParameterizedTest
    @MethodSource("validNumbers")
    fun `reject a changed check digit in a supported card number`(number: String) {
        val incorrectCheckDigit = (number.last() - '0' + 1) % 10
        val card = PaymentCardNumber.newBuilder()
            .setValue(number.dropLast(1) + incorrectCheckDigit)
            .vBuild()

        val feedback = PaymentCardNumberValidator.validate(card)

        feedback.shouldNotBeNull()
    }

    /**
     * Rejects malformed values even when supplied directly rather than through a text field.
     */
    @ParameterizedTest
    @MethodSource("invalidNumbers")
    fun `reject invalid card numbers`(number: String) {
        val card = PaymentCardNumber.newBuilder()
            .setValue(number)
            .buildPartial()

        val feedback = PaymentCardNumberValidator.validate(card)

        feedback.shouldNotBeNull()
    }

    /**
     * Supplies public test card numbers and synthetic values with valid Luhn check digits.
     */
    private companion object {

        /**
         * Covers all supported lengths and the boundaries of both Mastercard issuer ranges.
         */
        @JvmStatic
        fun validNumbers(): List<Arguments> = listOf(
            "4222222222222",
            "4242424242424242",
            "4000000000000000006",
            "5555555555554444",
            "5100000000000008",
            "5200000000000007",
            "5300000000000006",
            "5400000000000005",
            "5599990000000008",
            "2221000000000009",
            "2221010000000008",
            "2229990000000003",
            "2230000000000008",
            "2299990000000008",
            "2300000000000003",
            "2699990000000004",
            "2700000000000009",
            "2719990000000000",
            "2720990000000007"
        )
            .map { Arguments.of(it) }

        /**
         * Covers unsupported networks, prefixes outside Mastercard ranges, and incorrect lengths.
         * Numeric prefix and length counterexamples have valid checksums to isolate those rules.
         */
        @JvmStatic
        fun invalidNumbers(): List<Arguments> = listOf(
            "",
            " ",
            "4242",
            "11112222333",
            "1111110000000001",
            "1234566",
            "12345674",
            "400000000002",
            "40000000000002",
            "400000000000006",
            "40000000000000006",
            "400000000000000002",
            "40000000000000000002",
            "5100000000003",
            "510000000000003",
            "51000000000000003",
            "5100000000000000003",
            "2221000000000",
            "222100000000000",
            "22210000000000000",
            "2221000000000000000",
            "5099990000000003",
            "5600000000000003",
            "2220990000000002",
            "2721000000000004",
            "30569309025904",
            "378282246310005",
            "6011111111111117",
            "3530111333300000",
            "6200000000000005",
            "6759649826438453",
            "4242424242424241",
            "378282246310004",
            "00000000",
            "0000000000000000",
            "0000000000000000000",
            "4242x424242424242",
            "４２４２４２４２４２４２４２４２",
            "4242 4242 4242 4242",
            "4242-4242-4242-4242"
        )
            .map { Arguments.of(it) }
    }
}
