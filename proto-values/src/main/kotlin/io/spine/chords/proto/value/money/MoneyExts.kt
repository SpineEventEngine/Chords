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

package io.spine.chords.proto.value.money

import io.spine.money.Currencies
import io.spine.money.Currency
import io.spine.money.CurrencyInfo
import io.spine.money.Money
import java.text.DecimalFormatSymbols
import kotlin.math.abs
import kotlin.reflect.KClass

/**
 * The decimal separator of the current locale.
 */
public val decimalSeparator: Char
    get() = DecimalFormatSymbols.getInstance().decimalSeparator

/**
 * The precision of Spine Money's fractional amount, in atto (10^-18) units.
 */
private const val AttoDigits = 18

/**
 * Parses a localized amount using the currency's fraction digits, up to 18 places.
 * Currencies without minor units discard the fractional input, as in previous releases.
 *
 * @param str The amount without a currency symbol or code.
 * @param currency The currency of the resulting amount.
 * @throws IllegalArgumentException If the amount or its precision is invalid.
 */
@Suppress("UnusedReceiverParameter") // The receiver provides a static parsing context.
public fun KClass<Money>.parseAmount(str: String, currency: Currency): Money {
    val trimmed = str.trim()
    val parts = trimmed.split(decimalSeparator)
    require(parts.size <= 2) { "Couldn't parse money value: $trimmed" }
    val fractionDigits = currency.fractionDigits().coerceAtLeast(0)
    require(fractionDigits <= AttoDigits) { "Money supports at most $AttoDigits fraction digits." }
    val fraction = parts.getOrElse(1) { "" }
    if (fractionDigits > 0) {
        require(fraction.length <= fractionDigits) {
            "The money string ($trimmed) has too many decimal digits for this currency " +
                    "(a max of $fractionDigits is allowed)."
        }
    }
    val units: Long
    val attos: Long
    try {
        units = parts[0].toLong()
        val absoluteAttos = if (fractionDigits > 0) {
            fraction.padEnd(AttoDigits, '0').toLong()
        } else {
            0L
        }
        attos = if (trimmed.startsWith('-')) -absoluteAttos else absoluteAttos
    } catch (e: NumberFormatException) {
        throw IllegalArgumentException("Couldn't parse money value: $trimmed", e)
    }
    return Money.newBuilder()
        .setCurrency(currency)
        .setUnits(units)
        .setAttos(attos)
        .build()
}

/**
 * Finds a JDK currency whose symbol starts the given text, in currency-code order.
 *
 * @throws IllegalArgumentException If the text is blank.
 */
public fun identifyCurrency(str: String): Currency? {
    val trimmed = str.trim()
    require(trimmed.isNotEmpty()) { "Couldn't parse an empty string as Money." }
    return java.util.Currency.getAvailableCurrencies()
        .sortedBy { it.currencyCode }
        .map { Currencies.of(it.currencyCode) }
        .find { trimmed.startsWith(it.info().symbol) }
}

/**
 * Formats the localized amount prefixed by its currency code.
 */
public fun Money.format(): String = "${currency.code} ${formatAmount()}"

/**
 * Formats the amount with its currency's fraction digits and the current decimal separator.
 * Preserves the sign of negative amounts smaller than one unit.
 */
public fun Money.formatAmount(): String {
    val fractionDigits = currency.fractionDigits().coerceAtLeast(0)
    require(fractionDigits <= AttoDigits) { "Money supports at most $AttoDigits fraction digits." }
    val fraction = if (fractionDigits > 0) {
        decimalSeparator + abs(attos).toString().padStart(AttoDigits, '0').take(fractionDigits)
    } else {
        ""
    }
    val sign = if (units == 0L && attos < 0L) "-" else ""
    return "$sign$units$fraction"
}

/**
 * Obtains currency information from Spine's registered providers, including the JDK provider.
 *
 * @throws IllegalArgumentException If the currency code is invalid or unknown.
 */
public val Currency.options: CurrencyInfo
    get() = info()
