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

package io.spine.chords.gradle

import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The functional test for `io.spine.chords.gradle` Gradle plugin.
 */
@DisplayName("Gradle plugin should")
class GradlePluginSpec {

    @Suppress("ConstPropertyName")
    companion object {

        private const val pluginId = "io.spine.chords.gradle"

        private const val sourceProtoFile =
            "src/main/proto/chords/commands.proto"

        private const val generatedKotlinFile =
            "generated/main/kotlin/io/chords/command/TestCommandDef.kt"
    }

    @Test
    fun copyResourcesAndApplyCodegenPlugins() {
        val projectDir = File("build/functionalTest")

        Files.createDirectories(projectDir.toPath())
        writeFile(File(projectDir, "settings.gradle.kts"), "")

        writeFile(
            File(projectDir, "build.gradle.kts"),
            buildGradleFileContent(pluginId)
        )
        writeFile(
            File(projectDir, sourceProtoFile),
            protoFileContent
        )

        val outputDir = File(projectDir, "_out")
        outputDir.mkdirs()
        val stdoutFile = File(outputDir, "std-out.txt")
        val stderrFile = File(outputDir, "err-out.txt")

        val result = GradleRunner.create()
            .forwardStdOutput(FileWriter(stdoutFile))
            .forwardStdError(FileWriter(stderrFile))
            .withPluginClasspath()
            .withArguments("generateCode")
            .withProjectDir(projectDir)
            .build()

        listOf(
            "> Task :copyResources",
            "> Task :generateCode",
            "BUILD SUCCESSFUL"
        ).forEach {
            assertTrue(
                result.output.contains(it)
            )
        }

        assertTrue(
            File(projectDir, generatedKotlinFile).exists()
        )
    }
}

private fun writeFile(file: File, text: String) {
    file.parentFile.mkdirs()
    FileWriter(file).use { writer ->
        writer.write(text)
    }
}

private fun buildGradleFileContent(pluginId: String): String = """
        
plugins {
    id("$pluginId")
}
        
apply(from = "../../../../version.gradle.kts")
version = extra["chordsVersion"]!!
       
""".trimIndent()

private val protoFileContent = """

syntax = "proto3";

package chords;

import "spine/options.proto";

option (type_url_prefix) = "type.chords";
option java_package = "io.chords.command";
option java_outer_classname = "TestCommandProto";
option java_multiple_files = true;

message TestCommand {
    string id = 1;
}

""".trimIndent()
