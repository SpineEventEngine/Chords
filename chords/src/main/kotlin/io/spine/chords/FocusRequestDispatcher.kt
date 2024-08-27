/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * An object that dispatches a focus request to a component that owns
 * this dispatcher.
 *
 * A component that was given this dispatcher has to ensure that incoming focus
 * requests are handled by the component in one of two ways:
 * - One way is to assign the [requester] onto the component using
 *   the component's [Modifier.focusRequester]. This would typically be more
 *   appropriate for simple components.
 * - Another way is to specify a [handleFocusRequest] callback, which would
 *   focus the component. This way is more universal and can be more relevant in
 *   cases when a component associated with this [FocusRequestDispatcher] is
 *   a complex component consisting of several focusable fields, which might
 *   require some logic for choosing which field should be focused at the time
 *   of receiving such a request.
 *
 *   @param focusRequester
 *           [FocusRequester] which should receive focus requests, if specified.
 */
public class FocusRequestDispatcher(focusRequester: FocusRequester? = null) {
    private val lazyRequester = lazy { focusRequester ?: FocusRequester() }
    private val requestNoInternal = mutableStateOf(0)

    /**
     * A property that can be helpful for being used as an additional `key` for
     * `LaunchedEffect` that invokes this dispatcher.
     *
     * Using this property as an additional `key` for `LaunchedEffect` was seen
     * to be useful (in addition to using the `FocusRequestDispatcher` instance
     * itself as a key) to ensure that the request is rescheduled  — when
     * the same `LaunchedEffect` is rescheduled in a quick succession by
     * setting, clearing, and setting again using the same
     * `FocusRequestDispatcher` instance as its key.
     */
    public val requestReschedulingKey: State<Int> = requestNoInternal

    /**
     * A [FocusRequester] instance that can be assigned to a component using its
     * [Modifier.focusRequester] to make a component receive focus requests.
     *
     * This property can be used as an alternative to assigning [requester] in
     * order to allow the component to receive focus requests.
     */
    public val requester: FocusRequester by lazyRequester

    /**
     * A callback, which has to be specified by a component as a function that
     * moves the input focus inside a component.
     *
     * This callback can be assigned as an alternative to using [requester] in
     * order to allow the component to receive focus requests.
     */
    public var handleFocusRequest: (() -> Unit)? = null

    /**
     * @return `true`, if this dispatcher has been associated with some
     * component(s) as a way for focusing them. `false` means that this
     * dispatcher won't change the focus since it's not associated with any
     * component or focusing logic.
     *
     * @see [requester]
     * @see [handleFocusRequest]
     */
    public fun isAttached(): Boolean {
        return lazyRequester.isInitialized() || handleFocusRequest != null
    }

    /**
     * Requests the component to which this [FocusRequestDispatcher] is assigned
     * to be focused.
     */
    public fun requestFocus() {
        if (lazyRequester.isInitialized()) {
            lazyRequester.value.requestFocus()
        }
        handleFocusRequest?.invoke()
    }

    /**
     * Changes the [requestReschedulingKey] property to a new value that is
     * unique in scope of the same `FocusRequestDispatcher`.
     *
     * It can be useful for configuring the `LaunchedEffect` that triggers
     * this dispatcher.
     *
     * @see requestReschedulingKey
     */
    public fun changeReschedulingKey() {
        requestNoInternal.value++
    }
}

/**
 * Attaches the given [FocusRequestDispatcher] (if it is not `null`) using
 * the given [Modifier].
 *
 * @receiver a [Modifier] whose [focusRequester] needs to be used for attaching
 *         the given [dispatcher].
 * @param dispatcher
 *         a [FocusRequestDispatcher] that should be attached using
 *         this [Modifier].
 * @return the same [Modifier] instance on which this function was invoked.
 */
public fun Modifier.focusRequestDispatcher(
    dispatcher: FocusRequestDispatcher?
): Modifier {
    return if (dispatcher != null) {
        this.focusRequester(dispatcher.requester)
    } else {
        this
    }
}
