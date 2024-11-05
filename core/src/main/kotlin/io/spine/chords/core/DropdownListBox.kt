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

package io.spine.chords.core

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ShapeDefaults.ExtraSmall
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldDefaults.MinWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Offset.Companion.Zero
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.input.key.Key.Companion.DirectionDown
import androidx.compose.ui.input.key.Key.Companion.DirectionUp
import androidx.compose.ui.input.key.Key.Companion.Escape
import androidx.compose.ui.input.key.Key.Companion.MoveEnd
import androidx.compose.ui.input.key.Key.Companion.MoveHome
import androidx.compose.ui.input.key.Key.Companion.PageDown
import androidx.compose.ui.input.key.Key.Companion.PageUp
import androidx.compose.ui.input.key.Key.Companion.Tab
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation.Companion.None
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProviderAtPosition
import androidx.compose.ui.window.PopupProperties
import io.spine.chords.core.keyboard.KeyModifiers.Companion.Alt
import io.spine.chords.core.keyboard.KeyModifiers.Companion.Ctrl
import io.spine.chords.core.keyboard.KeyModifiers.Companion.Shift
import io.spine.chords.core.keyboard.KeyRange
import io.spine.chords.core.keyboard.key
import io.spine.chords.core.keyboard.matches
import io.spine.chords.core.primitive.VerticalScrollbar
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.lang.Character.UnicodeBlock
import java.lang.Character.UnicodeBlock.SPECIALS
import java.lang.Character.isISOControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A component that attaches a drop-down list attached to a custom composable
 * invoker, and allows selecting a single item in this list.
 *
 * The drop-down list is displayed when the user presses the Up/Down keys with
 * or without the Alt modifier (customizable with [expandKey] property).
 *
 * The items that should be displayed in the drop-down list should be provided
 * in the [items] property, and can be of any type. The way how each item is
 * rendered in the list is defined by the [itemContent] property.
 *
 * The currently selected item is tracked in the [selectedItem] property.
 *
 * This invoker can either consist of a single component, such as [TextField],
 * or a more complex composable content if needed, and it would typically
 * involve displaying of the currently selected item in some way. In order to
 * allow a proper list's expansion and selection behavior the way how the
 * invoker's composable content is specified has to adhere to certain
 * requirements:
 * - It needs to contain some focusable element and that focusable element has
 *   to delegate key events to the [DropdownListBox]. It has to be done by
 *   attaching [Modifier.onPreviewKeyEvent] on the respective focusable element,
 *   and the handler for this event should invoke the
 *   [DropdownListBoxScope.handleKeyEvent] function, which is available inside
 *   the [invoker]'s declaration.
 * - Mouse clicks have to be handled on the invoker with calling the
 *   [DropdownListBoxScope.handleClick] function.
 *
 * @param I
 *         a type of items displayed in the drop-down list.
 */
// All class's functions are better to have in this class.
@Suppress("TooManyFunctions", "LargeClass")
public class DropdownListBox<I> : Component() {
    public companion object : AbstractComponentDeclarationApi() {

        /**
         * Declares an instance of [DropdownListBox] with the respective
         * property value specifications.
         *
         * @param I
         *         a type of items displayed in the drop-down list.
         * @param props
         *         a lambda that is invoked on a component's instance, and
         *         should configure its properties in a way that is needed for
         *         this component's instance. It is invoked before each
         *         recomposition of the component.
         * @return a component's instance that has been created for this
         *         declaration site.
         */
        @Composable
        public operator fun <I> invoke(
            props: ComponentProps<DropdownListBox<I>>
        ): DropdownListBox<I> = createAndRender(props, { DropdownListBox() }) {
            Content()
        }
    }

    /**
     * Content to which the drop-down list is attached.
     *
     * It should contain some focusable element that would delegate keyboard
     * events by invoking the [DropdownListBoxScope.handleKeyEvent] function.
     */
    public lateinit var invoker: @Composable DropdownListBoxScope.() -> Unit

    /**
     * A list of items to display in the drop-down list.
     */
    public var items: Iterable<I> = emptyList()

    /**
     * Currently selected item in the drop-down list,
     * or `null` if none is selected.
     */
    public var selectedItem: I? = null

    /**
     * A function to be called when an item in the drop-down list is selected.
     */
    public lateinit var onSelectItem: (I?) -> Unit

    /**
     * A [MutableState] holding the current expanded state of
     * the drop-down list (`true` if the list is expanded, `false` otherwise).
     */
    public var expanded: MutableState<Boolean> = mutableStateOf(false)

