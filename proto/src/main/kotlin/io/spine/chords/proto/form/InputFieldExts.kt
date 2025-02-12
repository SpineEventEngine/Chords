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

package io.spine.chords.proto.form

import com.google.protobuf.Message
import io.spine.chords.core.InputField
import io.spine.chords.core.ValueParseException
import io.spine.chords.core.exceptionBasedParser
import io.spine.protobuf.ValidatingBuilder
import io.spine.validate.ValidationException

/**
 * A helper method designed to be used within [InputField.parseValue] to help
 * fulfill its contract in a more compact way, which is useful when the parsing
 * consists of building a [Message][com.google.protobuf.Message].
 *
 * More precisely, it runs the provided [builder] to obtain
 * a [ValidatingBuilder] message that corresponds to the `rawText` argument
 * of [InputField.formatValue], which invokes this function. Then it invokes
 * [vBuild][ValidatingBuilder.vBuild()] on that builder instance and returns
 * its value if it completes successfully. If `vBuild` fails with
 * [ValidationException] then this method throws [ValueParseException] with
 * the given [failureMessage].
 *
 * @param failureMessage
 *         a parsing failure message that will be reported when throwing
 *         [ValueParseException] (in case when building a message fails with
 *         [ValidationException]).
 * @param builder
 *         a lambda that should create and configure a respective
 *         [ValidatingBuilder] instance based on the [passeValue]'s
 *         `rawText` argument.
 * @return the message value that has been successfully parsed (built by
 *         invoking [vBuild][ValidatingBuilder.vBuild()] on
 *         [ValidatingBuilder] returned by [builder]).
 * @throws ValueParseException
 *         if [vBuild][ValidatingBuilder.vBuild()] on the prepared builder
 *         throws [ValidationException].
 * @see [InputField.formatValue]
 * @see vBuildBasedParser
 */
public fun <V : Message> vBuildBasedParser(
    failureMessage: String = "Enter a valid value",
    builder: () -> ValidatingBuilder<V>,
): V = exceptionBasedParser(
    ValidationException::class,
    failureMessage
) {
    builder().vBuild() as V
}
