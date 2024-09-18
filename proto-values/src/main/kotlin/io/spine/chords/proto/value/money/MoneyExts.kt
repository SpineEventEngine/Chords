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

package io.spine.chords.proto.value.money

import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.EnumValueDescriptor
import io.spine.money.Currency
import io.spine.money.Currency.CURRENCY_UNDEFINED
import io.spine.money.Currency.UNRECOGNIZED
import io.spine.money.CurrencyOptions
import io.spine.money.Money
import io.spine.money.MoneyProto
import io.spine.util.Exceptions.newIllegalArgumentException
import java.text.DecimalFormatSymbols
import java.util.*
import kotlin.math.abs
import kotlin.reflect.KClass

/**
 * Represents the number of digits denoting nano (10^-9) units of the amount.
 */
private const val NanosDigits = 9

/**
 * Parses the given human-readable string [Money] value.
 *
 * @param str
 *         a string that should be parsed as [Money].
 * @param currency
 *         a money currency for given string.
 * @return the respective [Money] value.
 * @throws IllegalArgumentException
 *         if the given string cannot be parsed as a [Money] value.
 */
// The receiver is needed to specify a "static" context for the function.
@Suppress("UnusedReceiverParameter")
public fun KClass<Money>.parseAmount(str: String, currency: Currency): Money {
    val trimmedStr = str.trim()
    val currencyOptions = currency.options
    val parts = trimmedStr.split(decimalSeparator)

    require(parts.size <= 2) { "Couldn't parse money value: $trimmedStr" }

    val unitsStr = parts[0]
    val nanosStr = if (parts.size > 1) parts[1] else 0.toString()

    if (currencyOptions.exponentDigits > 0) {
        require(nanosStr.length <= currencyOptions.exponentDigits) {
            "The money string ($trimmedStr) has too many decimal digits for this currency " +
                    "(a max of $currencyOptions.exponentDigits is allowed)."
        }
    }

    val units = try {
        unitsStr.toLong()
    } catch (e: NumberFormatException) {
        throw IllegalArgumentException("Couldn't parse money value: $trimmedStr", e)
    }
    val nanos = if (currencyOptions.exponentDigits > 0) {
        try {
            val absoluteNanos = nanosStr
                .padEnd(NanosDigits, '0')
                .toInt()
            if (units >= 0) absoluteNanos else -absoluteNanos
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Couldn't parse money value: $trimmedStr", e)
        }
    } else {
        0
    }

    return Money.newBuilder()
        .setUnits(units)
        .setNanos(nanos)
        .setCurrency(currency)
        .vBuild()
}

/**
 * Parses a human-readable money string with the purpose of identifying
 * the currency from this string, without paying attention to the actual
 * monetary amount.
 *
 * @param str
 *         a human-readable money string, which is expected to contain
 *         the currency symbol at the beginning.
 * @return [Currency], which was identified or `null`, if it couldn't
 *         be identified.
 */
public fun identifyCurrency(str: String): Currency? {
    val trimmedStr = str.trim()
    require(trimmedStr.isNotEmpty()) { "Couldn't parse an empty string as Money." }

    val currency = Currency.values().find {
        if (it == UNRECOGNIZED || it == CURRENCY_UNDEFINED) {
            false
        } else {
            val symbol = it.options.symbol
            trimmedStr.startsWith(symbol)
        }
    }
    return currency
}

/**
 * Gets a decimal separator character.
 */
public val decimalSeparator: Char get() = DecimalFormatSymbols.getInstance().decimalSeparator

/**
 * Formats the [Money] value as a respective human-readable string
 * without currency symbol.
 *
 * @receiver money instance that should be formatted.
 * @return a human-readable string representation of this [Money] value.
 */
public fun Money.formatAmount(): String {
    val currencyOptions = currency.options
    val exponentDigits = currencyOptions.exponentDigits
    val nanoSuffix = when {
        exponentDigits > 0 ->
            decimalSeparator + String.format(Locale.ROOT, "%0${NanosDigits}d", abs(nanos))
                .substring(0, exponentDigits)

        else ->
            ""
    }
    return "$units$nanoSuffix"
}

/**
 * Obtains a [CurrencyOptions] value associated with this currency, which
 * describes the parameters characterizing the currency.
 *
 * @receiver the [Currency] value whose [CurrencyOptions] should be retrieved.
 * @return the respective [CurrencyOptions] value.
 * @throws IllegalArgumentException if this [Currency] doesn't have
 *          an associated [CurrencyOptions] value.
 */
// TODO:2023-11-03:dmitry.pikhulya:
//      A case for code generation that can be considered:
//      The reason that prevents the efficient implementation of this property
//      is an inability to implement value caching due to an absence of backing
//      fields for extension properties in Kotlin. Hence code generation could
//      hopefully assist with that.
//      See https://github.com/Projects-tm/1DAM/issues/41
public val Currency.options: CurrencyOptions get() {
    val rawOptions = enumDescriptor().options
    val optionsValue = rawOptions.getExtension(MoneyProto.currency)
    checkNotNull(optionsValue) { "No CurrencyOptions found for this currency" }
    return optionsValue
}

/**
 * Enum descriptor for the [Currency] Proto type.
 *
 * @see [Descriptors.EnumDescriptor]
 */
private object CurrencyDescriptor {
    val value: Descriptors.EnumDescriptor = run {
        val defaultMoneyInstance = Money.getDefaultInstance()
        defaultMoneyInstance.currency.descriptorForType
    }
}

/**
 * Finds the enum value descriptor for the `Currency` instance.
 *
 * @receiver the `Currency` instance whose respective enum descriptor should
 *           be obtained.
 * @throws IllegalArgumentException if there is no `Currency` value found.
 */
private fun Currency.enumDescriptor(): EnumValueDescriptor {
    return CurrencyDescriptor.value.findValueByName(this.name)
        ?: throw newIllegalArgumentException(
            "There is no `Currency` value found for passed instance '%s'",
            this
        )
}