    /**
     * A composable function, which is given a list's item value, and should
     * render the content that represents this item in the drop-down list.
     */
    public lateinit var itemContent: @Composable (item: I) -> Unit

    /**
     * Content to be shown when drop-down list doesn't have any items.
     */
    public var noItemsContent: @Composable () -> Unit = { Text("No such items") }

    /**
     * A text to be displayed for drop-down list none item.
     */
    public var noneItemText: String = "<None>"

    /**
     * Boolean which indicates if drop-down list should contain none item.
     */
    public var noneItemEnabled: Boolean = true

    /**
     * Specifies whether the "None" item has to be preselected by
     * default when the list is expanded (applicable only if `noneItemEnabled`
     * is `true`).
     *
     * If this property is `true`, expanding the list makes the first item to
     * be preselected by default. If this property is set to `false`, the first
     * item is preselected instead.
     */
    public var preselectNoneByDefault: Boolean = true


    /**
     * A [KeyRange], which specifies the key(s) used to expand
     * the drop-down list.
     */
    public var expandKey: KeyRange =
        Alt(DirectionDown.key) or DirectionDown.key or
                Alt(DirectionUp.key) or DirectionUp.key

    /**
     * Specifies whether the component should have a functionality that allows
     * the user to search item(s) by typing their content.
     *
     * If this property is set to `true`, then typing printable characters while
     * the dropdown is open allows the user to enter a built-in search string,
     * which invokes the [onSearchSelectionChange] event upon such search.
     */
    public var searchSelectionEnabled: Boolean = false

    /**
     * A function to be called when search selection text is changed.
     *
     * The specified callback receives a search string entered so far. A typical
     * callback's implementation could affect the [items] list provided to
     * the component (by filtering it accordingly).
     */
    public var onSearchSelectionChange: ((String) -> Unit)? = null

    /**
     * A lambda that should focus the [invoker].
     *
     * It needs to be specified when [searchSelectionEnabled] is `true` for to
     * improve the focus-related usability of this feature.
     */
    public var focusInvoker: (() -> Unit)? = null

    /**
     * A lambda that should remove the input focus from the [invoker].
     *
     * It needs to be specified when [searchSelectionEnabled] is `true` for to
     * improve the focus-related usability of this feature. An implementation
     * could typically invoke the [FocusManager.clearFocus] in this lambda.
     */
    public var unfocusInvoker: (() -> Unit)? = null

    /**
     * A density of the screen, it is used when calculating which part
     * of drop-down list should be visible to user.
     */
    private lateinit var density: Density

    /**
     * A list of previously displayed drop-down items.
     */
    private var previousItems: Iterable<I> = items

    /**
     * Index of currently selected item in drop-down list.
     *
     * If nothing is selected, index is -1.
     */
    private var selectedItemIndex by mutableStateOf(-1)

    /**
     * Index of currently preselected item in drop-down list.
     *
     * If nothing is preselected, index is `-1`. If [noneItemEnabled] property
     * is `true`, a value of `-1` means preselecting the "None" item.
     */
    private var preselectedItemIndex by mutableStateOf(-1)

    /**
     * Available height for drop-down list to occupy.
     */
    private var listAvailableHeight by mutableStateOf(0.dp)

    /**
     * Height of the visible list's part.
     */
    private var visibleListHeight: Dp by mutableStateOf(0.dp)

    /**
     * Height of visible layer's part in pixels (as derived
     * from [visibleListHeight]).
     */
    private val visibleListHeightPx: Int get() = with(density) { visibleListHeight.roundToPx() }

    /**
     * Extra offset to be added to drop-down list position, which differs
     * depending on if list is expanded below or above drop-down field.
     */
    private var listOffset by mutableStateOf(Zero)

    /**
     * Alignment of the drop-down list relative to desired position, which
     * differs depending on if list is expanded below or above drop-down field.
     */
    private var listAlignment by mutableStateOf(BottomEnd)

    /**
     * Drop-down scroll position.
     */
    private var scrollPositionRequested: Int? by mutableStateOf(0)

    /**
     * A [MutableMap] storing heights for each drop-down item.
     */
    private val itemHeights = mutableMapOf<Int, Int>()

    /**
     * Indicates whether the drop-down list is expanded or not, before
     * dismiss request happened.
     */
    private var expandedBeforeDismissRequest by mutableStateOf(false)

