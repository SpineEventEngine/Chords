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
 * to a module, which requires the code generation for Proto sources.
 *
 * Adds a dependency for the `compileKotlin` task if it is present in the project.
 * Otherwise, it should be configured manually in the project build script.
 * The task `generateCode` should be configured to execute in this case.
 *
 * The dependencies on Proto sources that are required for code generation
 * can be configured in the following way:
 * ```
 * chordsGradlePlugin {
 *     protoDependencies("io.spine:spine-money:1.5.0")
 * }
 * ```
 */
public class GradlePlugin : Plugin<Project> {

    @Suppress("ConstPropertyName")
    private companion object {
        private const val workspaceModuleName = "codegen-workspace"
        private const val gradleWrapperJar = "gradle/wrapper/gradle-wrapper.jar"
    }

    /**
     * Creates plugin extension, which allows to configure the plugin,
     * and tasks that perform the necessary actions.
     */
    override fun apply(project: Project) {

        val workspaceDir = File(project.buildDir, workspaceModuleName)

        project.createExtension()

        val copyResources = project.tasks
            .register("copyResources") { task ->
                task.doLast {
                    copyResources(workspaceDir)
                }
            }

        val addRunPermission = project.tasks
            .register("addRunPermission") { task ->
                task.dependsOn(copyResources)
                task.doLast {
                    addRunPermission(workspaceDir)
                }
            }

        val generateCode = project.tasks
            .register("generateCode", GenerateCode::class.java) { task ->
                task.dependsOn(addRunPermission)
                task.workspaceDir = workspaceDir.path
                task.dependencies(project.extension.dependencies)
                project.extension.codegenPluginsArtifact?.let {
                    task.codegenPluginsArtifact = it
                }
            }

        val compileKotlin = project.tasks.findByName("compileKotlin")
        if (compileKotlin != null) {
            compileKotlin.dependsOn(generateCode)
        } else {
            project.logger.warn(
                """
                Warning! Task `compileKotlin` not found, so required dependency not added.
                To generate code, the `generateCode` task should be executed.
                """.trimIndent()
            )
        }
    }

    /**
     * Executes a native command to add run permission to `gradlew`.
     *
     * The operation is performed under Linux-based OS
     * and is skipped under Windows.
     */
    private fun addRunPermission(workspaceDir: File) {
        if (OperatingSystem.current().isWindows) {
            return
        }

        ProcessBuilder()
            .command("chmod", "+x", "./gradlew")
            .directory(workspaceDir)
            .start()
    }

    /**
     * Copies the necessary resources from classpath.
     *
     * Actually, it creates a `workspace` module, in which the code generation
     * is to be performed.
     */
    private fun copyResources(workspaceDir: File) {
        val resourcesRoot = "/$workspaceModuleName"
        listResources(resourcesRoot).forEach { resource ->
            val outputFile = File(workspaceDir, resource)
            val inputStream = loadResourceAsStream(
                resourcesRoot + resource
            )
            outputFile.parentFile.mkdirs()
            outputFile.writeBytes(inputStream.readBytes())
        }

        val gradleWrapperJar = File(workspaceDir, gradleWrapperJar)
        if (!gradleWrapperJar.exists()) {
            gradleWrapperJar.parentFile.mkdirs()
            //System.err.println("Copy file:" + gradleWrapperJar.path)
            loadResourceAsStream("/gradle-wrapper.zip")
                .copyTo(gradleWrapperJar.outputStream())
        }
    }

    /**
     * Loads the specified resource from classpath.
     *
     * @throws IllegalStateException If the requested resource is not available.
     */
    private fun loadResource(path: String): URL {
        val resourceUrl = this::class.java.getResource(path)
        checkNotNull(resourceUrl) {
            "Resource not found in classpath: `$path`."
        }
        return resourceUrl
    }

    /**
     * Opens [InputStream] on the specified resource in the classpath.
     */
    private fun loadResourceAsStream(path: String): InputStream {
        return loadResource(path).openStream()
    }

    /**
     * Returns the list of resources in the classpath by the [path] specified.
     */
    private fun listResources(path: String): Set<String> {
        val resourceUrl = loadResource(path)
        check(resourceUrl.protocol == "jar") {
            "Protocol is not supported yet: `${resourceUrl.protocol}`."
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

/**
 * Obtains the extension the plugin added to this Gradle project.
 */
private val Project.extension: ParametersExtension
    get() = extensions.getByType(ParametersExtension::class.java)
