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

package io.spine.chords.money

import androidx.compose.ui.text.TextRange
import io.kotest.assertions.withClue
import io.kotest.core.spec.DisplayName
import io.kotest.matchers.shouldBe
import io.spine.chords.RawTextContent
import io.spine.chords.money.MoneyFieldReviser
import io.spine.money.Currency
import io.spine.money.Currency.BIF
import io.spine.money.Currency.IQD
import io.spine.money.Currency.IRR
import io.spine.money.Currency.PYG
import io.spine.money.Currency.TZS
import io.spine.money.Currency.UAH
import io.spine.money.Currency.USD
import io.spine.money.Currency.ZWL
import java.text.DecimalFormatSymbols
import org.junit.jupiter.api.Test

private val decimalSeparator get() = DecimalFormatSymbols.getInstance().decimalSeparator

@DisplayName("`MoneyField` should")
internal class MoneyFieldSpec {

    @Test
    fun `revise money field content`() {
        val sanitizingSamples = getSanitizingSamples()

        sanitizingSamples.forEach { (from, to) ->
            val fromLocalized = from.amount.replace(',', decimalSeparator)
            val toLocalized = to.replace(',', decimalSeparator)
            val moneyFieldReviser = MoneyFieldReviser(from.currency)
            withClue(
                "MoneyFieldReviser.reviseRawTextContent(RawTextContent(), " +
                        "RawTextContent(\"$fromLocalized\"))"
            ) {
                val revisedRawTextContent = moneyFieldReviser.reviseRawTextContent(
                    RawTextContent(),
                    RawTextContent(fromLocalized, TextRange(fromLocalized.length))
                )

                revisedRawTextContent.text shouldBe toLocalized
            }
        }
    }

    private fun getSanitizingSamples() = mapOf(
        // Valid dollar amounts.
        CurrencyAmount("0,00", USD) to "0,00",
        CurrencyAmount("1,23", USD) to "1,23",
        CurrencyAmount("98,76", USD) to "98,76",
        CurrencyAmount("482,09", USD) to "482,09",
        CurrencyAmount("5482,35", USD) to "5482,35",
        CurrencyAmount("54982,29", USD) to "54982,29",
        CurrencyAmount("9354982,74", USD) to "9354982,74",

        // Negative amounts (they are not allowed).
        CurrencyAmount("-10,00", USD) to "10,00",
        CurrencyAmount("-293478", PYG) to "293478",

        // Valid amounts in other currencies.
        CurrencyAmount("230,75", UAH) to "230,75",
        CurrencyAmount("8230,75", UAH) to "8230,75",
        CurrencyAmount("82930,75", UAH) to "82930,75",
        CurrencyAmount("92898", BIF) to "92898",
        CurrencyAmount("5389", PYG) to "5389",
        CurrencyAmount("24,50", TZS) to "24,50",
        CurrencyAmount("9,99", TZS) to "9,99",
        CurrencyAmount("7294,23", ZWL) to "7294,23",
        CurrencyAmount("2452,21", IRR) to "2452,21",
        CurrencyAmount("345,987", IQD) to "345,987",

        // Insufficient or extra decimal digits.
        CurrencyAmount("0", USD) to "0,00",
        CurrencyAmount("0,", USD) to "0,00",
        CurrencyAmount("0,1", USD) to "0,10",
        CurrencyAmount("1200", USD) to "1200,00",
        CurrencyAmount("550,123", UAH) to "550,12",
        CurrencyAmount("550,1234", USD) to "550,12",
        CurrencyAmount("345", IQD) to "345,000",
        CurrencyAmount("345,8", IQD) to "345,800",
        CurrencyAmount("345,87", IQD) to "345,870",
        CurrencyAmount("345,87212", IQD) to "345,872",
        CurrencyAmount("5,", BIF) to "5",
        CurrencyAmount("92898,", BIF) to "92898",
        CurrencyAmount("92898,5", BIF) to "92898",
        CurrencyAmount("92898,57", BIF) to "92898",

        // Missing amount digits.
        CurrencyAmount(",", UAH) to ",00",
        CurrencyAmount(",2", UAH) to ",20",
        CurrencyAmount(",35", UAH) to ",35",
        CurrencyAmount(",345", UAH) to ",34",
        CurrencyAmount("", BIF) to "",
        CurrencyAmount(",", BIF) to "",

        // Extra whitespace characters.
        CurrencyAmount(" 98,76", USD) to "98,76",
        CurrencyAmount("  9838, 92 ", USD) to "9838,92",
        CurrencyAmount("   92898  ", BIF) to "92898",
        CurrencyAmount("   9  8 \t 5  ,  7  6  \n", USD) to "985,76",
        CurrencyAmount("    299 ,\t98\n \r", USD) to "299,98",

        // Empty values.
        CurrencyAmount("", USD) to "",
        CurrencyAmount("   ", USD) to "",
        CurrencyAmount("\t", USD) to "",
        CurrencyAmount(" \t\t  \n\r \t ", USD) to "",

        // Extra decimal separators.
        CurrencyAmount("23652,36,", USD) to "23652,36",
        CurrencyAmount("236,523,6", USD) to "236,52",
        CurrencyAmount(",23652,36", USD) to ",23",
        CurrencyAmount("23,6,,523,6,", USD) to "23,65",
        CurrencyAmount(",,", USD) to ",00",
        CurrencyAmount("23652,36,", USD) to "23652,36",

        // Extra random characters.
        CurrencyAmount("23s652,3g6h", USD) to "23652,36",
        CurrencyAmount("23lks+ 65-^фі5}2,aa", USD) to "236552,00",
    )
}

private data class CurrencyAmount(val amount: String, val currency: Currency)