    /**
     * Height of supporting text if invoker has any.
     */
    private var supportingTextHeight by mutableStateOf(0)

    /**
     * Drop-down list offset from the top of the screen.
     */
    private var offsetFromTop by mutableStateOf(0)

    /**
     * Drop-down list offset from the bottom of the screen.
     */
    private var offsetFromBottom by mutableStateOf(0)

    /**
     * Height of invoker to which drop-down list is attached.
     */
    private var invokerHeight by mutableStateOf(0)

    /**
     * A [MutableState], whose value represents combined height of all
     * drop-down list items.
     */
    private var totalItemsHeight by mutableStateOf(0.dp)

    /**
     * Vertical padding of drop-down list content.
     */
    private val listVerticalPadding = 8.dp

    /**
     *  Heights of none item in drop-down list.
     */
    private var noneItemHeight by mutableStateOf(0)

    /**
     * Indicates if clear selected item icon is pressed.
     */
    private var clearSelectedItemPressed by mutableStateOf(false)

    /**
     * Indicates if search selection field should be shown.
     */
    private val showSearchSelection = mutableStateOf(false)

    /**
     * Current text value of search selection field.
     */
    private val searchSelectionText = mutableStateOf("")

    /**
     * Index of the first item, which either refers to the "None" item (a value
     * of `-1`), or the first data item (a value of `0`) depending on
     * the value of [noneItemEnabled].
     */
    private val firstItemIndex: Int get() = if (noneItemEnabled) -1 else 0

    @Composable
    @ReadOnlyComposable
    override fun beforeComposeContent() {
        super.beforeComposeContent()
        density = LocalDensity.current
    }

    @Composable
    override fun content() {
        val coroutineScope = rememberCoroutineScope()
        val scrollState = rememberScrollState(0)

        val expanded = expanded.value
        LaunchedEffect(expanded) {
            expandedBeforeDismissRequest = expanded

            if (expanded) {
                if (selectedItem != null) {
                    preselectedItemIndex = items.indexOfFirst { it == selectedItem }
                }
                if (selectedItem == null && noneItemEnabled) {
                    preselectedItemIndex = if (preselectNoneByDefault) -1 else 0
                }
            }
        }

        LaunchedEffect(noneItemEnabled) {
            if (!noneItemEnabled) {
                noneItemHeight = 0
            }
        }

        LaunchedEffect(expanded, showSearchSelection.value) {
            if (expanded && showSearchSelection.value) {
                unfocusInvoker?.invoke()
            }
        }

        selectedItemIndex = items.indexOfFirst { it == selectedItem }
        if (previousItems != items) {
            previousItems = items
            preselectedItemIndex = if (noneItemEnabled && preselectNoneByDefault) -1 else 0
        }

        val scope = DropdownListBoxScopeImpl(
            onInvokerKeyEvent = { handleDropdownKeyEvent(it) },
            onInvokerClick = { onInvokerClick() },
            onAdjustPositionBasedOnSupportingTextHeight = { supportingTextHeight = it },
            onClearSelectedItemPress = { onClearSelectedItemPress() }
        )

        Box {
            Box(modifier = Modifier
                .onGloballyPositioned { calculateDropdownListVerticalOffsets(it) }
            ) {
                scope.invoker()
            }

            if (expanded) {
                DropdownList(coroutineScope, scrollState)
            }
        }
    }

    /**
     * Determines drop-down list offsets from the top and
     * the bottom of the screen.
     *
     * @param layoutCoordinates
     *         the layout coordinates used to determine
     *         drop-down list offsets.
     */
    private fun calculateDropdownListVerticalOffsets(layoutCoordinates: LayoutCoordinates) {
        val windowHeight = layoutCoordinates.findRootCoordinates().size.height
        invokerHeight = layoutCoordinates.size.height

        offsetFromTop = layoutCoordinates.positionInWindow().round().y
        offsetFromBottom = windowHeight - offsetFromTop - invokerHeight
    }

