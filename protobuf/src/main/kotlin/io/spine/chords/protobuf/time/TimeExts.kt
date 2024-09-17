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

package io.spine.chords.protobuf.time

import com.google.protobuf.Duration
import com.google.protobuf.Timestamp
import com.google.protobuf.util.Durations
import com.google.protobuf.util.Timestamps
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

public const val DefaultDatePattern: String = "yyyy-MM-dd"

/**
 * Returns the readable `String` in the format of "yyyy-MM-dd"
 * constructed from the `Timestamp` object.
 */
public fun Timestamp.toReadableString(): String {
    val instant = toInstant()
    val zoneId = ZoneId.systemDefault()
    val dateTimeFormatter = DateTimeFormatter.ofPattern(DefaultDatePattern)

    return dateTimeFormatter.format(instant.atZone(zoneId))
}

/**
 * Returns an [Instant] value that correspond to this timestamp.
 */
public fun Timestamp.toInstant(): Instant =
    Instant.ofEpochSecond(seconds, nanos.toLong())

/**
 * Number of days for this `Duration` value.
 */
public val Duration.days: Long
    get() {
        return Durations.toDays(this)
    }

/**
 * Subtracts the other `Timestamp` object from this `Timestamp` object.
 */
public operator fun Timestamp.minus(other: Timestamp): Duration {
    return Timestamps.between(this, other)
}

/**
 * Compares this `Duration` object with the specified `Duration`
 * object for order.
 *
 * Returns zero if this object is equal to the
 * specified [other] object, a negative number if it's
 * less than [other], or a positive number if it's
 * greater than [other].
 */
public operator fun Duration.compareTo(other: Duration): Int {
    return Durations.comparator().compare(this, other)
}

/**
 * Converts the number of days to the `Duration` object.
 */
public fun Int.days(): Duration {
    return Durations.fromDays(this.toLong())
}

/**
 * Converts the number of hours to the `Duration` object
 */
public fun Int.hours(): Duration {
    return Durations.fromHours(this.toLong())
}
