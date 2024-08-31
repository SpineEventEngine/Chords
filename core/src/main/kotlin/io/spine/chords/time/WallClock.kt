/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.chords.time

import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The client's local time characteristics.
 */
public object WallClock {

    /**
     * Local time zone offset.
     */
    public val zoneOffset: ZoneOffset
        get() = OffsetDateTime.now().offset
}