    /**
     * Determines should the drop-down list be shown above or below layout which
     * expands it and maximum available height that drop-down list can have.
     */
    private fun calculateDropdownListPosition() {
        val offsetFromBottomDp = with(density) { offsetFromBottom.toDp() }
        val (offset, alignment, availableHeight) = when {
            totalItemsHeight < offsetFromBottomDp -> {
                Triple(
                    Offset(
                        x = 0.0f,
                        y = (invokerHeight - supportingTextHeight).toFloat()
                    ),
                    BottomEnd,
                    offsetFromBottomDp - 10.dp
                )
            }

            offsetFromBottom < offsetFromTop -> {
                val offsetFromTopDp = with(density) { offsetFromTop.toDp() }
                Triple(Zero, TopEnd, offsetFromTopDp - 10.dp)
            }

            else -> {
                Triple(
                    Offset(
                        x = 0.0f,
                        y = (invokerHeight - supportingTextHeight).toFloat()
                    ),
                    BottomEnd,
                    offsetFromBottomDp - 10.dp
                )
            }
        }

        listOffset = offset
        listAlignment = alignment
        listAvailableHeight = availableHeight
    }

    /**
     * Invoked when the user clicks outside the popup, and it closes
     * drop-down list if it was expanded.
     */
    private fun handleDismissRequest() {
        expanded.value = false

        if (showSearchSelection.value) {
            handleSearchSelectionDismiss()
        }

        if (selectedItem == null) {
            onSelectItem(null)
        } else {
            preselectedItemIndex = items.indexOfFirst { it == selectedItem }
        }
    }

    /**
     * Calculates the vertical position of the item with the given index
     * relative to the top edge of the top-most item.
     *
     * @param itemIndex
     *         an optional index specifying the item until which the height sum
     *         is calculated. If `null`, calculates the sum of all item heights.
     * @return
     *         the vertical position of the specified item.
     */
    private fun getItemPos(itemIndex: Int? = null): Int {
        val filteredItemHeights = if (itemIndex != null) {
            itemHeights.filterKeys { it < itemIndex }
        } else {
            itemHeights
        }

        val addedNoneItemHeight = if (noneItemEnabled && itemIndex != -1) noneItemHeight else 0
        return filteredItemHeights.values.sum() + addedNoneItemHeight
    }

    /**
     * Handles user's click on drop-down list item.
     *
     * @param item
     *         the drop-down list item which is clicked.
     */
    private fun handleItemClick(item: I) {
        onSelectItem(item)
        preselectedItemIndex = items.indexOfFirst { it == selectedItem }
    }

    /**
     * Handles user's key presses whether drop-down list is expanded or not.
     *
     * @param event
     *         a key event which occurs when user presses key.
     */
    private fun handleDropdownKeyEvent(event: KeyEvent): Boolean {
        if (expanded.value) {
            return handleKeyEventWhenDropdownExpanded(event)
        }

        return handleKeyEventWhenDropdownNotExpanded(event)
    }

    /**
     * Handles mouse clicks inside the invoker to which the drop-down
     * list is attached.
     */
    private fun onInvokerClick() {
        if (clearSelectedItemPressed) {
            clearSelectedItemPressed = false
        } else {
            expanded.value = !expandedBeforeDismissRequest
            expandedBeforeDismissRequest = !expandedBeforeDismissRequest
        }
    }

    /**
     * Handles user's mouse presses on clear selected item icon, inside the
     * invoker to which drop-down list is attached.
     */
    private fun onClearSelectedItemPress() {
        if (selectedItem != null) {
            clearSelectedItemPressed = true
            onSelectItem(null)
        }
    }

    /**
     * Handles user's key presses when drop-down list is expanded.
     *
     * @param event
     *         a key event which occurs when user presses key.
     */
    private fun handleKeyEventWhenDropdownExpanded(event: KeyEvent): Boolean {
        var stopPropagation = false

        when {
            event matches DirectionUp.key.down -> {
                movePreselectionByItems(-1)
                stopPropagation = true
            }

            event matches DirectionDown.key.down -> {
                movePreselectionByItems(1)
                stopPropagation = true
            }

            event matches (DirectionUp.key or DirectionDown.key).up -> {
                stopPropagation = true
            }

            event matches PageUp.key.down -> {
                movePreselectionByPages(-1)
            }

            event matches PageDown.key.down -> {
                movePreselectionByPages(1)
            }

            event matches (Ctrl(MoveHome.key) or Alt(MoveHome.key)).down -> {
                preselectedItemIndex = firstItemIndex
                stopPropagation = true
            }

            event matches (Ctrl(MoveEnd.key) or Alt(MoveEnd.key)).down -> {
                preselectedItemIndex = items.count() - 1
                stopPropagation = true
            }

            event matches '\n'.key.typed -> {
                handleEnterKeyEvent()
                stopPropagation = true
            }

            event matches (Tab.key or Shift(Tab.key) or Escape.key).down -> {
                handleDismissRequest()
            }

            else -> {
                handleKeyEvent(event)
            }
        }

        return stopPropagation
    }

