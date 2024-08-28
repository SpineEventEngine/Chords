/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons.Filled
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType.Companion.Enter
import androidx.compose.ui.input.pointer.PointerEventType.Companion.Exit
import androidx.compose.ui.input.pointer.PointerEventType.Companion.Press
import androidx.compose.ui.input.pointer.PointerIcon.Companion.Hand
import androidx.compose.ui.input.pointer.PointerIcon.Companion.Text
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.spine.chords.primitive.moveFocusOnTab
import io.spine.chords.primitive.preventWidthAutogrowing
import java.util.*

/**
 * A base class for components, which allows selecting one item from a drop-down
 * items list.
 *
 * A concrete implementation has to provide the list of items to choose from by
 * implementing the [items] property. See the [InputComponent] documentation for
 * the description of a general component's API for accessing selected item's
 * value
 *
 * @param I
 *         a type of items from which the selection is made by this component.
 */
public abstract class DropdownSelector<I> : InputComponent<I>() {

    /**
     * A currently selected item.
     *
     * It can be read to identify the currently selected item or written in
     * order to make the component to display another item as the selected one.
     */
    public var selectedItem: I?
        get() {
            return value.value
        }
        set(i) {
            value.value = i
        }

    /**
     * A list of item values to choose from.
     */
    protected abstract val items: State<Iterable<I>>

    /**
     * A label for the component.
     */
    public open var label: String = ""

    /**
     * Indicates whether the field is enabled for receiving the user input.
     */
    public open var enabled: Boolean = true

    /**
     * A [Modifier] to be applied to the component.
     */
    public open var modifier: Modifier = Modifier

    /**
     * Indicates whether the drop-down menu is expanded or not.
     */
    private val expanded = mutableStateOf(false)

    /**
     * Represents the text entered into a drop-down field
     * used to filter dropdown items.
     */
    private var searchString by mutableStateOf("")

    /**
     * Represents the selection range of text (start and end indices)
     * within the drop-down field.
     */
    private var selection by mutableStateOf(TextRange(0))

    /**
     * Tracks whether the field is currently in a "dirty" state (has any
     * entry, no matter valid or not), as reported by the respective field
     * editor component.
     */
    private var dirty = false

    /**
     * The field's text style based on whether a drop-down item
     * is selected or not.
     */
    private val fieldTextStyle: TextStyle @Composable get() {
        val defaultTextStyle = LocalTextStyle.current
        return if (selectedItem != null) {
            defaultTextStyle
        } else {
            val defaultTextColor = defaultTextStyle.color
            defaultTextStyle.copy(color = defaultTextColor.copy(alpha = 0.5f))
        }
    }

