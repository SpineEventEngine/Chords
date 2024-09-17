/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.appshell

import io.spine.chords.Client

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
 * @param name
 *         an application's name, which is in particular displayed in
 *         the application window's title.
 * @param client
 *         a [Client] that handles the server communication, which should be
 *         used throughout the application.
 * @param views
 *         the list application's views.
 * @param initialView
 *         allows to specify a view from the list of [views], if any view other
 *         than the first one has to be displayed when the application starts.
 */
public open class ClientApplication(
    name: String,
    public val client: Client,
    views: List<AppView>,
    initialView: AppView? = null
) : Application(name, views, initialView)
