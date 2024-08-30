/*
 * Copyright (c) 2024 TeamDev. All rights reserved.
 * TeamDev PROPRIETARY and CONFIDENTIAL.
 * Use is subject to license terms.
 */

package io.spine.internal.gradle.javac

import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.CommandLineArgumentProvider

/**
 * Configures Error Prone for this `JavaCompile` task.
 *
 * Specifies the arguments for the compiler invocations. In particular, this configuration
 * overrides a number of Error Prone defaults. See [ErrorProneConfig] for the details.
 *
 * Please note that while `ErrorProne` is a standalone Gradle plugin,
 * it still has to be configured through `JavaCompile` task options.
 *
 * Here's an example of how to use it:
 *
 * ```
 * tasks {
 *     withType<JavaCompile> {
 *         configureErrorProne()
 *     }
 * }
 *```
 */
@Suppress("unused")
fun JavaCompile.configureErrorProne() {
    options.errorprone
        .errorproneArgumentProviders
        .add(ErrorProneConfig.ARGUMENTS)
}

/**
 * The knowledge that is required to set up `Error Prone`.
 */
private object ErrorProneConfig {

    /**
     * Command line options for the `Error Prone` compiler.
     */
    val ARGUMENTS = CommandLineArgumentProvider {
        listOf(

            // Exclude generated sources from being analyzed by ErrorProne.
            // Include all directories started from `generated`, such as `generated-proto`.
            "-XepExcludedPaths:.*/generated.*/.*",

            // Turn the check off until ErrorProne can handle `@Nested` JUnit classes.
            // See issue: https://github.com/google/error-prone/issues/956
            "-Xep:ClassCanBeStatic:OFF",

            // Turn off checks that report unused methods and method parameters.
            // See issue: https://github.com/SpineEventEngine/config/issues/61
            "-Xep:UnusedMethod:OFF",
            "-Xep:UnusedVariable:OFF",

            "-Xep:CheckReturnValue:OFF",
            "-Xep:FloggerSplitLogStatement:OFF",
            "-Xep:FloggerLogString:OFF"
        )
    }
}
