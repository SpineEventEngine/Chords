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

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.text.TextRange
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.spine.chords.core.RawTextContent
import io.spine.chords.proto.value.money.options
import io.spine.money.Currency
import io.spine.money.Currency.BIF
import io.spine.money.Currency.IQD
import io.spine.money.Currency.IRR
import io.spine.money.Currency.PYG
import io.spine.money.Currency.TZS
import io.spine.money.Currency.UAH
import io.spine.money.Currency.USD
import io.spine.money.Currency.VND
import io.spine.money.Currency.ZWL
import java.awt.Panel
import java.awt.event.KeyEvent.KEY_TYPED
import java.awt.event.KeyEvent.VK_UNDEFINED
import java.text.DecimalFormatSymbols
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The decimal separator used by the current JVM locale.
 */
private val decimalSeparator get() = DecimalFormatSymbols.getInstance().decimalSeparator

/**
 * Tests input revision and key filtering for [MoneyField].
 */
@DisplayName("`MoneyField` should")
internal class MoneyFieldSpec {

    /**
     * Money text is normalized to the precision and character set of its currency.
     */
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

    /**
     * Fractional currencies must continue admitting a period for decimal input.
     */
    @Test
    fun `admit period for currency with fractional digits`() {
        USD.options.exponentDigits shouldBe 2
        val reviser = MoneyFieldReviser(USD)

        val consumed = reviser.filterKeyEvent(typedKeyEvent('.'))

        consumed shouldBe false
    }

    /**
     * Filtering a separator before text revision prevents zero-decimal amounts from truncating.
     */
    @Test
    fun `preserve zero-decimal amount and caret when separator is typed`() {
        VND.options.exponentDigits shouldBe 0
        val reviser = MoneyFieldReviser(VND)
        val amount = "1234567"
        val separators = setOf('.', decimalSeparator)
        val caretPositions = listOf(3, 0, amount.length)

        separators.forEach { separator ->
            caretPositions.forEach { position ->
                withClue("Typing '$separator' at caret position $position") {
                    val current = RawTextContent(amount, TextRange(position))

                    val result = applyTypedCharacter(reviser, current, separator)

                    result shouldBe current
                }
            }
        }
    }

    /**
     * Defines representative valid and invalid money strings for the sanitizer.
     *
     * @return Samples paired with their expected sanitized values.
     */
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

/**
 * A money string paired with the currency whose formatting rules apply to it.
 *
 * @property amount The raw money amount.
 * @property currency The currency that defines the amount precision.
 */
private data class CurrencyAmount(val amount: String, val currency: Currency)

/**
 * Creates a Compose key event for typing [character] on a desktop keyboard.
 *
 * @param character The character emitted by the event.
 * @return A Compose wrapper around a valid AWT `KEY_TYPED` event.
 */
private fun typedKeyEvent(character: Char): KeyEvent = KeyEvent(
    java.awt.event.KeyEvent(
        Panel(), KEY_TYPED, 0L, 0, VK_UNDEFINED, character
    )
)

/**
 * Models the preview-key filter followed by the text-change revision pipeline.
 *
 * @param reviser The reviser that filters and normalizes the typed character.
 * @param current The amount text and collapsed caret before typing.
 * @param character The character inserted at the current caret when admitted.
 * @return The unchanged [current] content if the key is consumed, or the revised candidate.
 */
private fun applyTypedCharacter(
    reviser: MoneyFieldReviser,
    current: RawTextContent,
    character: Char
): RawTextContent {
    if (reviser.filterKeyEvent(typedKeyEvent(character))) {
        return current
    }
    require(current.selection.collapsed) {
        "Typing is modelled only for a collapsed caret."
    }
    val caretPosition = current.selection.start
    val candidateText = current.text.substring(0, caretPosition) +
            character +
            current.text.substring(caretPosition)
    val candidate = RawTextContent(candidateText, TextRange(caretPosition + 1))
    return reviser.reviseRawTextContent(current, candidate)
}
