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

package io.spine.chords.client.appshell

import io.spine.chords.client.Client
import io.spine.chords.core.appshell.AppView
import io.spine.chords.core.appshell.Application
import io.spine.chords.core.appshell.app
import java.awt.Dimension

/**
 * Makes the [ClientApplication]'s [client][ClientApplication.client] property
 * to be available via a shortcut `app.client` expression.
 */
// The receiver is needed to specify a "static" context for the function.
@Suppress("UnusedReceiverParameter")
public val Application.client: Client get() = (app as ClientApplication).client

/**
 * A variant of [Application] that represents a client application, which
 * interacts with the application server.
 *
 * @param name An application's name, which is in particular displayed in
 *   the application window's title.
 * @param client A [Client] that handles the server communication, which should be
 *   used throughout the application.
 * @param views The list application's views.
 * @param initialView Allows to specify a view from the list of [views], if any view other
 *   than the first one has to be displayed when the application starts.
 * @param minWindowSize The minimal size of the application window.
 */
public open class ClientApplication(
    name: String,
    public val client: Client,
    views: List<AppView>,
    initialView: AppView? = null,
    minWindowSize: Dimension = Dimension(1100, 800)
) : Application(name, views, initialView, minWindowSize)
