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

package io.spine.chords.core.layout

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.spine.chords.core.TestApplication
import io.spine.chords.core.layout.given.DialogSetupSpecEnv.ConfiguredDialog
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the request and return-value contract of [DialogSetup].
 */
@DisplayName("`DialogSetup` should")
internal class DialogSetupSpec {

    /**
     * Repeated requests return the displayed object and leave the properties
     * supplied by its first request intact.
     */
    @Test
    fun `return the displayed instance and discard repeated properties`() {
        val firstDialog = ConfiguredDialog.open {
            label = "first"
        }

        val repeatedResult = ConfiguredDialog.open {
            label = "second"
        }
        firstDialog.applyConfiguredProps()

        repeatedResult shouldBeSameInstanceAs firstDialog
        firstDialog.label shouldBe "first"
        firstDialog.nestedDialog shouldBe null
    }

    /**
     * Closing removes the class match so the next request can install and
     * return a fresh object.
     */
    @Test
    fun `return a fresh instance after the displayed dialog closes`() {
        val firstDialog = ConfiguredDialog.open()
        firstDialog.close()

        val reopenedDialog = ConfiguredDialog.open()

        reopenedDialog shouldNotBeSameInstanceAs firstDialog
        TestApplication.currentBottomDialog shouldBeSameInstanceAs reopenedDialog
    }

    /**
     * Clears dialog state left by an earlier request-level case.
     */
    @BeforeEach
    fun resetDialogs() {
        TestApplication.closeDialogs()
    }

    /**
     * Installs the shared unrendered application before request-level tests.
     */
    private companion object {

        /**
         * Initializes the application UI used by [Dialog.open].
         */
        @JvmStatic
        @BeforeAll
        fun setUpApplication() {
            TestApplication.install()
        }
    }
}