    /**
     * Renders the composable content that should be displayed in a drop-down
     * list item identified by the provided parameters.
     *
     * This function can be overridden to customize what is displayed within
     * each drop-down list item. By default, this method displays the item's
     * text representation as defined by the [itemText] function, with
     * highlighting its portion that matches the [searchString] entered by
     * the user.
     *
     * @param item
     *         an item whose composable content should be rendered.
     * @param itemText
     *         an item's text representation as defined by
     *         the [itemText] function.
     */
    @Composable
    protected open fun itemContent(item: I, itemText: String) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp),
            text = itemText.annotateSubstring(
                searchString,
                SpanStyle(fontWeight = Bold)
            )
        )
    }

    @Composable
    @OptIn(ExperimentalComposeUiApi::class)
    override fun content() {
        val fieldText = getFieldText(searchString)

        SideEffect {
            updateDirtyState(fieldText)
        }

        DropdownListBox<I> {
            items = getFilteredItems(searchString)
            selectedItem = this@DropdownSelector.selectedItem
            noneItemEnabled = !valueRequired
            onSelectItem = ::onSelectItem
            expanded = this@DropdownSelector.expanded
            preselectNoneByDefault = searchString.trim().length == 0
            itemContent = {
                val itemText = itemText(it)
                itemContent(it, itemText)
            }
            invoker = { SelectorField() }
        }
    }

    @Composable
    @OptIn(ExperimentalComposeUiApi::class)
    private fun DropdownListBoxScope.SelectorField() {
        val validationErrorText = externalValidationMessage?.value
        TextField(
            value = TextFieldValue(getFieldText(searchString), selection),
            singleLine = true,
            onValueChange = { handleDropdownInputChange(it) },
            enabled = enabled,
            label = { Text(text = label) },
            isError = validationErrorText != null,
            supportingText = (validationErrorText ?: "").let {
                {
                    Text(
                        text = it,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            adjustPositionBasedOnSupportingTextHeight(coordinates.size.height)
                        })
                }
            },
            textStyle = fieldTextStyle,
            modifier = modifier
                .focusRequester(this@DropdownSelector.focusRequester)
                .moveFocusOnTab()

                // This approach with using `Modifier.onPointerEvent` is needed,
                // because `Modifier.clickable` won't work when `TextField`
                // is enabled.
                // See: https://github.com/JetBrains/compose-multiplatform/issues/220
                .onPointerEvent(Press) { handleClick() }
                .preventWidthAutogrowing()
                .onPreviewKeyEvent { handleKeyEvent(it) },
            trailingIcon = {
                TrailingIcons(
                    valueRequired,
                    selectedItem != null,
                    expanded.value
                )
            }
        )
    }

    /**
     * Get field text value based on current search string value.
     *
     * @param searchStringValue
     *         the current search string value.
     */
    private fun getFieldText(searchStringValue: String) = when {
        searchStringValue != "" -> searchStringValue
        selectedItem != null -> itemText(selectedItem!!)
        else -> ""
    }

    /**
     * A function, which has to be implemented to provide a text representation
     * for a given item.
     *
     * Item text representations are used for displaying item contents in
     * the list, and define the text by which items can be searched/filtered by
     * the user within the component's input field.
     *
     * @param item
     *         an item whose text representation should be obtained.
     * @return a text representation for the given item.
     */
    protected abstract fun itemText(item: I): String

    /**
     * Selects an item from the drop-down menu.
     *
     * @param item
     *         a drop-down menu item to be selected.
     */
    private fun onSelectItem(item: I?) {
        searchString = ""
        selectedItem = item
        selection = TextRange(if (item != null) itemText(item).length else 0)
        valueValid.value = true
        expanded.value = false
    }

    /**
     * Retrieves a filtered list of items based on a search string value.
     *
     * @param searchString
     *         the search string to filter items by.
     * @return list of items that match the filtering criteria.
     */
    private fun getFilteredItems(searchString: String): List<I> {
        val trimmedSearchString = searchString.trim()
        if (trimmedSearchString != "") {
            return items.value.filter {
                itemText(it).contains(
                    trimmedSearchString,
                    ignoreCase = true
                )
            }
        }

        return items.value.toList()
    }

    /**
     * Checks whether drop-down field is in "dirty" state, and if it is,
     * invokes [onDirtyStateChange] callback.
     *
     * @param fieldText
     *         a [String] representing text currently shown in drop-down
     *         input field.
     */
    private fun updateDirtyState(fieldText: String) {
        val newDirty = (fieldText != "")
        if (dirty != newDirty) {
            dirty = newDirty
            onDirtyStateChange?.invoke(newDirty)
        }
    }

    /**
     * Invoked when the user enters new text in a field in [DropdownSelector].
     *
     * @param searchValue
     *         a [TextFieldValue] which holds raw text content that was
     *         just modified by the user.
     */
    private fun handleDropdownInputChange(searchValue: TextFieldValue) {
        selection = searchValue.selection

        val newSearchString = searchValue.text.trimStart()
        if (selectedItem != null) {
            if (newSearchString == itemText(selectedItem!!)) {
                return
            }
        }

        if (searchString != newSearchString || selectedItem != null) {
            searchString = newSearchString
            selectedItem = null
            valueValid.value = (newSearchString == "")
            expanded.value = true
        }
    }
}

