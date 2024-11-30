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

import androidx.compose.runtime.Stable
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
@Stable
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
            try {
                lazyFocusRequester.value.requestFocus()
            } catch (e: IllegalStateException) {
                throw IllegalStateException(
                    "Couldn't request focus for component ${javaClass.simpleName}", e
                )
            }
        } else {
            throw IllegalStateException(
                "Make sure to either assign `lazyFocusRequester` onto some composable, or " +
                "override the `focus` method with the required focusing logic.")
        }
    }
}
