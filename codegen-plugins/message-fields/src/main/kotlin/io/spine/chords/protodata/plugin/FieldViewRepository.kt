/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */
package io.spine.chords.protodata.plugin

import io.spine.core.EventContext
import io.spine.protodata.event.FieldEntered
import io.spine.protodata.plugin.ViewRepository
import io.spine.server.route.EventRoute
import io.spine.server.route.EventRouting

/**
 * The repository for [FieldView].
 */
internal class FieldViewRepository : ViewRepository<FieldMetadataId,
        FieldView,
        FieldMetadata>() {

    override fun setupEventRouting(routing: EventRouting<FieldMetadataId>) {
        super.setupEventRouting(routing)
        routing.route(FieldEntered::class.java)
        { message: FieldEntered, _: EventContext? ->
            EventRoute.withId(
                fieldMetadataId {
                    file = message.file
                    typeName = message.type
                    field = message.field
                }
            )
        }
    }
}
