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

package io.spine.money.given

import io.spine.money.Currencies
import io.spine.money.Currencies.USD
import io.spine.money.Currency
import io.spine.money.Money

/**
 * Provides amounts for localized money parsing and formatting tests.
 */
internal object MoneySpecEnv {
    /**
     * The BIF currency used by the existing amount cases.
     */
    val BIF: Currency = Currencies.of("BIF")

    /**
     * The IQD currency used by the existing amount cases.
     */
    val IQD: Currency = Currencies.of("IQD")

    /**
     * The IRR currency used by the existing amount cases.
     */
    val IRR: Currency = Currencies.of("IRR")

    /**
     * The PYG currency used by the existing amount cases.
     */
    val PYG: Currency = Currencies.of("PYG")

    /**
     * The TZS currency used by the existing amount cases.
     */
    val TZS: Currency = Currencies.of("TZS")

    /**
     * The UAH currency used by the existing amount cases.
     */
    val UAH: Currency = Currencies.of("UAH")

    /**
     * The ZWL currency used by the existing amount cases.
     */
    val ZWL: Currency = Currencies.of("ZWL")

    /**
     * Supplies invalid localized inputs for the parsing contract.
     */
    fun getInvalidSamplesWithExtraDecimalSeparators(): Map<CurrencyAmount, String> = mapOf(

        // Extra decimal separators.
        CurrencyAmount("23652,36,", USD) to "23652,36,",
        CurrencyAmount("236,523,6", USD) to "236,523,6",
        CurrencyAmount(",23652,36", USD) to ",23652,36",
        CurrencyAmount("23,6,,523,6,", USD) to "23,6,,523,6,",
        CurrencyAmount(",,", USD) to ",,",
        CurrencyAmount("23652,36,", USD) to "23652,36,"
    )

    /**
     * Supplies invalid localized inputs for the parsing contract.
     */
    fun getInvalidSamplesWithTooManyDecimalDigits(): Map<CurrencyAmount, String> = mapOf(

        // Extra decimal digits.
        CurrencyAmount("550,123", UAH) to "550,123",
        CurrencyAmount("550,1234", USD) to "550,1234",
        CurrencyAmount("345,87212", IQD) to "345,87212",

        // Extra whitespace characters.
        CurrencyAmount("  9838, 92 ", USD) to "9838, 92",
        CurrencyAmount("   9  8 \t 5  ,  7  6  \n", USD) to "9  8 \t 5  ,  7  6",
        CurrencyAmount("    299 ,\t98\n \r", USD) to "299 ,\t98",
    )