    /**
     * Handles user's Enter key press.
     */
    private fun handleEnterKeyEvent() {
        if (showSearchSelection.value) {
            handleSearchSelectionDismiss()
        }

        val item = if (preselectedItemIndex != -1) {
            items.elementAtOrNull(preselectedItemIndex)
        } else {
            null
        }
        onSelectItem(item)
    }

    /**
     * Handles user's printable key presses when drop-down list is expanded.
     *
     * @param event
     *         a key event which occurs when user presses key.
     */
    private fun handleKeyEvent(event: KeyEvent) {
        val keyEvent = event.awtEventOrNull
        if (searchSelectionEnabled && keyEvent?.keyChar?.isPrintable() == true) {
            if (!showSearchSelection.value) {
                searchSelectionText.value = keyEvent.keyChar.toString()
            }
            showSearchSelection.value = true
        }
    }

    /**
     * Handles user's key presses when search selection field is shown.
     *
     * @param event
     *         a key event which occurs when user presses key.
     */
    private fun handleKeyEventWhenSearchSelectionIsShown(event: KeyEvent): Boolean {
        var stopPropagation = false

        if (event matches '\n'.key.typed || event matches '\n'.key.down ||
            event matches '\n'.key.up
        ) {
            if (showSearchSelection.value) {
                handleSearchSelectionDismiss()
            }

            onSelectItem(items.elementAtOrNull(preselectedItemIndex))
            preselectedItemIndex = items.indexOfFirst { it == selectedItem }
            stopPropagation = true
        }

        return stopPropagation
    }

    /**
     * Invoked when search selection field needs to be closed.
     */
    private fun handleSearchSelectionDismiss() {
        showSearchSelection.value = false
        searchSelectionText.value = ""
        onSearchSelectionChange?.let { it(searchSelectionText.value) }
        focusInvoker?.invoke()
    }

    private fun movePreselectionByPages(noOfPages: Int) {
        val preselectedPosition = getItemPos(preselectedItemIndex)
        val updatedPreselectedPosition = preselectedPosition + visibleListHeightPx * noOfPages

        preselectedItemIndex = when {
            updatedPreselectedPosition <= 0 -> firstItemIndex
            updatedPreselectedPosition >= getItemPos(null) -> items.count() - 1
            else -> {
                val updatedPreselectedItemIndex = itemIndexAtPos(updatedPreselectedPosition)
                updatedPreselectedItemIndex
            }
        }
    }

    private fun scrollPreselectionIntoView(scrollState: ScrollState) {
        val viewportTop = scrollState.value
        val itemPosTop = getItemPos(preselectedItemIndex)
        if (itemPosTop < viewportTop) {
            scrollPositionRequested = viewportTop - (viewportTop - itemPosTop)
            if (scrollPositionRequested!! > scrollState.maxValue) {
                scrollPositionRequested = scrollState.maxValue
            }
        }

        val viewportBottom = viewportTop + visibleListHeightPx
        val itemHeight = getItemHeight(preselectedItemIndex)
        val itemPosBottom = itemPosTop + itemHeight
        if (itemPosBottom > viewportBottom) {
            scrollPositionRequested =
                viewportBottom + (itemPosBottom - viewportBottom) - visibleListHeightPx
            if (scrollPositionRequested!! < 0) {
                scrollPositionRequested = 0
            }
        }
    }

    private fun getItemHeight(itemIndex: Int): Int {
        return if (itemIndex == -1) noneItemHeight else itemHeights[itemIndex] ?: 0
    }

    /**
     * Get preselected item index when user presses PageUp/PageDown key.
     *
     * @param verticalPos
     *         updated position of preselected drop-down item after
     *         user has pressed PageUp/PageDown key.
     */
    private fun itemIndexAtPos(verticalPos: Int): Int {
        return if (verticalPos < noneItemHeight) {
            firstItemIndex
        } else {
            val noOfItems = items.count()
            var itemsHeightSoFar = noneItemHeight
            var currentItemIndex = 0
            var result = noOfItems - 1
            repeat(noOfItems) {
                val itemHeight = getItemHeight(currentItemIndex++)
                itemsHeightSoFar += itemHeight
                if (verticalPos < itemsHeightSoFar) {
                    result = currentItemIndex
                }
            }
            result
        }
    }

