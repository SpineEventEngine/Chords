/*
 * Copyright 2025, TeamDev. All rights reserved.
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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType.Companion.Press
import androidx.compose.ui.input.pointer.PointerIcon.Companion.Hand
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace
import androidx.compose.ui.text.font.FontWeight.Companion.SemiBold
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.unit.dp
import io.spine.chords.core.ComponentSetup
import io.spine.chords.core.DropdownListBox
import io.spine.chords.core.DropdownListBoxScope
import io.spine.chords.core.keyboard.KeyRange.Companion.Digit
import io.spine.chords.core.keyboard.key
import io.spine.chords.core.keyboard.matches
import io.spine.chords.core.InputField
import io.spine.chords.core.InputReviser
import io.spine.chords.core.RawTextContent
import io.spine.chords.core.exceptionBasedParser
import io.spine.money.Currency
import io.spine.money.Currency.CURRENCY_UNDEFINED
import io.spine.money.Currency.UNRECOGNIZED
import io.spine.money.Currency.USD
import io.spine.money.Money
import io.spine.chords.proto.value.money.decimalSeparator
import io.spine.chords.proto.value.money.formatAmount
import io.spine.chords.proto.value.money.options
import io.spine.chords.proto.value.money.parseAmount
import kotlin.math.abs

/**
 * Default money currency to be selected in [MoneyField].
 */
private val defaultCurrency = USD

/**
 * A field that allows entering [Money] values.
 */
public class MoneyField : InputField<Money>() {
    public companion object : ComponentSetup<MoneyField>({ MoneyField() })

    /**
     * A list of item values to choose from.
     */
    private val currencies: Iterable<Currency> = Currency.values().filter {
        it != UNRECOGNIZED && it != CURRENCY_UNDEFINED
    }

    /**
     * List of currency items filtered by the current search substring.
     */
    private var filteredCurrencyItems by mutableStateOf(currencies.toList())

    init {
        label = "Money"
    }

    /**
     * Indicates whether the drop-down menu is expanded or not.
     */
    private val expanded = mutableStateOf(false)

    /**
     * A currently selected currency.
     */
    private var selectedCurrency by mutableStateOf(defaultCurrency)

    init {
        inputReviser = MoneyFieldReviser(defaultCurrency)
    }

    @Composable
    @ReadOnlyComposable
    override fun beforeComposeContent() {
        super.beforeComposeContent()
        inputReviser = MoneyFieldReviser(selectedCurrency)
        textStyle = LocalTextStyle.current.copy(fontFamily = Monospace)
    }

    override fun parseValue(rawText: String): Money = exceptionBasedParser(
        IllegalArgumentException::class,
        "Invalid format"
    ) {
        Money::class.parseAmount(rawText, selectedCurrency)
    }

    override fun formatValue(value: Money): String = value.formatAmount()

    @Composable
    override fun content() {
        val currentFocusManager = LocalFocusManager.current

        DropdownListBox<Currency> {
            items = filteredCurrencyItems
            selectedItem = selectedCurrency
            onSelectItem = ::onSelectCurrencyItem
            expanded = this@MoneyField.expanded
            searchSelectionEnabled = true
            onSearchSelectionChange = { filteredCurrencyItems = getFilteredItems(it) }
            unfocusInvoker = { currentFocusManager.clearFocus() }
            focusInvoker = { this@MoneyField.focusRequester.requestFocus() }
            noneItemEnabled = false
            itemContent = {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = CenterVertically
                ) {
                    Text(
                        text = it.name,
                        modifier = Modifier.width(50.dp),
                        fontWeight = SemiBold
                    )
                    Text(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
                        text = it.options.name
                    )
                }
            }
            invoker = {
                FieldContent()
            }
        }
    }

    /**
     * The actual money field, which is rendered as the "invoker" inside
     * `DropdownListBox` in this component.
     */
    @Composable
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    private fun DropdownListBoxScope.FieldContent() {
        val exponentDigits = selectedCurrency.options.exponentDigits

        promptText = if (exponentDigits > 0) {
            "0$decimalSeparator${"0".repeat(exponentDigits)}"
        } else {
            "0"
        }

        prefix = {
            Text(text = selectedCurrency.options.symbol, fontFamily = Monospace)
        }

        suffix = {
            Row(
                modifier = Modifier.pointerHoverIcon(Hand)
                    .onPointerEvent(Press) { this@MoneyField.expanded.value = true },
                verticalAlignment = CenterVertically
            ) {
                Text(selectedCurrency.name)
                TrailingIcon(this@MoneyField.expanded.value)
            }
        }

        supportingText = {
            Text(text = it, modifier = Modifier.onGloballyPositioned { coordinates ->
                adjustPositionBasedOnSupportingTextHeight(coordinates.size.height)
            })
        }

        Box(modifier = Modifier.onPreviewKeyEvent { handleKeyEvent(it) }) {
            super.content()
        }
    }

    /**
     * Retrieves a filtered list of currency items
     * based on a search string value.
     *
     * @param searchString
     *         the search string to filter currencies by.
     * @return list of currency items that match the filtering criteria.
     */
    private fun getFilteredItems(searchString: String): List<Currency> {
        val trimmedSearchString = searchString.trim()
        if (trimmedSearchString == "") {
            return currencies.toList()
        }
        return currencies.filter {
            it.name.contains(trimmedSearchString, true) ||
            it.options.name.contains(trimmedSearchString, true)
        }
    }

    /**
     * Selects currency item from the drop-down menu.
     *
     * @param item A drop-down menu currency item to be selected.
     */
    private fun onSelectCurrencyItem(item: Currency?) {
        expanded.value = false

        if (item == null || selectedCurrency == item) {
            return
        }

        selectedCurrency = item

        if (value.value == null || !valid.value) {
            return
        }

        try {
            var nanos = ""
            val exponentDigits = selectedCurrency.options.exponentDigits
            if (exponentDigits > 0) {
                nanos = abs(value.value?.nanos ?: 0).toString()

                if (nanos.length > exponentDigits) {
                    nanos = nanos.substring(0, exponentDigits)
                }
            }
            val moneyWithSelectedCurrency = Money::class.parseAmount(
                "${value.value?.units}$decimalSeparator${nanos}",
                selectedCurrency
            )

            value.value = moneyWithSelectedCurrency
        } catch (
            // Exception value itself is not needed.
            @Suppress("SwallowedException")
            e: IllegalArgumentException
        ) {
            check(false) {
                "Encountered unexpected null value for money during parsing operation."
            }
        }
    }
}

