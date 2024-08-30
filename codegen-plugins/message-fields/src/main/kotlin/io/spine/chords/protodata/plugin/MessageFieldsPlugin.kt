/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.protodata.plugin

import io.spine.chords.runtime.MessageField
import io.spine.chords.runtime.MessageOneof
import io.spine.protodata.plugin.Plugin
import io.spine.protodata.plugin.ViewRepository
import io.spine.protodata.renderer.Renderer

/**
 * The ProtoData [Plugin] that generates [MessageField] and [MessageOneof]
 * implementations for the fields of command Proto messages.
 *
 * The code is also generated for types of command message fields,
 * except for the types provided by Protobuf.
 *
 * It is required to avoid usages of Protobuf reflection API calls. Read more
 * in this [issue](https://github.com/Projects-tm/1DAM/issues/41).
 *
 * See the [MessageFieldsRenderer] for more details on code generation.
 */
public class MessageFieldsPlugin : Plugin {

    override fun renderers(): List<Renderer<*>> {
        return listOf(MessageFieldsRenderer())
    }

    override fun viewRepositories(): Set<ViewRepository<*, *, *>> {
        return setOf(FieldViewRepository())
    }
}
