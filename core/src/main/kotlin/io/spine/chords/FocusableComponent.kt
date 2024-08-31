/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords

import androidx.compose.ui.focus.FocusRequester

/**
 * A base class for components that can be focused.
 *
 * A component that extends this class can be focused by invoking its [focus]
 * method.
 *
 * To implement the focusing behavior, an actual component implementation that
 * extends this class can either choose to assign [lazyFocusRequester] onto
 * a composable that should be focused on behalf of the component, or override
 * the [focus] method to implement a more complex focusing logic, which can in
 * particular be appropriate in cases when such a composable needs to be
 * identified dynamically.
 *
 * @see [focus]
 * @see [focusRequester]
 */
public abstract class FocusableComponent : Component() {
    private val lazyFocusRequester = lazy { FocusRequester() }

    /**
     * A [FocusRequester] whose [requestFocus][FocusRequester.requestFocus]
     * method will be invoked when the [focus] method is invoked.
     *
     * It is present as a convenience for implementations to be able to make
     * the respective composable focusable upon a [focus] method call.
     * Nevertheless, the implementation doesn't have to use it if it needs to
     * implement a more complex focusing algorithm, in which case the [focus]
     * function can be overridden to implement the desired focusing logic.
     */
    protected val focusRequester: FocusRequester by lazyFocusRequester

    /**
     * Requests the component to be focused.
     */
    public open fun focus() {
        if (lazyFocusRequester.isInitialized()) {
            lazyFocusRequester.value.requestFocus()
        } else {
            throw IllegalStateException(
                "Make sure to either assign `lazyFocusRequester` onto some composable, or " +
                "override the `focus` method with the required focusing logic.")
        }
    }
}