/**
 * Constructs [AnnotatedString] that contains [this] string, and contains its
 * part that matches the given [substring] annotated using the
 * specified [substringStyle].
 *
 * @receiver a string whose substring should be located and annotated.
 * @param substring
 *         a substring that should be searched for in this string.
 * @param substringStyle
 *         a [SpanStyle] that should be used for annotating this strings
 *         portion, which matches the given [substring] (if it was found).
 * @param caseSensitive
 *         `true` makes substring search to be case-sensitive, `false` makes
 *         it case-insensitive.
 * @return an `AnnotatedString` instance that contains [this] string with its
 *         respective [substring] annotated with [substringStyle].
 */
@Composable
private fun String.annotateSubstring(
    substring: String,
    substringStyle: SpanStyle,
    caseSensitive: Boolean = false
): AnnotatedString {
    val startIdx = if (caseSensitive) {
        this.indexOf(substring)
    } else {
        val locale = Locale.getDefault()
        this.lowercase(locale).indexOf(
            substring.lowercase(locale)
        )
    }

    return if (startIdx == -1) {
        AnnotatedString(this)
    } else {
        val endIdx = startIdx + substring.length
        AnnotatedString(
            this, listOf(
                Range(substringStyle, startIdx, endIdx)
            )
        )
    }
}

/**
 * The trailing `DropdownSelector`'s icons that are helpful to see and control
 * the component's state.
 *
 * @param valueRequired
 *         the value of `valueRequired` property of `DropdownSelector`.
 * @param containsValue
 *         `true`, if `DropdownSelector` currently contains a value, and
 *         `false` otherwise.
 * @param expanded
 *         `true`, if `DropdownSelector` is currently expanded, and
 *         `false` otherwise.
 */
@Composable
private fun DropdownListBoxScope.TrailingIcons(
    valueRequired: Boolean,
    containsValue: Boolean,
    expanded: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = CenterVertically
    ) {
        if (!valueRequired) {
            ClearValueIcon(containsValue)
        }
        DropdownExpansionIcon(expanded)
    }
}


/**
 * The `DropdownSelector`'s trailing icon, which allows to clear
 * the current selection.
 *
 * @param containsValue
 *         `true`, if `DropdownSelector` currently contains a value, and
 *         `false` otherwise.
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun DropdownListBoxScope.ClearValueIcon(containsValue: Boolean) {
    var isClearIconHovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .pointerHoverIcon(if (containsValue)  Hand else Text)
            .alpha(if (containsValue) 1f else 0f)
            .onPointerEvent(Press) { handleClearSelectedItemPress() }
            .onPointerEvent(Enter) { isClearIconHovered = true }
            .onPointerEvent(Exit) { isClearIconHovered = false }
            .background(
                if (isClearIconHovered) {
                    colorScheme.primary.copy(alpha = 0.1f)
                } else {
                    Transparent
                }
            )
    ) {
        Icon(Filled.Clear, contentDescription = null)
    }
}

/**
 * A trailing `DropdownSelector`'s icon, which displays its expansion state.
 *
 * @param expanded
 *         `true`, if `DropdownSelector` is currently expanded, and
 *         `false` otherwise.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
private fun DropdownExpansionIcon(expanded: Boolean) {
    var isTrailingIconHovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .pointerHoverIcon(Hand)
            .padding(end = 10.dp)
            .onPointerEvent(Enter) { isTrailingIconHovered = true }
            .onPointerEvent(Exit) { isTrailingIconHovered = false }
            .background(
                if (isTrailingIconHovered) {
                    colorScheme.primary.copy(alpha = 0.1f)
                } else {
                    Transparent
                }
            )
    ) {
        TrailingIcon(expanded)
    }
}
