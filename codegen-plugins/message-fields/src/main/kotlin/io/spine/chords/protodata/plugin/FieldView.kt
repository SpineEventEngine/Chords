/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */
package io.spine.chords.protodata.plugin

import io.spine.core.External
import io.spine.core.Subscribe
import io.spine.protodata.event.FieldEntered
import io.spine.protodata.plugin.View

/**
 * Records the [FieldMetadata].
 */
internal class FieldView : View<FieldMetadataId,
        FieldMetadata,
        FieldMetadata.Builder>() {

    @Subscribe
    @Suppress("EmptyFunctionBlock", "UNUSED_PARAMETER")
    internal fun on(@External event: FieldEntered) {
        // There is nothing to do here — ID holds all the required state.
    }
}
