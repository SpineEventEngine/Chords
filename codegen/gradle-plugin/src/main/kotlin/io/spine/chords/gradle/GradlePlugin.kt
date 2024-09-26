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
import java.io.InputStream
import java.net.URL
import java.net.URLDecoder
import java.util.jar.JarFile
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem


/**
 * A Gradle [Plugin] that generates Kotlin extensions for Proto messages.
 *
 * Actually, it applies
 * [codegen/plugins](https://github.com/SpineEventEngine/Chords/tree/master/codegen/plugins)
 * to a module, which requires the code generation.
 *
 * It is under construction at the moment.
 */
public class GradlePlugin : Plugin<Project> {

    @Suppress("ConstPropertyName")
    private companion object {
        private const val moduleName = "codegen-workspace"
    }

    override fun apply(project: Project) {

        val workspaceDir = File(project.buildDir, moduleName)

        val copyResources = project.tasks
            .register("copyResources") { task ->
                task.doLast {
                    copyResources(workspaceDir)
                    addRunPermissionToGradle(workspaceDir)
                }
            }

        project.tasks
            .register("generateCode", GenerateCode::class.java) { task ->
                task.dependsOn(copyResources)
                task.workspaceDir = workspaceDir.path
            }
    }

    private fun addRunPermissionToGradle(workspaceDir: File) {
        if (OperatingSystem.current().isWindows) {
            return
        }

        ProcessBuilder()
            .command("chmod", "+x", "./gradlew")
            .directory(workspaceDir)
            .start()
    }

    private fun copyResources(workspaceDir: File) {
        val resourcesRoot = "/$moduleName"
        listResources(resourcesRoot).forEach { resource ->
            val outputFile = File(workspaceDir, resource)
            val inputStream = loadResourceAsStream(
                resourcesRoot + resource
            )
            outputFile.parentFile.mkdirs()
            outputFile.writeBytes(inputStream.readBytes())
        }
    }

    private fun loadResource(path: String): URL {
        val resourceUrl = this::class.java.getResource(path)
        checkNotNull(resourceUrl) {
            "Resource not found in classpath: `$path`."
        }
        return resourceUrl
    }

    private fun loadResourceAsStream(path: String): InputStream {
        return loadResource(path).openStream()
    }

    private fun listResources(path: String): Set<String> {
        val resourceUrl = loadResource(path)
        check(resourceUrl.protocol == "jar") {
            "Protocol not supported yet: `${resourceUrl.protocol}`."
        }
        val result: MutableSet<String> = mutableSetOf()
        val resourcePath = resourceUrl.path
        val afterProtocol = "file:".length
        val beforeJarEntry = resourcePath.indexOf("!")
        val jarPath = resourcePath.substring(afterProtocol, beforeJarEntry)
        val jarFile = JarFile(URLDecoder.decode(jarPath, "UTF-8"))
        val jarEntries = jarFile.entries()
        while (jarEntries.hasMoreElements()) {
            val entryName = jarEntries.nextElement().name
            if (entryName.startsWith(path.substring(1))
                && !entryName.endsWith("/")
            ) {
                result.add(entryName.substring(path.length - 1))
            }
        }
        return result
    }
}