/**
 * An [InputReviser] that is used to revise the entered input text and
 * to stop propagation of specific key events for [MoneyField].
 *
 * @property currency
 *         a money amount currency.
 */
internal class MoneyFieldReviser(
    private val currency: Currency
) : InputReviser {

    override fun reviseRawTextContent(
        current: RawTextContent,
        candidate: RawTextContent
    ): RawTextContent {
        var updatedCandidate = candidate.copy(
            candidate.text.replace('.', decimalSeparator)
        )
        if (current.text != updatedCandidate.text &&
            current.text.isNotEmpty()
        ) {
            updatedCandidate =
                if (current.selection.collapsed) {
                    updateCandidateWhenTextIsNotSelected(current, updatedCandidate)
                } else {
                    updateCandidateWhenTextIsSelected(current, updatedCandidate)
                }
        }
        return sanitizeMoneyField(current, updatedCandidate)
    }

    override fun filterKeyEvent(keyEvent: KeyEvent): Boolean {
        return keyEvent matches (!Digit and !decimalSeparator.key and !'.'.key).typed
    }

    /**
     * Updates user's input when entered text replaces the one selected by user.
     *
     * Check is being done to see if selected text that is being replaced
     * contains decimal separator, if yes, and if new entered text doesn't
     * contain decimal separator, input value is modified to preserve decimal
     * separator in it.
     *
     * @param current A [RawTextContent] that encapsulates current
     *   text input value and cursor position.
     * @param candidate A [RawTextContent] that encapsulates
     *   updated text input value and updated cursor position.
     * @return [RawTextContent] which contains updated text input and updated
     *   cursor input position.
     */
    private fun updateCandidateWhenTextIsSelected(
        current: RawTextContent,
        candidate: RawTextContent
    ): RawTextContent {
        if (current.selection.length == current.text.length) {
             return candidate
        }

        val selectedText = current.getSelectedText()
        val replaceWithText = candidate.text.substring(
            current.selection.min,
            candidate.selection.start
        )
        val decimalSeparatorIndex = current.text.indexOfFirst { it == decimalSeparator }
        return when {
            (!replaceWithText.contains(decimalSeparator) &&
                selectedText.contains(decimalSeparator)
            ) -> {
                val startIndex = candidate.selection.start
                val updatedRawText = candidate.text.substring(0, startIndex) +
                        decimalSeparator +
                        candidate.text.substring(startIndex)

                RawTextContent(updatedRawText, TextRange(startIndex))
            }

            decimalSeparatorIndex < current.selection.min -> {
                updateDecimalPart(current, candidate)
            }

            else -> {
                candidate
            }
        }
    }

    /**
     * Updates user's input when user doesn't select any input text
     * and just enters new one.
     *
     * Check is being done to see if user is replacing some character and
     * if that character is decimal separator resulting input value will
     * preserve separator, it will just update cursor position.
     *
     * @param current A [RawTextContent] that encapsulates current text input
     *   value and cursor position.
     * @param candidate A [RawTextContent] that encapsulates updated text input
     *   value and updated cursor position.
     * @return [RawTextContent] which contains updated text input and
     *   updated cursor input position.
     */
    private fun updateCandidateWhenTextIsNotSelected(
        current: RawTextContent,
        candidate: RawTextContent
    ): RawTextContent {
        val decimalSeparatorIndex =
            current.text.indexOfFirst { it == decimalSeparator }
        return when {
            candidate.selection.start <= current.selection.start -> {
                val deletedChar = current.text[candidate.selection.start]
                when {
                    deletedChar == decimalSeparator -> {
                        val textRange = TextRange(candidate.selection.start)
                        RawTextContent(current.text, textRange)
                    }

                    candidate.selection.start > decimalSeparatorIndex -> {
                        val remainingDecimalPartIndex =
                            current.selection.min + (1.takeIf {
                                current.selection.collapsed &&
                                        candidate.selection.collapsed &&
                                        current.selection.start == candidate.selection.start
                            } ?: 0)

                        val updatedRawText =
                            current.text.substring(0, candidate.selection.min) +
                                    "0" + current.text.substring(remainingDecimalPartIndex)

                        candidate.copy(updatedRawText)
                    }

                    else -> {
                        candidate
                    }
                }
            }

            decimalSeparatorIndex < current.selection.min -> {
                updateDecimalPart(current, candidate)
            }

            else -> {
                candidate
            }
        }
    }

    /**
     * Revise user's input when user modifies decimal part of money value.
     *
     * @param current A [RawTextContent] that encapsulates current text input
     *   value and cursor position.
     * @param rawCandidate A [RawTextContent] that encapsulates updated text
     *   input value and updated cursor position.
     * @return [RawTextContent] which contains revised text input and
     *   updated cursor input position.
     */
    private fun updateDecimalPart(
        current: RawTextContent,
        rawCandidate: RawTextContent
    ): RawTextContent {
        val beforeSelection = current.text.substring(0, current.selection.min)

        val atSelection = rawCandidate.text.substring(
            current.selection.min,
            rawCandidate.selection.max
        )

        val afterSelection = if (current.text.length >= rawCandidate.selection.max)
            current.text.substring(
                rawCandidate.selection.max,
                current.text.length
            ) else ""

        return rawCandidate.copy(beforeSelection + atSelection + afterSelection)
    }

    /**
     * Corrects the human-readable string representation of [Money] according to
     * some basic limitations of how the money string should be formatted.
     *
     * Note that depending on the value of [candidate] input
     * string, this function doesn't necessarily return [RawTextContent] with
     * valid money string, e.g. when the input string doesn't
     * include a currency symbol or an amount value.
     *
     * The way how this function works makes it useful for "live" correction of
     * a partially (or fully) entered money string as the user edits it. This
     * intent shapes the kind of corrections made by this function, for example:
     * - It makes some obvious corrections to already
     *   specified parts of data, e.g.:
     *   - removes leading/trailing whitespace characters, as well as any
     *     non-digit characters from the numeric part of the string that
     *     contains the monetary amount itself;
     *   - removes extra decimal separator symbols;
     *   - ensures that the amount value contains the number of decimal digits
     *     that corresponds to the chosen currency (if it was specified in
     *     [rawTextCandidate] text value).
     * - It preserves right behavior of input cursor in certain situations
     *   (See: https://github.com/Projects-tm/1DAM/issues/67):
     *   - ensures correct behavior when user tries to remove digital separator,
     *     it preserves it and only updates input cursor position
     *   - ensures that when user enters non-digit characters in the middle of
     *     amount part, such characters are not shown and cursor position
     *     remains the same.
     * - It generally doesn't try to compensate for the missing data, e.g. it
     *   doesn't specify a missing monetary amount (for example to zero or
     *   something else).
     *
     * Having mentioned the above, it should generally be noted that, depending
     * on the [candidate] input text, this method doesn't
     * necessarily result in a [RawTextContent] with a string value,
     * which can be interpreted as a valid money string.
     *
     * An empty string is interpreted as an absence of a value and is
     * considered as a valid value, and thus results in returning a
     * [RawTextContent] with empty string value as well.
     *
     * @param current A [RawTextContent] that encapsulates current text input
     *   value and cursor position.
     * @param candidate A [RawTextContent] that encapsulates updated text input
     *   value and cursor position that should be sanitized.
     * @return [RawTextContent] which holds updated cursor position and
     *         sanitized modification of [rawTextCandidate] text value.
     */
    private fun sanitizeMoneyField(
        current: RawTextContent,
        candidate: RawTextContent
    ): RawTextContent {
        val trimmedStr = candidate.text.trim()
        if (trimmedStr.isEmpty()) {
            return RawTextContent("")
        }

        val exponentDigits = currency.options.exponentDigits

        val (sanitizedAmount, inputCursorOffset) = sanitizeAmount(
            current,
            candidate,
            trimmedStr,
            exponentDigits
        )
        val amountParts = sanitizedAmount.split(decimalSeparator)

        val newIntPart = amountParts[0]
        val newDecimalPart = calculateNewDecimalPart(amountParts, exponentDigits)

        val optionalDecimalSeparator = if (exponentDigits > 0) decimalSeparator else ""
        val sanitizedText = "$newIntPart$optionalDecimalSeparator$newDecimalPart"

        return RawTextContent(
            sanitizedText, TextRange(
                candidate.selection.start - inputCursorOffset,
                candidate.selection.end - inputCursorOffset
            )
        )
    }

    /**
     * Sanitizes amount part of money string, which includes removing
     * non-digit characters which are not first detected decimal separator
     * character, and based on those removed characters calculates by how much
     * input cursor position should be updated.
     *
     * Calculates an input cursor offset which represents number of whitespace
     * characters that can be trimmed in new text input value, and it calculates
     * trim start offset which is number of whitespace characters
     * at the beginning of new text input value.
     *
     * @param currentRawTextContent
     *         a current [RawTextContent], which is used to determine if user
     *         has selected part of input text that starts from the beginning
     *         of the input which is getting modified. If yes, then check is
     *         being done to see if there are any whitespace characters at
     *         the beginning of new text input value [rawTextCandidate].
     * @param rawTextContentCandidate
     *         a [RawTextContent] that holds new value of text that user
     *         entered, which is used to check if it contains whitespace
     *         characters that should be trimmed.
     * @param amountStr
     *         an amount part of money string that is being sanitized.
     * @param exponentDigits
     *         a number of digits after the decimal separator of a given
     *         currency.
     * @return a pair of string and integer, where string represents sanitized
     *         amount value and integer represents offset that should be
     *         applied to input cursor position.
     */
    private fun sanitizeAmount(
        currentRawTextContent: RawTextContent,
        rawTextContentCandidate: RawTextContent,
        amountStr: String,
        exponentDigits: Int
    ): Pair<String, Int> {
        var inputCursorOffset = 0
        var trimStartOffset = 0
        if (currentRawTextContent.selection.start == 0 ||
            currentRawTextContent.selection.end == 0
        ) {
            trimStartOffset = rawTextContentCandidate.text.indexOfFirst { !it.isWhitespace() }
            inputCursorOffset = trimStartOffset
        }
        inputCursorOffset += rawTextContentCandidate.text.length -
                (rawTextContentCandidate.text.indexOfLast { !it.isWhitespace() } + 1)

        var decimalSeparatorAlreadyIncluded = false
        var decimalOffset = 0
        val sanitizedAmount = amountStr.filterIndexed { index, char ->
            when {
                char.isDigit() -> {
                    if (decimalSeparatorAlreadyIncluded) {
                        if (decimalOffset != exponentDigits) {
                            decimalOffset++
                            true
                        } else {
                            false
                        }
                    } else {
                        true
                    }
                }

                char == decimalSeparator -> {
                    val includeThisSeparator = !decimalSeparatorAlreadyIncluded
                    decimalSeparatorAlreadyIncluded = true
                    val inputTextPosition = index + trimStartOffset
                    if (!includeThisSeparator && decimalOffset != exponentDigits &&
                        inputTextPosition < rawTextContentCandidate.selection.start
                    ) {
                        inputCursorOffset++
                    }
                    includeThisSeparator
                }

                else -> {
                    val inputTextPosition = index + trimStartOffset
                    if (decimalOffset != exponentDigits &&
                        inputTextPosition < rawTextContentCandidate.selection.start
                    ) {
                        inputCursorOffset++
                    }
                    false
                }
            }
        }

        return Pair(sanitizedAmount, inputCursorOffset)
    }

    /**
     * Returns amount after decimal separator which is filled with additional
     * '0's if size of decimal part candidate is less than it is defined by
     * currency, or empty string if currency doesn't have decimal separator.
     *
     * @param amountParts
     *         holds decimal part candidate.
     * @param exponentDigits
     *         number of digits after decimal separator defined by currency.
     * @return decimal part amount or empty string.
     */
    private fun calculateNewDecimalPart(amountParts: List<String>, exponentDigits: Int): String {
        if (exponentDigits > 0) {
            val decimalPart = if (amountParts.size > 1) amountParts[1] else ""
            return decimalPart.substring(0, Integer.min(exponentDigits, decimalPart.length))
                .padEnd(exponentDigits, '0')
        }

        return ""
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is MoneyFieldReviser) {
            return false
        }

        return currency == other.currency
    }

    override fun hashCode(): Int {
        return currency.hashCode()
    }
}