    private fun movePreselectionByItems(byNoOfItems: Int) {
        preselectedItemIndex += byNoOfItems
        if (preselectedItemIndex < 0) {
            preselectedItemIndex = if (!noneItemEnabled) 0 else -1
        }
        val totalItems = items.count()
        if (preselectedItemIndex >= totalItems) {
            preselectedItemIndex = totalItems - 1
        }
    }

    /**
     * Handles user's key presses when drop-down list is not expanded.
     *
     * @param event
     *         a key event that was triggered in the invoker.
     * @return `true` if further event's propagation should be prevented, and
     *         `false` otherwise.
     */
    private fun handleKeyEventWhenDropdownNotExpanded(event: KeyEvent): Boolean = when {
        event matches expandKey.down -> {
            // Prevent event propagation when the key is pressed, ensuring
            // the list isn't expanded prematurely.
            true
        }

        event matches expandKey.up -> {
            expanded.value = true
            true
        }

        else -> false
    }

    /**
     * Composable for wrapping items in a column within the drop-down list.
     *
     * @receiver
     *         a [BoxScope], the scope within which this function
     *         is intended to be used.
     * @param scrollState
     *         a [ScrollState], whose value indicates current
     *         drop-down list scrollbar position.
     */
    @Composable
    private fun BoxScope.DropdownListContent(scrollState: ScrollState) {

        Column(
            modifier = Modifier
                .width(MinWidth)
                .verticalScroll(scrollState)
                .onGloballyPositioned {
                    val layoutHeightDp = with(density) { it.size.height.toDp() }
                    totalItemsHeight = layoutHeightDp
                    calculateDropdownListPosition()
                }
        ) {
            if (items.count() > 0) {
                if (noneItemEnabled) {
                    DropdownListNoneItem(
                        text = noneItemText,
                        color = if (preselectedItemIndex == -1) {
                            colorScheme.primary.copy(alpha = 0.1f)
                        } else {
                            null
                        },
                        onMeasureHeight = { measuredHeight ->
                            noneItemHeight = measuredHeight
                        },
                        onClick = {
                            onSelectItem(null)
                        }
                    )
                }
                items.forEachIndexed { index, item ->
                    val color = when (index) {
                        selectedItemIndex -> {
                            colorScheme.primary.copy(alpha = 0.2f)
                        }

                        preselectedItemIndex -> {
                            colorScheme.primary.copy(alpha = 0.1f)
                        }

                        else -> {
                            null
                        }
                    }

                    DropdownListItem(
                        onClick = { handleItemClick(item) },
                        onMeasureHeight = { measuredHeight ->
                            itemHeights[index] = measuredHeight
                        },
                        color = color
                    ) {
                        itemContent(item)
                    }
                }
            } else {
                DropdownListNoItems(content = noItemsContent)
            }
        }
        VerticalScrollbar(scrollState) {
            Modifier.align(Alignment.CenterEnd)
        }
    }

    /**
     * A popup, which is displaying drop-down list.
     *
     * @param coroutineScope
     *         a [CoroutineScope] used to launch a job which scrolls
     *         vertical drop-down list scrollbar to desired position.
     * @param scrollState
     *         a [ScrollState], whose value indicates current
     *         drop-down list scrollbar position.
     */
    @Composable
    @OptIn(ExperimentalComposeUiApi::class)
    private fun DropdownList(coroutineScope: CoroutineScope, scrollState: ScrollState) {
        LaunchedEffect(preselectedItemIndex) {
            scrollPreselectionIntoView(scrollState)
        }

        LaunchedEffect(totalItemsHeight) {
            calculateDropdownListPosition()
        }

        LaunchedEffect(selectedItem) {
            if (selectedItem != null) {
                noneItemHeight = 0
            }
        }

        Popup(
            onDismissRequest = { handleDismissRequest() },
            popupPositionProvider = PopupPositionProviderAtPosition(
                positionPx = listOffset,
                isRelativeToAnchor = true,
                offsetPx = Zero,
                alignment = listAlignment,
                windowMarginPx = 0
            ),
            properties = PopupProperties(focusable = searchSelectionEnabled),
            onPreviewKeyEvent = { handleKeyEventWhenDropdownExpanded(it) }
        ) {
            Surface(shape = ExtraSmall, tonalElevation = 3.0.dp, shadowElevation = 3.0.dp) {
                visibleListHeight = min(
                    totalItemsHeight, listAvailableHeight - listVerticalPadding * 2
                )
                Box(
                    modifier = Modifier
                        .padding(vertical = listVerticalPadding)
                        .height(visibleListHeight)
                ) {
                    if (scrollPositionRequested != null) {
                        coroutineScope.launch {
                            scrollState.scrollTo(scrollPositionRequested!!)
                            scrollPositionRequested = null
                        }
                    }

                    DropdownListContent(scrollState)
                }
                if (showSearchSelection.value) {
                    SearchSelectionField(coroutineScope)
                }
            }
        }
    }

