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

package io.spine.money

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.spine.chords.proto.value.money.decimalSeparator
import io.spine.chords.proto.value.money.formatAmount
import io.spine.chords.proto.value.money.options
import io.spine.chords.proto.value.money.parseAmount
import io.spine.money.Currencies.USD
import io.spine.money.given.MoneySpecEnv.getInvalidSamplesWithExtraDecimalSeparators
import io.spine.money.given.MoneySpecEnv.getInvalidSamplesWithNonDigitsAmountPart
import io.spine.money.given.MoneySpecEnv.getInvalidSamplesWithNonDigitsDecimalPart
import io.spine.money.given.MoneySpecEnv.getInvalidSamplesWithTooManyDecimalDigits
import io.spine.money.given.MoneySpecEnv.getMoneyValues
import io.spine.money.given.MoneySpecEnv.getValidSamples
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies localized parsing and formatting against Spine Money 2 values.
 */
@DisplayName("`Money` should")
internal class MoneySpec {

    /**
     * Verifies throw IllegalArgumentException when money string has extra decimal separators.
     */
    @Test
    fun `throw IllegalArgumentException when money string has extra decimal separators`() {
        val samples = getInvalidSamplesWithExtraDecimalSeparators()

        samples.forEach { (from, to) ->
            val amount = from.amount.replace(',', decimalSeparator)
            val currency = from.currency
            val toLocalized = to.replace(',', decimalSeparator)

            withClue("Money::class.parse(\"$amount\", \"$currency\")") {
                val exception = shouldThrow<IllegalArgumentException> {
                    Money::class.parseAmount(amount, currency)
                }

                exception.message shouldBe "Couldn't parse money value: $toLocalized"
            }
        }
    }

    /**
     * Verifies throw IllegalArgumentException when money string has too many decimal digits.
     */
    @Test
    fun `throw IllegalArgumentException when money string has too many decimal digits`() {
        val samples = getInvalidSamplesWithTooManyDecimalDigits()

        samples.forEach { (from, to) ->
            val amount = from.amount.replace(',', decimalSeparator)
            val currency = from.currency
            val toLocalized = to.replace(',', decimalSeparator)

            withClue("Money::class.parse(\"$amount\", \"$currency\")") {
                val exception = shouldThrow<IllegalArgumentException> {
                    Money::class.parseAmount(amount, currency)
                }

                exception.message shouldBe "The money string ($toLocalized) has too many " +
                        "decimal digits for this currency " +
                        "(a max of ${currency.options.fractionDigits} is allowed)."
            }
        }
    }

    /**
     * Verifies throw IllegalArgumentException when money string has amount part with non-digits.
     */
    @Test
    fun `throw IllegalArgumentException when money string has amount part with non-digits`() {
        val samples = getInvalidSamplesWithNonDigitsAmountPart()

        samples.forEach { (from, to) ->
            val amount = from.amount.replace(',', decimalSeparator)
            val currency = from.currency
            val toLocalized = to.replace(',', decimalSeparator)

            withClue("Money::class.parse(\"$amount\", \"$currency\")") {
                val exception = shouldThrow<IllegalArgumentException> {
                    Money::class.parseAmount(amount, currency)
                }

                exception.message shouldBe "Couldn't parse money value: $toLocalized"
            }
        }
    }

    /**
     * Verifies throw IllegalArgumentException when money string has decimal part with non-digits.
     */
    @Test
    fun `throw IllegalArgumentException when money string has decimal part with non-digits`() {
        val samples = getInvalidSamplesWithNonDigitsDecimalPart()

        samples.forEach { (from, to) ->
            val amount = from.amount.replace(',', decimalSeparator)
            val currency = from.currency
            val toLocalized = to.replace(',', decimalSeparator)

            withClue("Money::class.parse(\"$amount\", \"$currency\")") {
                val exception = shouldThrow<IllegalArgumentException> {
                    Money::class.parseAmount(amount, currency)
                }

                exception.message shouldBe "Couldn't parse money value: $toLocalized"
            }
        }
    }

    /**
     * Verifies parse money string.
     */
    @Test
    fun `parse money string`() {
        val samples = getValidSamples()

        samples.forEach { (from, to) ->
            val amount = from.amount.replace(',', decimalSeparator)
            val currency = from.currency

            withClue("Money::class.parse(\"$amount\", \"$currency\")") {
                val money = Money::class.parseAmount(amount, currency)

                money shouldBe to
            }
        }
    }

    /**
     * Verifies format money value to string.
     */
    @Test
    fun `format money value to string`() {
        val samples = getMoneyValues()

        samples.forEach { (from, to) ->
            val toLocalized = to.replace(',', decimalSeparator)

            withClue(
                "Money(units = ${from.units}, attos = ${from.attos}, " +
                        "currency = ${from.currency}).format()"
            ) {
                val moneyStr = from.formatAmount()

                moneyStr shouldBe toLocalized
            }
        }
    }

    /**
     * Preserves the sign of a negative fractional amount with zero whole units.
     */
    @Test
    fun `round trip a negative fraction`() {
        val input = "-0${decimalSeparator}01"
        val money = Money::class.parseAmount(input, USD)

        money.units shouldBe 0L
        money.attos shouldBe -10_000_000_000_000_000L
        money.formatAmount() shouldBe input
    }
}