    /**
     * Supplies invalid localized inputs for the parsing contract.
     */
    fun getInvalidSamplesWithNonDigitsAmountPart(): Map<CurrencyAmount, String> = mapOf(

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

    /**
     * Supplies invalid localized inputs for the parsing contract.
     */
    fun getInvalidSamplesWithNonDigitsDecimalPart(): Map<CurrencyAmount, String> = mapOf(

        // Extra random characters.
        CurrencyAmount("23,aa", USD) to "23,aa"
    )

    /**
     * Supplies exact amounts, including currencies with zero and three fraction digits.
     */
    fun getValidSamples(): Map<CurrencyAmount, Money> = mapOf(

        // Valid dollar amounts.
        CurrencyAmount("0,00", USD) to money(USD, 0),
        CurrencyAmount("1,23", USD) to money(USD, 1, 230_000_000_000_000_000L),
        CurrencyAmount("98,76", USD) to money(USD, 98, 760_000_000_000_000_000L),
        CurrencyAmount("482,09", USD) to money(USD, 482, 90_000_000_000_000_000L),
        CurrencyAmount("5482,35", USD) to money(USD, 5482, 350_000_000_000_000_000L),
        CurrencyAmount("54982,29", USD) to money(USD, 54982, 290_000_000_000_000_000L),
        CurrencyAmount("9354982,74", USD) to money(USD, 9354982, 740_000_000_000_000_000L),

        // Negative amounts.
        CurrencyAmount("-10,00", USD) to money(USD, -10),
        CurrencyAmount("-10,45", USD) to money(USD, -10, -450_000_000_000_000_000L),
        CurrencyAmount("-293478", PYG) to money(PYG, -293478),

        // Valid amounts in other currencies.
        CurrencyAmount("230,75", UAH) to money(UAH, 230, 750_000_000_000_000_000L),
        CurrencyAmount("8230,75", UAH) to money(UAH, 8230, 750_000_000_000_000_000L),
        CurrencyAmount("82930,75", UAH) to money(UAH, 82930, 750_000_000_000_000_000L),
        CurrencyAmount("92898", BIF) to money(BIF, 92898),
        CurrencyAmount("5389", PYG) to money(PYG, 5389),
        CurrencyAmount("24,50", TZS) to money(TZS, 24, 500_000_000_000_000_000L),
        CurrencyAmount("9,99", TZS) to money(TZS, 9, 990_000_000_000_000_000L),
        CurrencyAmount("7294,23", ZWL) to money(ZWL, 7294, 230_000_000_000_000_000L),
        CurrencyAmount("2452,21", IRR) to money(IRR, 2452, 210_000_000_000_000_000L),
        CurrencyAmount("345,987", IQD) to money(IQD, 345, 987_000_000_000_000_000L),

        // Insufficient decimal digits.
        CurrencyAmount("0", USD) to money(USD, 0),
        CurrencyAmount("0,", USD) to money(USD, 0),
        CurrencyAmount("0,1", USD) to money(USD, 0, 100_000_000_000_000_000L),
        CurrencyAmount("1200", USD) to money(USD, 1200),
        CurrencyAmount("345", IQD) to money(IQD, 345),
        CurrencyAmount("345,8", IQD) to money(IQD, 345, 800_000_000_000_000_000L),
        CurrencyAmount("345,87", IQD) to money(IQD, 345, 870_000_000_000_000_000L),
        CurrencyAmount("5,", BIF) to money(BIF, 5),
        CurrencyAmount("92898,", BIF) to money(BIF, 92898),
        CurrencyAmount("92898,5", BIF) to money(BIF, 92898),
        CurrencyAmount("92898,57", BIF) to money(BIF, 92898),

        // Extra whitespace characters.
        CurrencyAmount(" 98,76", USD) to money(USD, 98, 760_000_000_000_000_000L),
        CurrencyAmount("   92898  ", BIF) to money(BIF, 92898)
    )

    /**
     * Supplies amounts and their localized display text.
     */
    fun getMoneyValues(): Map<Money, String> = mapOf(

        // Valid dollar amounts.
        money(USD, 0) to "0,00",
        money(USD, 1, 230_000_000_000_000_000L) to "1,23",
        money(USD, 98, 760_000_000_000_000_000L) to "98,76",
        money(USD, 482, 90_000_000_000_000_000L) to "482,09",
        money(USD, 5482, 350_000_000_000_000_000L)
                to "5482,35",
        money(USD, 54982, 290_000_000_000_000_000L)
                to "54982,29",
        money(USD, 9354982, 740_000_000_000_000_000L)
                to "9354982,74",

        // Valid amounts in other currencies.
        money(UAH, 230, 750_000_000_000_000_000L)
                to "230,75",
        money(UAH, 8230, 750_000_000_000_000_000L)
                to "8230,75",
        money(UAH, 82930, 750_000_000_000_000_000L)
                to "82930,75",
        money(BIF, 92898) to "92898",
        money(PYG, 5389) to "5389",
        money(TZS, 24, 500_000_000_000_000_000L) to "24,50",
        money(TZS, 9, 990_000_000_000_000_000L) to "9,99",
        money(ZWL, 7294, 230_000_000_000_000_000L)
                to "7294,23",
        money(IRR, 2452, 210_000_000_000_000_000L)
                to "2452,21",
        money(IQD, 345, 987_000_000_000_000_000L)
                to "345,987",

        // Insufficient decimal digits.
        money(USD, 0) to "0,00",
        money(USD, 0) to "0,00",
        money(USD, 0, 100_000_000_000_000_000L) to "0,10",
        money(USD, 1200) to "1200,00",
        money(IQD, 345) to "345,000",
        money(IQD, 345, 800_000_000_000_000_000L)
                to "345,800",
        money(IQD, 345, 870_000_000_000_000_000L)
                to "345,870",
        money(BIF, 5) to "5"
    )

    /**
     * Builds exact fixture values without repeating the builder chain in every sample.
     */
    private fun money(currency: Currency, units: Long, attos: Long = 0): Money =
        Money.newBuilder()
            .setCurrency(currency)
            .setUnits(units)
            .setAttos(attos)
            .build()

}

/**
 * Associates localized input with its currency.
 */
internal data class CurrencyAmount(val amount: String, val currency: Currency)