    /**
     * Composable for displaying search selection field.
     *
     * @param coroutineScope
     *         a [CoroutineScope] used to launch a job which scrolls
     *         vertical drop-down list scrollbar to desired position.
     */
    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun SearchSelectionField(coroutineScope: CoroutineScope) {
        val focusRequester = remember { FocusRequester() }
        val interactionSource = remember { MutableInteractionSource() }
        val searchSelectionContent = remember {
            mutableStateOf(
                RawTextContent(
                    searchSelectionText.value,
                    selection = TextRange(searchSelectionText.value.length)
                )
            )
        }

        coroutineScope.launch {
            focusRequester.requestFocus()
        }

        LaunchedEffect(Unit) {
            onSearchSelectionChange?.let {
                searchSelectionContent.value = RawTextContent(
                    searchSelectionText.value,
                    selection = TextRange(searchSelectionText.value.length)
                )
                it(searchSelectionText.value)
            }
        }

        Box(
            modifier = Modifier
        ) {
            BasicTextField(
                value = searchSelectionContent.value,
                onValueChange = {
                    searchSelectionContent.value =
                        it.copy(text = trimWhitespacesExceptSpace(it.text))
                    searchSelectionText.value = searchSelectionContent.value.text
                    onSearchSelectionChange?.let { it(searchSelectionText.value) }
                },
                interactionSource = interactionSource,
                singleLine = true,
                modifier = Modifier
                    .widthIn(100.dp, MinWidth)
                    .height(24.dp)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { handleKeyEventWhenSearchSelectionIsShown(it) }
            ) {
                TextFieldDefaults.DecorationBox(
                    value = searchSelectionText.value,
                    innerTextField = it,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = None,
                    interactionSource = interactionSource,
                    contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
                        top = 0.dp,
                        bottom = 0.dp
                    )
                )
            }
        }
    }
}

/**
 * Trim whitespace characters except space from input string.
 *
 * @param input
 *         a string which should be trimmed.
 * @return trimmed string.
 */
private fun trimWhitespacesExceptSpace(input: String): String {
    return input.replace(Regex("[\\t\\n\\x0B\\f\\r]"), "")
}


/**
 * Provides a scope for the children of [DropdownListBox].
 */
public interface DropdownListBoxScope {

    /**
     * A function, which invoker's declaration should call to key events that
     * are triggered inside the invoker.
     *
     * A focusable invoker's part should handle key events using
     * the [Modifier.onPreviewKeyEvent] modifier, and delegate respective events
     * by invoking this function.
     *
     * @param event
     *         a key event which occurs when user presses key.
     */
    public fun handleKeyEvent(event: KeyEvent): Boolean

    /**
     * A function, which the invoker's declaration should call upon clicks made
     * on the invoker in order to ensure a proper expansion and
     * selection behavior.
     */
    public fun handleClick()

    /**
     * Adjusts drop-down list position based on supporting text height
     * if the invoker has any.
     *
     * Function that should be called by [Modifier.onGloballyPositioned]
     * modifier of [Text] composable which represents supporting text of the
     * invoker that is placed inside the scope.
     */
    public fun adjustPositionBasedOnSupportingTextHeight(height: Int)

    /**
     * Handles user's mouse presses on clear selected item icon
     * inside the invoker to which drop-down list is attached.
     *
     * Function that should be called by [Modifier.onPointerEvent] modifier
     * of an invoker that is placed inside the scope.
     * The [Modifier.onPointerEvent] modifier must be called with
     * the `PointerEventType.Press` argument.
     */
    public fun handleClearSelectedItemPress()
}

/**
 * Implementation of [DropdownListBoxScope], which is used by [DropdownListBox].
 *
 * @property onInvokerKeyEvent
 *         a callback which is invoked when user presses key.
 * @property onInvokerClick
 *         a callback which is invoked when user presses mouse inside the
 *         invoker of [DropdownListBox].
 * @property onAdjustPositionBasedOnSupportingTextHeight
 *         a callback which is invoked if the invoker of [DropdownListBox]
 *         has supporting text.
 * @property onClearSelectedItemPress
 *         a callback which is invoked when user presses mouse on clear
 *         selected item icon inside the invoker of [DropdownListBox].
 */
