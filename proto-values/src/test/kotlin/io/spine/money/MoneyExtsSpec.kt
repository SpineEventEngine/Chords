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

package io.spine.money

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.DisplayName
import io.kotest.matchers.shouldBe
import io.spine.chords.proto.value.money.decimalSeparator
import io.spine.chords.proto.value.money.formatAmount
import io.spine.chords.proto.value.money.options
import io.spine.chords.proto.value.money.parseAmount
import io.spine.money.Currency.BIF
import io.spine.money.Currency.IQD
import io.spine.money.Currency.IRR
import io.spine.money.Currency.PYG
import io.spine.money.Currency.TZS
import io.spine.money.Currency.UAH
import io.spine.money.Currency.USD
import io.spine.money.Currency.ZWL
import org.junit.jupiter.api.Test

@DisplayName("`MoneyExts` should")
internal class MoneyExtsSpec {

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
                        "(a max of ${currency.options}.exponentDigits is allowed)."
            }
        }
    }

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

    @Test
    fun `format money value to string`() {
        val samples = getMoneyValues()

        samples.forEach { (from, to) ->
            val toLocalized = to.replace(',', decimalSeparator)

            withClue(
                "Money(units = ${from.units}, nanos = ${from.nanos}, " +
                        "currency = ${from.currency}).format()"
            ) {
                val moneyStr = from.formatAmount()

                moneyStr shouldBe toLocalized
            }
        }
    }

    private fun getInvalidSamplesWithExtraDecimalSeparators() = mapOf(

        // Extra decimal separators.
        CurrencyAmount("23652,36,", USD) to "23652,36,",
        CurrencyAmount("236,523,6", USD) to "236,523,6",
        CurrencyAmount(",23652,36", USD) to ",23652,36",
        CurrencyAmount("23,6,,523,6,", USD) to "23,6,,523,6,",
        CurrencyAmount(",,", USD) to ",,",
        CurrencyAmount("23652,36,", USD) to "23652,36,"
    )

    private fun getInvalidSamplesWithTooManyDecimalDigits() = mapOf(

        // Extra decimal digits.
        CurrencyAmount("550,123", UAH) to "550,123",
        CurrencyAmount("550,1234", USD) to "550,1234",
        CurrencyAmount("345,87212", IQD) to "345,87212",

        // Extra whitespace characters.
        CurrencyAmount("  9838, 92 ", USD) to "9838, 92",
        CurrencyAmount("   9  8 \t 5  ,  7  6  \n", USD) to "9  8 \t 5  ,  7  6",
        CurrencyAmount("    299 ,\t98\n \r", USD) to "299 ,\t98",
    )

    private fun getInvalidSamplesWithNonDigitsAmountPart() = mapOf(

        // Missing amount digits.
        CurrencyAmount(",", UAH) to ",",
        CurrencyAmount(",2", UAH) to ",2",
        CurrencyAmount(",35", UAH) to ",35",
        CurrencyAmount("", BIF) to "",
        CurrencyAmount(",", BIF) to ",",

        // Empty values.
        CurrencyAmount("", USD) to "",
        CurrencyAmount("   ", USD) to "",
        CurrencyAmount("\t", USD) to "",
        CurrencyAmount(" \t\t  \n\r \t ", USD) to "",

        // Extra random characters.
        CurrencyAmount("23lks+ 65-^фі5}2,aa", USD) to "23lks+ 65-^фі5}2,aa"
    )

    private fun getInvalidSamplesWithNonDigitsDecimalPart() = mapOf(

        // Extra random characters.
        CurrencyAmount("23,aa", USD) to "23,aa"
    )

    private fun getValidSamples() = mapOf(

        // Valid dollar amounts.
        CurrencyAmount("0,00", USD)
                to Money.newBuilder().setUnits(0).setNanos(0).setCurrency(USD).vBuild(),
        CurrencyAmount("1,23", USD)
                to Money.newBuilder().setUnits(1).setNanos(230_000_000).setCurrency(USD).vBuild(),
        CurrencyAmount("98,76", USD)
                to Money.newBuilder().setUnits(98).setNanos(760_000_000).setCurrency(USD).vBuild(),
        CurrencyAmount("482,09", USD)
                to Money.newBuilder().setUnits(482).setNanos(90_000_000).setCurrency(USD).vBuild(),
        CurrencyAmount("5482,35", USD)
                to Money.newBuilder().setUnits(5482).setNanos(350_000_000).setCurrency(USD)
            .vBuild(),
        CurrencyAmount("54982,29", USD)
                to Money.newBuilder().setUnits(54982).setNanos(290_000_000).setCurrency(USD)
            .vBuild(),
        CurrencyAmount("9354982,74", USD)
                to Money.newBuilder().setUnits(9354982).setNanos(740_000_000).setCurrency(USD)
            .vBuild(),

        // Negative amounts (they are not allowed).
        CurrencyAmount("-10,00", USD)
                to Money.newBuilder().setUnits(-10).setNanos(0).setCurrency(USD).vBuild(),
        CurrencyAmount("-10,45", USD)
                to Money.newBuilder().setUnits(-10).setNanos(-450_000_000).setCurrency(USD)
            .vBuild(),
        CurrencyAmount("-293478", PYG)
                to Money.newBuilder().setUnits(-293478).setNanos(0).setCurrency(PYG).vBuild(),

        // Valid amounts in other currencies.
        CurrencyAmount("230,75", UAH)
                to Money.newBuilder().setUnits(230).setNanos(750_000_000).setCurrency(UAH).vBuild(),
        CurrencyAmount("8230,75", UAH)
                to Money.newBuilder().setUnits(8230).setNanos(750_000_000).setCurrency(UAH)
            .vBuild(),
        CurrencyAmount("82930,75", UAH)
                to Money.newBuilder().setUnits(82930).setNanos(750_000_000).setCurrency(UAH)
            .vBuild(),
        CurrencyAmount("92898", BIF)
                to Money.newBuilder().setUnits(92898).setNanos(0).setCurrency(BIF).vBuild(),
        CurrencyAmount("5389", PYG)
                to Money.newBuilder().setUnits(5389).setNanos(0).setCurrency(PYG).vBuild(),
        CurrencyAmount("24,50", TZS)
                to Money.newBuilder().setUnits(24).setNanos(500_000_000).setCurrency(TZS).vBuild(),
        CurrencyAmount("9,99", TZS)
                to Money.newBuilder().setUnits(9).setNanos(990_000_000).setCurrency(TZS).vBuild(),
        CurrencyAmount("7294,23", ZWL)
                to Money.newBuilder().setUnits(7294).setNanos(230_000_000).setCurrency(ZWL)
            .vBuild(),
        CurrencyAmount("2452,21", IRR)
                to Money.newBuilder().setUnits(2452).setNanos(210_000_000).setCurrency(IRR)
            .vBuild(),
        CurrencyAmount("345,987", IQD)
                to Money.newBuilder().setUnits(345).setNanos(987_000_000).setCurrency(IQD).vBuild(),

        // Insufficient decimal digits.
        CurrencyAmount("0", USD)
                to Money.newBuilder().setUnits(0).setNanos(0).setCurrency(USD).vBuild(),
        CurrencyAmount("0,", USD)
                to Money.newBuilder().setUnits(0).setNanos(0).setCurrency(USD).vBuild(),
        CurrencyAmount("0,1", USD)
                to Money.newBuilder().setUnits(0).setNanos(100_000_000).setCurrency(USD).vBuild(),
        CurrencyAmount("1200", USD)
                to Money.newBuilder().setUnits(1200).setNanos(0).setCurrency(USD).vBuild(),
        CurrencyAmount("345", IQD)
                to Money.newBuilder().setUnits(345).setNanos(0).setCurrency(IQD).vBuild(),
        CurrencyAmount("345,8", IQD)
                to Money.newBuilder().setUnits(345).setNanos(800_000_000).setCurrency(IQD).vBuild(),
        CurrencyAmount("345,87", IQD)
                to Money.newBuilder().setUnits(345).setNanos(870_000_000).setCurrency(IQD).vBuild(),
        CurrencyAmount("5,", BIF)
                to Money.newBuilder().setUnits(5).setNanos(0).setCurrency(BIF).vBuild(),
        CurrencyAmount("92898,", BIF)
                to Money.newBuilder().setUnits(92898).setNanos(0).setCurrency(BIF).vBuild(),
        CurrencyAmount("92898,5", BIF)
                to Money.newBuilder().setUnits(92898).setNanos(0).setCurrency(BIF).vBuild(),
        CurrencyAmount("92898,57", BIF)
                to Money.newBuilder().setUnits(92898).setNanos(0).setCurrency(BIF).vBuild(),

        // Extra whitespace characters.
        CurrencyAmount(" 98,76", USD)
                to Money.newBuilder().setUnits(98).setNanos(760_000_000).setCurrency(USD).vBuild(),
        CurrencyAmount("   92898  ", BIF)
                to Money.newBuilder().setUnits(92898).setNanos(0).setCurrency(BIF).vBuild()
    )

    private fun getMoneyValues() = mapOf(

        // Valid dollar amounts.
        Money.newBuilder().setUnits(0).setNanos(0).setCurrency(USD).vBuild() to "0,00",
        Money.newBuilder().setUnits(1).setNanos(230_000_000).setCurrency(USD).vBuild() to "1,23",
        Money.newBuilder().setUnits(98).setNanos(760_000_000).setCurrency(USD).vBuild() to "98,76",
        Money.newBuilder().setUnits(482).setNanos(90_000_000).setCurrency(USD).vBuild() to "482,09",
        Money.newBuilder().setUnits(5482).setNanos(350_000_000).setCurrency(USD).vBuild()
                to "5482,35",
        Money.newBuilder().setUnits(54982).setNanos(290_000_000).setCurrency(USD).vBuild()
                to "54982,29",
        Money.newBuilder().setUnits(9354982).setNanos(740_000_000).setCurrency(USD).vBuild()
                to "9354982,74",

        // Valid amounts in other currencies.
        Money.newBuilder().setUnits(230).setNanos(750_000_000).setCurrency(UAH).vBuild()
                to "230,75",
        Money.newBuilder().setUnits(8230).setNanos(750_000_000).setCurrency(UAH).vBuild()
                to "8230,75",
        Money.newBuilder().setUnits(82930).setNanos(750_000_000).setCurrency(UAH).vBuild()
                to "82930,75",
        Money.newBuilder().setUnits(92898).setNanos(0).setCurrency(BIF).vBuild() to "92898",
        Money.newBuilder().setUnits(5389).setNanos(0).setCurrency(PYG).vBuild() to "5389",
        Money.newBuilder().setUnits(24).setNanos(500_000_000).setCurrency(TZS).vBuild() to "24,50",
        Money.newBuilder().setUnits(9).setNanos(990_000_000).setCurrency(TZS).vBuild() to "9,99",
        Money.newBuilder().setUnits(7294).setNanos(230_000_000).setCurrency(ZWL).vBuild()
                to "7294,23",
        Money.newBuilder().setUnits(2452).setNanos(210_000_000).setCurrency(IRR).vBuild()
                to "2452,21",
        Money.newBuilder().setUnits(345).setNanos(987_000_000).setCurrency(IQD).vBuild()
                to "345,987",

        // Insufficient decimal digits.
        Money.newBuilder().setUnits(0).setNanos(0).setCurrency(USD).vBuild() to "0,00",
        Money.newBuilder().setUnits(0).setNanos(0).setCurrency(USD).vBuild() to "0,00",
        Money.newBuilder().setUnits(0).setNanos(100_000_000).setCurrency(USD).vBuild() to "0,10",
        Money.newBuilder().setUnits(1200).setNanos(0).setCurrency(USD).vBuild() to "1200,00",
        Money.newBuilder().setUnits(345).setNanos(0).setCurrency(IQD).vBuild() to "345,000",
        Money.newBuilder().setUnits(345).setNanos(800_000_000).setCurrency(IQD).vBuild()
                to "345,800",
        Money.newBuilder().setUnits(345).setNanos(870_000_000).setCurrency(IQD).vBuild()
                to "345,870",
        Money.newBuilder().setUnits(5).setNanos(0).setCurrency(BIF).vBuild() to "5"
    )
}

private data class CurrencyAmount(val amount: String, val currency: Currency)
