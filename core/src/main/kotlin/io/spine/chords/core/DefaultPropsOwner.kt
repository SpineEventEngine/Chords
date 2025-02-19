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

package io.spine.chords.core

import io.spine.chords.core.appshell.Application
import io.spine.chords.core.appshell.app
import io.spine.chords.core.appshell.SharedDefaults

/**
 * A marker interface, which signifies types whose default property values can
 * be configured on the application level (see [Application.sharedDefaults]).
 *
 * Implementations of this interface have an obligation to obtain property
 * initializers for the respective type using
 * [app.sharedDefaults.defaultsInitializer][SharedDefaults.defaultsInitializer]
 * and run them to apply default property configurations for the
 * respective instance. The simplest way to do this is to extend
 * [DefaultPropsOwnerBase] with the respective [DefaultPropsOwner]
 * implementation, and call its [DefaultPropsOwnerBase.setDefaultProps] method
 * at the respective moment(s) of object's lifecycle.
 */
public interface DefaultPropsOwner

public abstract class DefaultPropsOwnerBase : DefaultPropsOwner {

    /**
     * A lambda that assigns default property values that are applicable for
     * this component according to the application-wide configuration.
     *
     * @see Application.sharedDefaults
     */
    private val defaultsInitializer: ((DefaultPropsOwner) -> Unit)? by lazy {
        app.sharedDefaults.defaultsInitializer(javaClass)
    }

    init {
        setDefaultProps()
    }

    /**
     * Runs any shared property initializers that might have been specified for
     * this type on the application level (see [Application.sharedDefaults]).
     *
     * @see Application.sharedDefaults
     */
    protected fun setDefaultProps() {
        defaultsInitializer?.invoke(this)
    }
}