private class DropdownListBoxScopeImpl(
    private val onInvokerKeyEvent: (event: KeyEvent) -> Boolean,
    private val onInvokerClick: () -> Unit,
    private val onAdjustPositionBasedOnSupportingTextHeight: (height: Int) -> Unit,
    private val onClearSelectedItemPress: () -> Unit
) : DropdownListBoxScope {

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        return onInvokerKeyEvent(event)
    }

    override fun handleClick() {
        onInvokerClick()
    }

    override fun adjustPositionBasedOnSupportingTextHeight(height: Int) {
        onAdjustPositionBasedOnSupportingTextHeight(height)
    }

    override fun handleClearSelectedItemPress() {
        onClearSelectedItemPress()
    }
}

/**
 * The drop-down list item.
 *
 * @param onClick
 *         the callback that is triggered when the item is clicked.
 * @param onMeasureHeight
 *         callback which is invoked when item is positioned.
 * @param color
 *         the background color of drop-down list item.
 * @param content
 *         content to be displayed inside drop-down list item.
 */
@Composable
private fun DropdownListItem(
    onClick: () -> Unit,
    onMeasureHeight: (Int) -> Unit,
    color: Color?,
    content: @Composable () -> Unit
) {
    val itemHeight = remember { mutableStateOf(0) }

    Row(
        modifier = Modifier
            .clickable(
                enabled = true,
                onClick = onClick
            )
            .fillMaxWidth()
            .heightIn(48.dp)
            .onGloballyPositioned {
                val height = it.size.height
                if (height != itemHeight.value) {
                    itemHeight.value = height
                    onMeasureHeight(height)
                }
            }
            .background(color ?: Transparent),
        verticalAlignment = CenterVertically
    ) {
        content()
    }
}

/**
 * The drop-down list without items.
 *
 * @param content
 *         the content to be shown when drop-down list doesn't have any items.
 */
@Composable
private fun DropdownListNoItems(content: @Composable (() -> Unit)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(48.dp)
            .padding(horizontal = 12.dp)
            .background(Transparent),
        verticalAlignment = CenterVertically,
        horizontalArrangement = Center
    ) {
        StyledContent(
            contentColor = colorScheme.secondary.copy(alpha = 0.5f),
            textStyle = MaterialTheme.typography.titleSmall,
            content = content
        )
    }
}

/**
 * The drop-down list none item.
 *
 * @param text
 *         the text to be displayed for drop-down list none item.
 * @param color
 *         the background color of drop-down list none item.
 * @param onMeasureHeight
 *         callback which is invoked when item is positioned.
 * @param onClick
 *         the callback that is triggered when the item is clicked.
 */
@Composable
private fun DropdownListNoneItem(
    text: String = "<None>",
    color: Color? = null,
    onMeasureHeight: (Int) -> Unit,
    onClick: () -> Unit
) {
    val noneItemHeight = remember { mutableStateOf(0) }

    Row(
        modifier = Modifier
            .clickable(
                enabled = true,
                onClick = onClick
            )
            .onGloballyPositioned {
                val height = it.size.height
                if (height != noneItemHeight.value) {
                    noneItemHeight.value = height
                    onMeasureHeight(height)
                }
            }
            .fillMaxWidth()
            .heightIn(48.dp)
            .background(color ?: Transparent),
        verticalAlignment = CenterVertically
    ) {
        StyledContent(
            contentColor = colorScheme.secondary.copy(alpha = 0.5f),
            content = {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        )
    }
}

/**
 * Apply color and typography to passed composable content.
 *
 * @param contentColor
 *         a color to be applied to content.
 * @param textStyle
 *         a text style to be applied to content.
 * @param content
 *         a content to which color and typography are applied.
 */
@Composable
private fun StyledContent(
    contentColor: Color,
    textStyle: TextStyle? = null,
    content: @Composable () -> Unit
) {
    val contentWithColor = @Composable {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            content = content
        )
    }

    if (textStyle != null) {
        ProvideTextStyle(textStyle, contentWithColor)
    } else {
        contentWithColor()
    }
}

/**
 * Checks if character is printable or not.
 *
 * @return `true` if character is printable, `false` otherwise.
 */
private fun Char.isPrintable(): Boolean {
    val block = UnicodeBlock.of(this)
    return (!isISOControl(this)) && this != CHAR_UNDEFINED && block != null && block != SPECIALS
}
