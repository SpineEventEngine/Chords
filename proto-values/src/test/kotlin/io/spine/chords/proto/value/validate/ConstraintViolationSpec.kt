/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.chords.proto.value.validate

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.string.TemplateString
import io.spine.validation.ConstraintViolation
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies that forms display messages emitted by Spine 2 validation.
 */
@DisplayName("`ConstraintViolation` should")
internal class ConstraintViolationSpec {

    /**
     * Substitutes every named parameter from the current validation message format.
     */
    @Test
    fun `format named placeholders`() {
        val template = TemplateString.newBuilder()
            .setWithPlaceholders(
                "Field ${'$'}{field} must have at least ${'$'}{minimum} characters."
            )
            .putPlaceholderValue("field", "name")
            .putPlaceholderValue("minimum", "3")
            .build()
        val violation = ConstraintViolation.newBuilder()
            .setMessage(template)
            .build()

        violation.formattedMessage shouldBe "Field name must have at least 3 characters."
    }

    /**
     * Preserves messages that need no substitution.
     */
    @Test
    fun `format a literal message`() {
        val violation = ConstraintViolation.newBuilder()
            .setMessage(TemplateString.newBuilder().setWithPlaceholders("A value is required."))
            .build()

        violation.formattedMessage shouldBe "A value is required."
    }

    /**
     * Rejects a malformed message rather than hiding a missing placeholder value.
     */
    @Test
    fun `reject incomplete placeholders`() {
        val violation = ConstraintViolation.newBuilder()
            .setMessage(TemplateString.newBuilder().setWithPlaceholders("Missing ${'$'}{field}."))
            .buildPartial()

        shouldThrow<IllegalArgumentException> { violation.formattedMessage }
    }
}
