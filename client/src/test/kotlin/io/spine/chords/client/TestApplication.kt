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

package io.spine.chords.client

import io.spine.chords.core.appshell.Application
import io.spine.chords.core.appshell.app
import java.awt.Dimension

/**
 * Assigns the JVM-wide [app] property so that the code under test in this
 * module can be exercised outside a running application.
 *
 * Some types tested in this module read application-wide state upon
 * construction. In particular,
 * [ModalCommandConsequences][io.spine.chords.client.layout.ModalCommandConsequences]
 * inherits from
 * [DefaultPropsOwnerBase][io.spine.chords.core.DefaultPropsOwnerBase], which
 * obtains default property values from the application's
 * [shared defaults][Application.sharedDefaults].
 *
 * In a running application, [app] is assigned by [Application.run]. Tests do
 * not run an application, and reading an unassigned [app] throws
 * [IllegalStateException]. Such types would therefore fail to be constructed
 * in tests, regardless of what a test actually verifies.
 *
 * To prevent this, this object assigns [app] a real, but deliberately minimal
 * [Application] instance rather than a mock. The instance serves no purpose
 * other than satisfying the dependency described above: it declares no views,
 * and its window is never created or shown, as [run][Application.run] is
 * never invoked on it. It also customizes no
 * [shared defaults][Application.sharedDefaults], so the types under test keep
 * the default property values declared by their own implementations.
 */
internal object TestApplication {

    /**
     * Tells whether [install] has already assigned the [app] property.
     */
    private var installed: Boolean = false

    /**
     * Assigns the [app] property, doing nothing if a previous call has
     * already assigned it.
     *
     * [app] is a [write-once][io.spine.chords.core.writeOnce] property, so
     * assigning it twice fails. At the same time, all test classes that need
     * an application share a single JVM, and each of them has to ensure that
     * [app] is assigned before its own test cases run, since the order in
     * which test classes are executed is not fixed.
     *
     * This method is therefore idempotent, and is synchronized to stay so if
     * tests are executed in parallel. Any test class can safely call it, e.g.
     * from a [BeforeAll][org.junit.jupiter.api.BeforeAll] method.
     */
    @Synchronized
    fun install() {
        if (!installed) {
            app = Application(
                name = "Chords client tests",
                views = emptyList(),
                minWindowSize = Dimension(1, 1)
            )
            installed = true
        }
    }
}
