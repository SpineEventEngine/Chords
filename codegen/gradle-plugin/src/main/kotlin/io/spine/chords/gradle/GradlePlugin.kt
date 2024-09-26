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
        private const val extensionName = "chordsGradlePlugin"
        private const val gradleWrapperJar = "gradle/wrapper/gradle-wrapper.jar"
        private const val mainTaskName = "applyCodegenPlugins"
        private const val compileKotlinTaskName = "compileKotlin"
    }

    override fun apply(project: Project) {

        val workspaceDir = File(project.buildDir, moduleName)

        project.createExtension()

        val copyResources = project.tasks
            .register("copyResources") { task ->
                task.doLast {
                    copyResources(workspaceDir)
                    addRunPermission(workspaceDir)
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
            .register(mainTaskName, GenerateCode::class.java) { task ->
                task.dependsOn(addRunPermission)
                task.workspaceDir = workspaceDir.path
                task.pluginsVersion = "2.0.0-SNAPSHOT.22"
                task.dependencies(project.extension.dependencies)
            }

        val compileKotlin = project.tasks.findByName(compileKotlinTaskName)
        if (compileKotlin != null) {
            compileKotlin.dependsOn(generateCode)
        } else {
            project.logger.warn(
                """
                    
                Warning! The task `$compileKotlinTaskName` not found.
                To run the code generation, execute or add dependency on `$mainTaskName` task .
                
                """.trimIndent()
            )
        }
    }

    private fun addRunPermission(workspaceDir: File) {
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
            //System.err.println("Copy file:" + outputFile.path)
            inputStream.copyTo(outputFile.outputStream())
        }

        val gradleWrapperJar = File(workspaceDir, gradleWrapperJar)
        if (!gradleWrapperJar.exists()) {
            gradleWrapperJar.parentFile.mkdirs()
            //System.err.println("Copy file:" + gradleWrapperJar.path)
            loadResourceAsStream("/gradle-wrapper.zip")
                .copyTo(gradleWrapperJar.outputStream())
        }
    }

    private fun Project.createExtension(): ParametersExtension {
        val extension = ParametersExtension()
        extensions.add(ParametersExtension::class.java, extensionName, extension)
        return extension
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

/**
 * Obtains the extension our plugin added to this Gradle project.
 */
private val Project.extension: ParametersExtension
    get() = extensions.getByType(ParametersExtension::class.java)
